package net.perfectdreams.butterscotch.android.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.android.UUIDAsStringSerializer
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import java.util.UUID

@Serializable
data class GameEntry(
    @Serializable(with = UUIDAsStringSerializer::class)
    val id: UUID,
    val title: String,
    val gameType: GameType,
    val importedAtMillis: Long,
    val favorited: Boolean,
    val saveSlots: List<SaveSlot>,
    /** Bumped whenever the icon file is rewritten, so launcher UI invalidates its bitmap cache. */
    val iconRevision: Long = 0,
    @Serializable(with = UUIDAsStringSerializer::class)
    val portraitLayout: UUID = LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT,
    @Serializable(with = UUIDAsStringSerializer::class)
    val landscapeLayout: UUID = LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT,
    /** OS reported to the game through GML's os_type / os_* builtins. Defaults to Windows, which is what the C runner hardcoded before this was selectable. */
    val runnerOs: RunnerOs = RunnerOs.WINDOWS,
) {
    // Mirrors the YoYoOperatingSystem enum in Butterscotch's runner.h. nativeValue MUST match the
    // C enum's integer value, since it is passed straight through startRunner to runner->osType.
    // The OS_LLVM_* debug variants (65536+) are intentionally omitted; they are internal build
    // flavors, not meaningful targets to pick for a WAD.
    @Serializable
    enum class RunnerOs(val nativeValue: Int, val fancyName: String) {
        WINDOWS(0, "Windows"),
        MACOSX(1, "macOS"),
        PSP(2, "PSP"),
        IOS(3, "iOS"),
        ANDROID(4, "Android"),
        SYMBIAN(5, "Symbian"),
        LINUX(6, "Linux"),
        WINPHONE(7, "Windows Phone"),
        TIZEN(8, "Tizen"),
        WIN8NATIVE(9, "Windows 8 Native"),
        WIIU(10, "Wii U"),
        THREEDS(11, "3DS"),
        PSVITA(12, "PS Vita"),
        BB10(13, "BlackBerry 10"),
        PS4(14, "PS4"),
        XBOXONE(15, "Xbox One"),
        PS3(16, "PS3"),
        XBOX360(17, "Xbox 360"),
        UWP(18, "UWP"),
        AMAZON(19, "Amazon"),
        SWITCH(20, "Switch"),
    }

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

    @Serializable
    data class SaveSlot(
        @Serializable(with = UUIDAsStringSerializer::class)
        val id: UUID,
        val active: Boolean,
        val fancyName: String,
    )
}