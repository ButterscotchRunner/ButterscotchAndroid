package net.perfectdreams.butterscotch.android.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import net.perfectdreams.butterscotch.android.GameImporter
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.components.MetadataForm
import net.perfectdreams.butterscotch.android.components.rememberGameMetadataFormState
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameEntry.GameType
import net.perfectdreams.butterscotch.android.library.GameLibrary
import java.util.UUID

/**
 * Folder-picker → copy → configure flow. Lives under [net.perfectdreams.butterscotch.android.Route.ImportGame] in the nav graph.
 *
 * State machine:
 *   Intro -> (user taps "Select folder") -> picker -> Copying -> Configure -> commit -> onDone()
 *                                                              \-> Cancel -> deletes staging, back to Intro
 *                                       -> MissingWad / Failure error screen -> back to Intro
 */
private sealed interface ImportUIState {
    data object Intro : ImportUIState
    class Copying : ImportUIState {
        var currentFile by mutableStateOf<String?>(null)
    }
    data class Configure(val result: GameImporter.Result.Success) : ImportUIState
    data class Error(val message: String, val previous: ImportUIState = Intro) : ImportUIState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    library: GameLibrary,
    layoutLibrary: LayoutLibrary,
    nav: NavHostController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportUIState>(ImportUIState.Intro) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) {
            // User cancelled the picker — stay on Intro.
            return@rememberLauncherForActivityResult
        }
        val copyingState = ImportUIState.Copying()
        state = copyingState
        scope.launch {
            state = when (val result = GameImporter.import(context, uri, library) { copyingState.currentFile = it }) {
                is GameImporter.Result.Success -> ImportUIState.Configure(result)
                is GameImporter.Result.MissingWad -> ImportUIState.Error(
                    "Missing WAD in folder!\n\nExpected one of: ${GameImporter.WAD_FILENAMES.joinToString(", ")}"
                )
                is GameImporter.Result.Failure -> ImportUIState.Error(result.message)
            }
        }
    }

    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            // User cancelled the picker — stay on Intro.
            return@rememberLauncherForActivityResult
        }
        val copyingState = ImportUIState.Copying()
        state = copyingState
        scope.launch {
            state = when (val result = GameImporter.importZip(context, uri, library) { copyingState.currentFile = it }) {
                is GameImporter.Result.Success -> ImportUIState.Configure(result)
                is GameImporter.Result.MissingWad -> ImportUIState.Error(
                    "Missing WAD in ZIP!\n\nExpected one of: ${GameImporter.WAD_FILENAMES.joinToString(", ")}"
                )
                is GameImporter.Result.Failure -> ImportUIState.Error(result.message)
            }
        }
    }

    Scaffold(
        topBar = {
            ButterscotchTopBar(
                when (state) {
                    is ImportUIState.Configure -> "Configure Game"
                    else -> "Add Game"
                },
                nav,
                navigationIcon = {
                    when (val s = state) {
                        is ImportUIState.Configure -> {
                            ButterscotchBackButton(nav) {
                                library.discardStaging(s.result.staged)
                                state = ImportUIState.Intro
                            }
                        }
                        else -> ButterscotchBackButton(nav)
                    }

                }
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            when (val s = state) {
                ImportUIState.Intro -> IntroPane(
                    onSelectFolder = { pickFolder.launch(null) },
                    onSelectZip = { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                )
                is ImportUIState.Copying -> CopyingPane(s.currentFile)
                is ImportUIState.Configure -> ConfigurePane(
                    result = s.result,
                    layoutLibrary = layoutLibrary,
                    onSave = { title, icon, portraitLayout, landscapeLayout, runnerOs, enablePhysicalControllers, enablePhysicalKeyboard, enableWidescreenHack ->
                        library.commit(
                            s.result.staged,
                            title,
                            GameType.GameMakerStudio(
                                s.result.wadVersion,
                                s.result.wadFilename
                            ),
                            icon = icon,
                            portraitLayout = portraitLayout,
                            landscapeLayout = landscapeLayout,
                            runnerOs = runnerOs,
                            enablePhysicalControllers = enablePhysicalControllers,
                            enablePhysicalKeyboard = enablePhysicalKeyboard,
                            enableWidescreenHack = enableWidescreenHack
                        )
                        nav.popBackStack()
                    }
                )
                is ImportUIState.Error -> ErrorPane(
                    message = s.message,
                    onDismiss = { state = s.previous },
                )
            }
        }
    }
}

@Composable
private fun IntroPane(onSelectFolder: () -> Unit, onSelectZip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Select a folder or a ZIP with a GameMaker WAD file (${GameImporter.WAD_FILENAMES.joinToString(", ")})",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onSelectFolder) {
            Text("Import folder")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSelectZip) {
            Text("Import ZIP")
        }
    }
}

@Composable
private fun CopyingPane(currentFile: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Copying game files...")
        if (currentFile != null) {
            Text(currentFile)
        }
    }
}

@Composable
private fun ConfigurePane(
    result: GameImporter.Result.Success,
    layoutLibrary: LayoutLibrary,
    onSave: (title: String, icon: Bitmap?, portraitLayout: UUID, landscapeLayout: UUID, runnerOs: GameEntry.RunnerOs, enablePhysicalControllers: Boolean, enablePhysicalKeyboard: Boolean, enableWidescreenHack: Boolean) -> Unit
) {
    // suggestedTitle comes from GEN8 (may be null for pre-WAD10 games); fall back to the folder
    // name so the user never sees an empty field.
    val initial = result.suggestedTitle ?: result.folderName

    // The game is not committed yet, so there is no entry to compare against: these start from the same defaults commit() would apply and are passed straight through onSave at commit time.
    val state = rememberGameMetadataFormState(
        key = result.staged.id,
        title = initial,
        icon = result.iconCandidates.firstOrNull()?.bitmap,
        portraitLayout = LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT,
        landscapeLayout = LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT,
        runnerOs = GameEntry.RunnerOs.WINDOWS,
        enablePhysicalControllers = true,
        enablePhysicalKeyboard = true,
        enableWidescreenHack = false
    )

    MetadataForm(
        layoutLibrary = layoutLibrary,
        state = state,
        loadCandidates = { result.iconCandidates },
        saveEnabled = state.title.isNotBlank(),
        onSave = { onSave(state.title.ifBlank { initial }, state.selectedIcon, state.portraitLayout, state.landscapeLayout, state.runnerOs, state.enablePhysicalControllers, state.enablePhysicalKeyboard, state.enableWidescreenHack) },
    )
}

@Composable
private fun ErrorPane(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDismiss) { Text("OK") }
    }
}
