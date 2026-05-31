package net.perfectdreams.butterscotch.android

import android.content.Intent
import android.os.Bundle
import android.view.ViewAnimationUtils
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.util.UUID
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        val gameLibrary = Libraries.loadGameLibrary(this.applicationContext)
        val layoutLibrary = Libraries.loadLayoutLibrary(this.applicationContext)

        if (intent?.action == ACTION_LAUNCH_GAME) {
            val gameIdAsString = intent.getStringExtra(GameActivity.EXTRA_GAME_ID)
            // Clear so a config change / recreate doesn't re-trigger the forward.
            intent.action = null
            intent.removeExtra(GameActivity.EXTRA_GAME_ID)
            if (gameIdAsString != null) {
                val gameId = UUID.fromString(gameIdAsString)

                if (gameLibrary.findById(gameId) != null) {
                    startActivity(Intent(this, GameActivity::class.java).apply {
                        putExtra(GameActivity.EXTRA_GAME_ID, gameId)
                    })
                    finish()
                }
                return
            }
            // Unknown/stale id: fall through to the normal launcher UI.
        }

        // Hold the splash a short beat so the reveal is actually perceptible (Compose boots near-instantly otherwise)
        var splashReady = false
        splashScreen.setKeepOnScreenCondition { !splashReady }
        window.decorView.postDelayed({ splashReady = true }, 350)

        // Tier 1 cut-out: collapse the cream splash into a shrinking circle, revealing the app underneath
        splashScreen.setOnExitAnimationListener { provider ->
            val splashView = provider.view
            val cx = splashView.width / 2
            val cy = splashView.height / 2
            val startRadius = hypot(cx.toFloat(), cy.toFloat())
            ViewAnimationUtils.createCircularReveal(splashView, cx, cy, startRadius, 0f).apply {
                interpolator = AccelerateInterpolator()
                duration = 450L
                doOnEnd { provider.remove() }
                start()
            }
        }

        enableEdgeToEdge()
        setContent {
            ButterscotchAndroidTheme {
                ButterscotchApp(gameLibrary, layoutLibrary)
            }
        }
    }

    companion object {
        const val ACTION_LAUNCH_GAME = "net.perfectdreams.butterscotch.android.action.LAUNCH_GAME"
    }
}
