package net.perfectdreams.butterscotch.android.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.components.InputToggle
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.settings.SettingsStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    gameLibrary: GameLibrary,
    layoutLibrary: LayoutLibrary,
    settingsStore: SettingsStore,
    nav: NavHostController,
) {
    Scaffold(
        topBar = {
            ButterscotchTopBar("Settings", nav, navigationIcon = { ButterscotchBackButton(nav) })
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            Column {
                InputToggle("Enable Haptic Feedback", null, settingsStore.settings.enableHapticFeedback) { enabled ->
                    settingsStore.update { copy(enableHapticFeedback = enabled) }
                }

                Text("Howdy!!!")
            }
        }
    }
}