package net.perfectdreams.butterscotch.android

import android.content.Context
import android.os.Build
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.perfectdreams.butterscotch.network.AndroidAnalyticsLaunchAppRequest
import java.util.UUID

object ButterscotchUtils {
    const val TAG = "ButterscotchUtils"

    val http by lazy {
        HttpClient(CIO) {
            expectSuccess = false
            defaultRequest { url(BuildConfig.API_BASE_URL) }
        }
    }

    // Stable anonymous per-install identifier, see AppState.installId
    fun getInstallId(context: Context): UUID = Libraries.loadAppStateStore(context).state.installId

    fun fireAppLaunchEvent(context: Context) {
        val identifier = getInstallId(context)

        GlobalScope.launch {
            try {
                val response = http.post("/api/${BuildConfig.API_VERSION}/android/analytics/launch-app") {
                    setBody(
                        Json.encodeToString(
                            AndroidAnalyticsLaunchAppRequest(
                                identifier,
                                Build.VERSION.SDK_INT,
                                Build.MANUFACTURER,
                                Build.MODEL,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE
                            )
                        )
                    )
                }

                Log.i(TAG, "Sent analytics message! Status code: ${response.status}")
            } catch (e: Exception) {
                // Fire and forget...
                Log.w(TAG, "Failed to log analytics message!", e)
            }
        }
    }

    // TODO: This is still a stub, it needs its own AndroidAnalyticsLaunchGameRequest DTO + /android/analytics/launch-game route
    fun fireGameLaunchEvent(context: Context) {
        val identifier = getInstallId(context)
        GlobalScope.launch {
            try {
                val response = http.post("/api/${BuildConfig.API_VERSION}/android/analytics/launch-app") {
                    setBody(
                        Json.encodeToString(
                            AndroidAnalyticsLaunchAppRequest(
                                identifier,
                                Build.VERSION.SDK_INT,
                                Build.MANUFACTURER,
                                Build.MODEL,
                                BuildConfig.VERSION_NAME,
                                BuildConfig.VERSION_CODE
                            )
                        )
                    )
                }

                Log.i(TAG, "Sent analytics message! Status code: ${response.status}")
            } catch (e: Exception) {
                // Fire and forget...
                Log.w(TAG, "Failed to log analytics message!", e)
            }
        }
    }
}