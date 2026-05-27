package net.perfectdreams.butterscotch.android

import kotlinx.serialization.Serializable

/**
 * The main game library navigation graph.
 *
 * The game emulator is NOT included here because we keep it as a separate activity.
 */
sealed interface Route {
    @Serializable data object Launcher : Route
    @Serializable data object ImportGame : Route
    @Serializable data class GameSettings(val gameId: String) : Route
}
