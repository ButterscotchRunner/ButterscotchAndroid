package net.perfectdreams.butterscotch.android

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The main game library navigation graph.
 *
 * The game emulator is NOT included here because we keep it as a separate activity.
 */
sealed interface Route {
    @Serializable data object Launcher : Route
    @Serializable data object ImportGame : Route
    @Serializable data object About : Route
    @Serializable data object Licenses : Route
    @Serializable data class GameSettings(val gameId: String) : Route
    @Serializable data class GameMetadata(val gameId: String) : Route
    @Serializable data class SaveSlotList(val gameId: String) : Route
    @Serializable data class SaveSlotDetail(val gameId: String, val slotId: String) : Route
}
