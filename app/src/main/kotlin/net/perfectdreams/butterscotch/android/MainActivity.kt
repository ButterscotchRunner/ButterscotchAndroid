package net.perfectdreams.butterscotch.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ButterscotchAndroidTheme {
                ButterscotchApp()
            }
        }
    }
}
