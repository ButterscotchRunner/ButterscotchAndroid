package net.perfectdreams.butterscotch.android

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.library.GameLibrary
import java.util.UUID

/**
 * The entry point of the app!
 */
@Composable
fun ButterscotchApp(gameLibrary: GameLibrary, layoutLibrary: LayoutLibrary) {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = Route.Launcher,
        // Changes the default fade to a horizontal right -> left slide animation
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(250)) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(250)) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(250)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(250)) },
    ) {
        composable<Route.Launcher> {
            LauncherScreen(library = gameLibrary, nav = nav)
        }
        composable<Route.ImportGame> {
            ImportScreen(library = gameLibrary, layoutLibrary = layoutLibrary, nav = nav)
        }
        composable<Route.About> {
            AboutScreen(nav = nav)
        }
        composable<Route.Pro> {
            ProScreen(nav = nav)
        }
        composable<Route.Licenses> {
            LicensesScreen(nav = nav)
        }
        composable<Route.GameSettings> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.GameSettings>()
            SettingsScreen(
                library = gameLibrary,
                gameId = UUID.fromString(args.gameId),
                nav = nav
            )
        }
        composable<Route.GameMetadata> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.GameMetadata>()
            GameMetadataScreen(
                gameLibrary = gameLibrary,
                layoutLibrary = layoutLibrary,
                gameId = UUID.fromString(args.gameId),
                nav = nav,
            )
        }
        composable<Route.SaveSlotList> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.SaveSlotList>()
            SaveSlotListScreen(
                library = gameLibrary,
                gameId = UUID.fromString(args.gameId),
                nav = nav,
            )
        }
        composable<Route.SaveSlotDetail> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.SaveSlotDetail>()
            SaveSlotDetailScreen(
                library = gameLibrary,
                gameId = UUID.fromString(args.gameId),
                slotId = args.slotId,
                nav = nav,
            )
        }
    }
}
