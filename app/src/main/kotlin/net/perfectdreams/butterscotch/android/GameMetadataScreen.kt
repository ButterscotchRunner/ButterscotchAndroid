package net.perfectdreams.butterscotch.android

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.components.MetadataForm
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
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

    var title by rememberSaveable { mutableStateOf(entry.title) }

    val originalIcon = remember {
        val f = gameLibrary.iconFile(entry)
        if (f.exists())
            BitmapFactory.decodeFile(f.absolutePath)
        else
            null
    }

    var selectedIcon by remember { mutableStateOf(originalIcon) }

    // Layout selection is staged like title/icon and committed on Save, so changing it enables the
    // Save button instead of persisting silently behind the user's back.
    var portraitLayout by rememberSaveable { mutableStateOf(entry.portraitLayout) }
    var landscapeLayout by rememberSaveable { mutableStateOf(entry.landscapeLayout) }

    val titleTrimmed = title.trim()
    val titleChanged = titleTrimmed.isNotBlank() && titleTrimmed != entry.title
    val iconChanged = selectedIcon !== originalIcon
    val layoutsChanged = portraitLayout != entry.portraitLayout || landscapeLayout != entry.landscapeLayout

    Scaffold(
        topBar = {
            ButterscotchTopBar("Metadata", nav, navigationIcon = { ButterscotchBackButton(nav) })
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp)) {
            MetadataForm(
                title = title,
                onTitleChange = { title = it },
                selectedIcon = selectedIcon,
                onIconChange = { selectedIcon = it },
                middleContent = {
                    LayoutDropdown(
                        label = "Portrait Layout",
                        selectedId = portraitLayout,
                        options = layoutLibrary.entries.filter { it.orientation == GamepadLayout.GamepadTargetOrientation.PORTRAIT },
                        onSelect = { id -> portraitLayout = id },
                    )
                    Spacer(Modifier.height(16.dp))
                    LayoutDropdown(
                        label = "Landscape Layout",
                        selectedId = landscapeLayout,
                        options = layoutLibrary.entries.filter { it.orientation == GamepadLayout.GamepadTargetOrientation.LANDSCAPE },
                        onSelect = { id -> landscapeLayout = id },
                    )
                    Spacer(Modifier.height(16.dp))
                },
                loadCandidates = { scanIconCandidates(gameLibrary.bundleDir(entry)) },
                saveEnabled = titleChanged || iconChanged || layoutsChanged,
                onSave = {
                    if (titleChanged) gameLibrary.setTitle(entry.id, titleTrimmed)
                    if (iconChanged) gameLibrary.setIcon(entry.id, selectedIcon)
                    if (layoutsChanged) gameLibrary.update(entry.id) { it.copy(portraitLayout = portraitLayout, landscapeLayout = landscapeLayout) }
                    nav.popBackStack()
                },
            )
        }
    }
}

// Exposed dropdown that assigns one of the LayoutLibrary's layouts to this game for an orientation.
// The selection persists immediately via the caller's onSelect. "(unknown)" shows when the assigned
// id has no matching layout (e.g. a default that is not seeded yet).
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun LayoutDropdown(
    label: String,
    selectedId: UUID,
    options: List<GamepadLayout>,
    onSelect: (UUID) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = options.firstOrNull { it.id == selectedId }?.fancyName ?: "(unknown)"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(layout.fancyName) },
                    onClick = {
                        onSelect(layout.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
