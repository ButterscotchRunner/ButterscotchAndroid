package net.perfectdreams.butterscotch.android.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.perfectdreams.butterscotch.android.BuildConfig

/**
 * Owns the [BillingClient] connection and the in-app purchase lifecycle.
 *
 * The fields [isPro], [proProduct], [connectionState] are Compose-aware.
 */
class BillingManager private constructor(
    private val appContext: Context,
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    // Whether the user owns the Pro unlock, seeded from the local cache so the UI is correct before the (async) Play Store query comes back
    var isPro by mutableStateOf(BuildConfig.DEBUG || prefs().getBoolean(KEY_PRO, false))
        private set

    // ProductDetails for the Pro unlock once queried, used to show the localized price
    // Null until the first successful query
    var proProduct by mutableStateOf<ProductDetails?>(null)
        private set

    var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Play hands new and updated purchases (including ones that complete after the buy sheet closes) to this single listener, regardless of which activity launched the flow
    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.d(TAG, "User canceled the purchase flow")
        } else {
            Log.w(TAG, "Purchase update failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        // enableOneTimeProducts is required when we sell non-consumables or consumables
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    /**
     * Connect (or reconnect) to Play.
     *
     * Safe to call repeatedly, e.g. on every Activity onStart.
     */
    fun connect() {
        if (this.connectionState != ConnectionState.DISCONNECTED)
            return

        this.connectionState = ConnectionState.CONNECTING
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    this@BillingManager.connectionState = ConnectionState.CONNECTED
                    scope.launch {
                        queryProducts()
                        refreshPurchases()
                    }
                } else {
                    this@BillingManager.connectionState = ConnectionState.DISCONNECTED
                    Log.w(TAG, "Billing setup failed: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Play drops the connection from time to time, the next connect() will rebuild it
                this@BillingManager.connectionState = ConnectionState.DISCONNECTED
            }
        })
    }

    // Pull localized price/title for the Pro product so the UI can render it
    private suspend fun queryProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            proProduct = result.productDetailsList?.firstOrNull { it.productId == PRO_PRODUCT_ID }
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result.billingResult.responseCode}")
        }
    }

    /**
     * Re-read the user's owned purchases from the Play Store cache and reconcile [isPro].
     *
     * This is what restores the unlock on a new device or reinstall, so it runs on every connect.
     */
    suspend fun refreshPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val owned = result.purchasesList.any { purchase ->
                purchase.products.contains(PRO_PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }
            grantPro(owned)
            // Acknowledge anything that slipped through unacknowledged (e.g. app killed mid-purchase)
            result.purchasesList.forEach { if (!it.isAcknowledged) handlePurchase(it) }
        }
    }

    /**
     * Launch the buy sheet for the Pro unlock.
     *
     * Must be called with a real foreground Activity.
     *
     * Returns false if we have no product details yet (connection not ready) so the caller can show  a "try again" message instead of silently doing nothing.
     */
    fun launchProPurchase(activity: Activity): Boolean {
        val product = proProduct ?: return false
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()
        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "launchBillingFlow failed: ${result.responseCode} ${result.debugMessage}")
            return false
        }
        return true
    }

    // Grant the entitlement, then acknowledge so Google does not auto-refund within 3 days
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        if (!purchase.products.contains(PRO_PRODUCT_ID)) return

        grantPro(true)

        if (!purchase.isAcknowledged) {
            scope.launch {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val ack = client.acknowledgePurchase(ackParams)
                if (ack.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "acknowledgePurchase failed: ${ack.responseCode} ${ack.debugMessage}")
                }
            }
        }
    }

    private fun grantPro(value: Boolean) {
        if (BuildConfig.DEBUG) {
            this.isPro = true
        } else {
            this.isPro = value
            prefs().edit().putBoolean(KEY_PRO, value).apply()
        }
    }

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "BillingManager"
        private const val PREFS_NAME = "butterscotch_billing"
        private const val KEY_PRO = "pro_unlocked"
        const val PRO_PRODUCT_ID = "butterscotch_pro"

        private var instance: BillingManager? = null

        // Should ONLY be called from a single thread
        fun getInstance(context: Context): BillingManager {
            val instance = this.instance
            if (instance != null) {
                return instance
            }

            return BillingManager(context).also { this.instance = it }
        }
    }
}
