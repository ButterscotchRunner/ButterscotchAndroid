package net.perfectdreams.butterscotch.android.settings

import kotlinx.serialization.Serializable

@Serializable
data class ButterscotchSettings(
    // Bump this when we need to migrate an old settings.json to a newer shape
    val version: Int = 1,
    val enableHapticFeedback: Boolean = true,
)
