package net.perfectdreams.butterscotch.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.components.MetadataForm
import net.perfectdreams.butterscotch.android.components.rememberGameMetadataFormState
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.pe.scanIconCandidates
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun GameMetadataScreen(
    gameLibrary: GameLibrary,
    layoutLibrary: LayoutLibrary,
    gameId: UUID,
    nav: NavHostController,
) {
    val entry = gameLibrary.findById(gameId) ?: return

    val originalIcon = remember {
        val f = gameLibrary.iconFile(entry)
        if (f.exists())
            BitmapFactory.decodeFile(f.absolutePath)
        else
            null
    }

    val state = rememberGameMetadataFormState(
        key = entry.id,
        title = entry.title,
        icon = originalIcon,
        portraitLayout = entry.portraitLayout,
        landscapeLayout = entry.landscapeLayout,
        runnerOs = entry.runnerOs,
        enablePhysicalControllers = entry.enablePhysicalControllers,
        enablePhysicalKeyboard = entry.enablePhysicalKeyboard,
        enableWidescreenHack = entry.enableWidescreenHack
    )

    // The baseline is the persisted entry, so we only write back what the user actually touched instead of rewriting (and cache-invalidating) every field on Save.
    val titleChanged = state.titleTrimmed.isNotBlank() && state.titleTrimmed != entry.title
    val iconChanged = state.selectedIcon !== originalIcon
    val layoutsChanged = state.portraitLayout != entry.portraitLayout || state.landscapeLayout != entry.landscapeLayout
    val runnerOsChanged = state.runnerOs != entry.runnerOs
    val controllersChanged = state.enablePhysicalControllers != entry.enablePhysicalControllers
    val keyboardChanged = state.enablePhysicalKeyboard != entry.enablePhysicalKeyboard
    val widescreenHackChanged = state.enableWidescreenHack != entry.enableWidescreenHack

    Scaffold(
        topBar = {
            ButterscotchTopBar("Metadata", nav, navigationIcon = { ButterscotchBackButton(nav) })
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            MetadataForm(
                layoutLibrary = layoutLibrary,
                state = state,
                loadCandidates = { scanIconCandidates(gameLibrary.bundleDir(entry)) },
                saveEnabled = titleChanged || iconChanged || layoutsChanged || runnerOsChanged || controllersChanged || keyboardChanged || widescreenHackChanged,
                onSave = {
                    if (titleChanged) gameLibrary.setTitle(entry.id, state.titleTrimmed)
                    if (iconChanged) gameLibrary.setIcon(entry.id, state.selectedIcon)
                    if (layoutsChanged) gameLibrary.update(entry.id) { it.copy(portraitLayout = state.portraitLayout, landscapeLayout = state.landscapeLayout) }
                    if (runnerOsChanged) gameLibrary.update(entry.id) { it.copy(runnerOs = state.runnerOs) }
                    if (controllersChanged) gameLibrary.update(entry.id) { it.copy(enablePhysicalControllers = state.enablePhysicalControllers) }
                    if (keyboardChanged) gameLibrary.update(entry.id) { it.copy(enablePhysicalKeyboard = state.enablePhysicalKeyboard) }
                    if (widescreenHackChanged) gameLibrary.update(entry.id) { it.copy(enableWidescreenHack = state.enableWidescreenHack) }
                    nav.popBackStack()
                },
            )
        }
    }
}