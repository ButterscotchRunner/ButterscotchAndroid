package net.perfectdreams.butterscotch.android

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.billing.BillingManager
import net.perfectdreams.butterscotch.android.components.BannerAd
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
    val billing = remember { BillingManager.getInstance(context) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            ButterscotchTopBar(
                title = "Butterscotch",
                nav = nav,
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = Color.Black)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(painterResource(R.drawable.discord), contentDescription = null, modifier = Modifier.size(24.dp)) },
                            text = { Text("Discord Community") },
                            onClick = {
                                menuExpanded = false
                                val intent = Intent(Intent.ACTION_VIEW, "https://discord.gg/2gQR7t3WJR".toUri())
                                context.startActivity(intent)
                            },
                        )

                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                            text = { Text("Butterscotch Pro") },
                            onClick = {
                                menuExpanded = false
                                nav.navigate(Route.Pro)
                            },
                        )

                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            text = { Text("About") },
                            onClick = {
                                menuExpanded = false
                                nav.navigate(Route.About)
                            },
                        )
                    }
                },
            )
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
                itemsIndexed(entries, key = { _, entry -> entry.id.toString() }) { index, entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!billing.isPro && index % 6 == 0) {
                            BannerAd()
                        }

                        GameTile(
                            library = library,
                            entry = entry,
                            onLaunch = {
                                context.startActivity(Intent(context, GameActivity::class.java).apply {
                                    putExtra(GameActivity.EXTRA_GAME_ID, entry.id.toString())
                                })
                            },
                            onOpenSettings = {
                                nav.navigate(Route.GameSettings(entry.id.toString()))
                            },
                        )
                    }
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onLaunch)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GameIcon(library, entry, modifier = Modifier.size(42.dp))
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (entry.gameType) {
                            is GameEntry.GameType.GameMakerStudio -> "GM:S (WAD Version ${entry.gameType.wadVersion})"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
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

/**
 * Shows the per-game icon scraped from the bundle's first .exe at import time, or the Butterscotch
 * app icon when no icon was extractable (no .exe / PE without resources / decode failed).
 *
 * The icons are tiny (<=256px) so we decode synchronously inside a `remember`; that's plenty fast
 * for a list of a few dozen entries and avoids pulling in an image-loading dependency.
 */
@Composable
private fun GameIcon(
    library: GameLibrary,
    entry: GameEntry,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val file = library.iconFile(entry)
    val bitmap = remember(entry.iconRevision) {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    } ?: remember(context) { appIconBitmap(context) }

    Image(
        painter = BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.None),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * Loads the app's launcher icon as a bitmap. We can't use `painterResource(R.mipmap.ic_launcher)`
 * because on API 26+ that resolves to an `<adaptive-icon>` XML, which Compose's painterResource
 * refuses to load.
 */
private fun appIconBitmap(context: Context): android.graphics.Bitmap {
    val drawable = context.packageManager.getApplicationIcon(context.packageName)
    return drawable.toBitmap()
}
