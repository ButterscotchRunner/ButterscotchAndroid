package net.perfectdreams.butterscotch.android

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.components.MetadataForm
import net.perfectdreams.butterscotch.android.library.GameEntry.GameType
import net.perfectdreams.butterscotch.android.library.GameLibrary

/**
 * Folder-picker → copy → configure flow. Lives under [Route.ImportGame] in the nav graph.
 *
 * State machine:
 *   Intro -> (user taps "Select folder") -> picker -> Copying -> Configure -> commit -> onDone()
 *                                                              \-> Cancel -> deletes staging, back to Intro
 *                                       -> MissingWad / Failure error screen -> back to Intro
 */
private sealed interface ImportUIState {
    data object Intro : ImportUIState
    data object Copying : ImportUIState
    data class Configure(val result: GameImporter.Result.Success) : ImportUIState
    data class Error(val message: String, val previous: ImportUIState = Intro) : ImportUIState
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    library: GameLibrary,
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
        state = ImportUIState.Copying
        scope.launch {
            state = when (val result = GameImporter.import(context, uri, library)) {
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
        state = ImportUIState.Copying
        scope.launch {
            state = when (val result = GameImporter.importZip(context, uri, library)) {
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
                ImportUIState.Copying -> CopyingPane()
                is ImportUIState.Configure -> ConfigurePane(
                    result = s.result,
                    onSave = { title, icon ->
                        library.commit(
                            s.result.staged,
                            title,
                            GameType.GameMakerStudio(
                                s.result.wadVersion,
                                s.result.wadFilename
                            ),
                            icon = icon,
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
private fun CopyingPane() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Copying game files…")
    }
}

@Composable
private fun ConfigurePane(
    result: GameImporter.Result.Success,
    onSave: (title: String, icon: Bitmap?) -> Unit
) {
    // suggestedTitle comes from GEN8 (may be null for pre-WAD10 games); fall back to the folder
    // name so the user never sees an empty field.
    val initial = result.suggestedTitle ?: result.folderName
    var title by rememberSaveable(result.staged.id) { mutableStateOf(initial) }
    var selectedIcon by remember(result.staged.id) {
        mutableStateOf<Bitmap?>(result.iconCandidates.firstOrNull()?.bitmap)
    }

    MetadataForm(
        title = title,
        onTitleChange = { title = it },
        selectedIcon = selectedIcon,
        onIconChange = { selectedIcon = it },
        loadCandidates = { result.iconCandidates },
        saveEnabled = title.isNotBlank(),
        onSave = { onSave(title.ifBlank { initial }, selectedIcon) },
        middleContent = {
            Text("Detected WAD: ${result.wadFilename}", style = MaterialTheme.typography.bodyMedium)
            if (result.wadVersion >= 0) {
                Text("WAD version: ${result.wadVersion}", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
        },
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
