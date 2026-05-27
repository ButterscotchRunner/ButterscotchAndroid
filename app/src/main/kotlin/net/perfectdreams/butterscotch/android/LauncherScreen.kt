package net.perfectdreams.butterscotch.android

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary

/**
 * The library list. Receives a [net.perfectdreams.butterscotch.android.library.GameLibrary] from [ButterscotchApp] — because its `entries` field
 * is a Compose snapshot list, mutations from [ImportScreen]/[SettingsScreen] reflect here
 * automatically without any lifecycle-based reload.
 *
 * Navigation is delegated upward via the three lambdas so this screen stays unaware of the nav
 * graph / Intent plumbing.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    library: GameLibrary,
    nav: NavHostController
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            ButterscotchTopBar("Butterscotch", nav, false)
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add Game") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    nav.navigate(Route.ImportGame)
                },
            )
        }
    ) { innerPadding ->
        val entries = library.entries
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No games yet — tap Add Game to import a folder.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The entries are already sorted here
                items(entries, key = { it.id }) { entry ->
                    GameTile(
                        library = library,
                        entry = entry,
                        onLaunch = {
                            context.startActivity(Intent(context, GameActivity::class.java).apply {
                                putExtra(GameActivity.EXTRA_GAME_ID, entry.id)
                            })
                        },
                        onOpenSettings = {
                            nav.navigate(Route.GameSettings(entry.id))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTile(
    library: GameLibrary,
    entry: GameEntry,
    onLaunch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onLaunch)
                    .padding(16.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when (entry.gameType) {
                        is GameEntry.GameType.GameMakerStudio -> "GM:S (WAD Version ${entry.gameType.wadVersion})"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // The IconButtons has its own click region, so tapping them doesn't bubble into the launch click above.

            IconButton(onClick = onOpenSettings) {
                if (entry.favorited) {
                    IconButton(onClick = {
                        library.update(entry.id) {
                            it.copy(favorited = false)
                        }
                        library.save()
                        Toast.makeText(context, "Unfavorited game!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.Star, contentDescription = "Unfavorite Game", tint = Color(0xFFFEB529))
                    }
                } else {
                    IconButton(onClick = {
                        library.update(entry.id) {
                            it.copy(favorited = true)
                        }
                        library.save()
                        Toast.makeText(context, "Favorited game!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.StarBorder, contentDescription = "Favorite Game", tint = Color(0xFFFEB529))
                    }
                }
            }

            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Game settings")
            }
        }
    }
}
