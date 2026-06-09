package net.perfectdreams.butterscotch.android.state

import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.UUIDAsStringSerializer
import java.util.UUID

// Device-local app state, things the app records about this install (NOT user-facing settings, those live in ButterscotchSettings)
@Serializable
data class AppState(
    // Bump this when we need to migrate an old state.json to a newer shape
    val version: Int = 1,
    @Serializable(with = UUIDAsStringSerializer::class)
    val installId: UUID? = null,
    val isButterscotchSuperDuperPlus: Boolean = false,
)
