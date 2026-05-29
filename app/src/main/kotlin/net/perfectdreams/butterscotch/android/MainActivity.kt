package net.perfectdreams.butterscotch.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
