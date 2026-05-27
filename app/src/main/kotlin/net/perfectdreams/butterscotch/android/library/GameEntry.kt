package net.perfectdreams.butterscotch.android.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GameEntry(
    val id: String,
    val title: String,
    val gameType: GameType,
    val importedAtMillis: Long,
    val favorited: Boolean
) {
    @Serializable
    sealed class GameType {
        @Serializable
        @SerialName("GameMakerStudio")
        class GameMakerStudio(
            val wadVersion: Int,
            val filename: String
        ) : GameType()

        // We keep it like this for when we decide to add new GameMaker versions :3
    }
}