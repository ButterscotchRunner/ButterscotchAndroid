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
    @Serializable data class GameSettings(@Serializable(with = UUIDAsStringSerializer::class) val gameId: UUID) : Route
    @Serializable data class GameMetadata(@Serializable(with = UUIDAsStringSerializer::class) val gameId: UUID) : Route
    @Serializable data class SaveSlotList(@Serializable(with = UUIDAsStringSerializer::class) val gameId: UUID) : Route
    @Serializable data class SaveSlotDetail(@Serializable(with = UUIDAsStringSerializer::class) val gameId: UUID, val slotId: String) : Route
}
