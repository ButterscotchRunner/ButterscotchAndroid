package net.perfectdreams.butterscotch.android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.perfectdreams.butterscotch.android.billing.BillingManager
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val gameLibrary = Libraries.loadGameLibrary(this.applicationContext)
        val layoutLibrary = Libraries.loadLayoutLibrary(this.applicationContext)
        val settingsStore = Libraries.loadSettingsStore(this.applicationContext)

        val billing = BillingManager.getInstance(this.applicationContext)
        billing.connect()

        // Pro users never see ads, so there's no reason to spin up the AdMob SDK for them
        if (!billing.isPlus) {
            MobileAds.initialize(this.applicationContext)
        }

        ButterscotchUtils.fireAppLaunchEvent(this)

        if (intent?.action == ACTION_LAUNCH_GAME) {
            val gameIdAsString = intent.getStringExtra(GameActivity.EXTRA_GAME_ID)
            // Clear so a config change / recreate doesn't re-trigger the forward.
            intent.action = null
            intent.removeExtra(GameActivity.EXTRA_GAME_ID)
            if (gameIdAsString != null) {
                if (billing.isPlus) {
                    val gameId = UUID.fromString(gameIdAsString)

                    if (gameLibrary.findById(gameId) != null) {
                        startActivity(Intent(this, GameActivity::class.java).apply {
                            putExtra(GameActivity.EXTRA_GAME_ID, gameId.toString())
                        })
                        finish()
                    }
                    return
                }

                Toast.makeText(this.applicationContext, "You don't have Pro to use shortcuts!", Toast.LENGTH_LONG).show()
            }
            // Unknown/stale id: fall through to the normal launcher UI.
        }

        enableEdgeToEdge()
        setContent {
            ButterscotchAndroidTheme {
                var splashGone by rememberSaveable { mutableStateOf(false) }
                Box(Modifier.fillMaxSize()) {
                    ButterscotchApp(gameLibrary, layoutLibrary, settingsStore)
                    if (!splashGone) {
                        SplashReveal(onFinished = { splashGone = true })
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_LAUNCH_GAME = "net.perfectdreams.butterscotch.android.action.LAUNCH_GAME"
    }
}
