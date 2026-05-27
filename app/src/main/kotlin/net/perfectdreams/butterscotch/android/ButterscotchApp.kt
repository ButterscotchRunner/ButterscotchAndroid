package net.perfectdreams.butterscotch.android

import android.content.Intent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import net.perfectdreams.butterscotch.android.library.GameLibrary

/**
 * The entry point of the app!
 */
@Composable
fun ButterscotchApp() {
    val context = LocalContext.current
    val library = remember { GameLibrary.load(context) }
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
            LauncherScreen(library = library, nav = nav)
        }
        composable<Route.ImportGame> {
            ImportScreen(library = library, nav)
        }
        composable<Route.GameSettings> { backStackEntry ->
            val args = backStackEntry.toRoute<Route.GameSettings>()
            SettingsScreen(
                library = library,
                gameId = args.gameId,
                nav = nav
            )
        }
    }
}
