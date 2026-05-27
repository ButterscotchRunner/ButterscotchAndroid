package net.perfectdreams.butterscotch.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.library.GameLibrary

/**
 * Per-game settings screen. Lives under [Route.GameSettings] in the nav graph. Today's only row is
 * "Delete"; future additions (export/import saves, edit title, etc.) slot in as more [SettingsRow]
 * calls in [SettingsScreen].
 *
 * Pops itself via [onDone] when the user backs out OR after a successful delete. If the entry is
 * missing on entry (e.g. removed by another flow), we pop immediately rather than render an empty
 * shell.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    library: GameLibrary,
    gameId: String,
    nav: NavHostController
) {
    // Sadly we need to do this hacky hack because when deleting this gets recomposed and gets a null entry
    val entry = library.findById(gameId) ?: return

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ButterscotchTopBar(entry.title, nav, true)
        }
    ) { innerPadding ->
        Column(Modifier
            .fillMaxSize()
            .padding(innerPadding)) {
            SettingsRow(
                title = "Delete",
                subtitle = "Removes the game and all its saves",
                titleColor = MaterialTheme.colorScheme.error,
                onClick = { showDeleteDialog = true },
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${entry.title}?") },
            text = { Text("This removes the game bundle and all of its saves. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    nav.popBackStack()
                    library.remove(gameId)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    HorizontalDivider()
}
