package net.perfectdreams.butterscotch.android.layouts

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class LayoutLibrary private constructor(
    private val indexFile: File,
    private val initial: List<GamepadLayout>
) {
    companion object {
        private const val TAG = "LayoutLibrary"
        private const val ROOT_DIR_NAME = "butterscotch"
        private const val INDEX_FILE_NAME = "layouts.json"
        val DEFAULT_PORTRAIT_LAYOUT = UUID.fromString("cb231fdd-df1d-44da-b850-ebb5a0a225f3")
        val DEFAULT_LANDSCAPE_LAYOUT = UUID.fromString("6fab9f1d-60c7-46ea-909b-9b5a92da0dd5")

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

        fun load(context: Context): LayoutLibrary {
            val rootDir = File(context.filesDir, ROOT_DIR_NAME).apply { mkdirs() }
            val indexFile = File(rootDir, INDEX_FILE_NAME)

            val initial = if (indexFile.exists()) {
                try {
                    json.decodeFromString<List<GamepadLayout>>(indexFile.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse $indexFile; starting with empty library", e)
                    emptyList()
                }
            } else {
                emptyList()
            }

            // To avoid upgrading issues, we WON'T persist the default gamepads on the config
            val realInitial = mutableListOf(
                GamepadLayout(
                    id = DEFAULT_LANDSCAPE_LAYOUT,
                    fancyName = "Default Landscape",
                    orientation = GamepadLayout.GamepadTargetOrientation.LANDSCAPE,
                    elements = listOf(
                        GamepadElement.Joystick(
                            positionX = 0.16, positionY = 0.74, scale = 0.42, opacity = 1.0,
                            up = InputBinding.Keyboard(GmlKey.UP.code),
                            down = InputBinding.Keyboard(GmlKey.DOWN.code),
                            left = InputBinding.Keyboard(GmlKey.LEFT.code),
                            right = InputBinding.Keyboard(GmlKey.RIGHT.code),
                            id = UUID.randomUUID()
                        ),
                        GamepadElement.Key(positionX = 0.66, positionY = 0.78, scale = 0.22, opacity = 1.0, label = null, trigger = KeyTrigger.Press, binding = InputBinding.Keyboard(GmlKey.C.code), id = UUID.randomUUID()),
                        GamepadElement.Key(positionX = 0.79, positionY = 0.78, scale = 0.22, opacity = 1.0, label = null, trigger = KeyTrigger.Press, binding = InputBinding.Keyboard(GmlKey.X.code), id = UUID.randomUUID()),
                        GamepadElement.Key(positionX = 0.92, positionY = 0.78, scale = 0.22, opacity = 1.0, label = null, trigger = KeyTrigger.Press, binding = InputBinding.Keyboard(GmlKey.Z.code), id = UUID.randomUUID()),
                        GamepadElement.Menu(positionX = 0.94, positionY = 0.10, scale = 0.14, opacity = 1.0, id = UUID.randomUUID()),
                    )
                ),
                GamepadLayout(
                    id = DEFAULT_PORTRAIT_LAYOUT,
                    fancyName = "Default Portrait",
                    orientation = GamepadLayout.GamepadTargetOrientation.PORTRAIT,
                    // Portrait is narrow, so the action buttons stack vertically on the right
                    // (Z bottommost = primary, where the thumb rests) instead of in a row.
                    elements = listOf(
                        GamepadElement.Joystick(
                            positionX = 0.25, positionY = 0.85, scale = 0.42, opacity = 1.0,
                            up = InputBinding.Keyboard(GmlKey.UP.code),
                            down = InputBinding.Keyboard(GmlKey.DOWN.code),
                            left = InputBinding.Keyboard(GmlKey.LEFT.code),
                            right = InputBinding.Keyboard(GmlKey.RIGHT.code),
                            id = UUID.randomUUID()
                        ),
                        GamepadElement.Key(positionX = 0.82, positionY = 0.56, scale = 0.20, opacity = 1.0, label = null, trigger = KeyTrigger.Press, binding = InputBinding.Keyboard(GmlKey.C.code), id = UUID.randomUUID()),
                        GamepadElement.Key(positionX = 0.82, positionY = 0.70, scale = 0.20, opacity = 1.0, label = null, trigger = KeyTrigger.Press, binding = InputBinding.Keyboard(GmlKey.X.code), id = UUID.randomUUID()),
                        GamepadElement.Key(positionX = 0.82, positionY = 0.84, scale = 0.20, opacity = 1.0, label = null, trigger = KeyTrigger.Press, binding = InputBinding.Keyboard(GmlKey.Z.code), id = UUID.randomUUID()),
                        GamepadElement.Menu(positionX = 0.92, positionY = 0.95, scale = 0.14, opacity = 1.0, id = UUID.randomUUID()),
                    )
                )
            )

            realInitial.addAll(initial)

            return LayoutLibrary(indexFile, realInitial)
        }
    }

    val entries: SnapshotStateList<GamepadLayout> = mutableStateListOf<GamepadLayout>().apply { addAll(initial) }

    fun findById(id: UUID): GamepadLayout? = entries.firstOrNull { it.id == id }

    // Insert or replace a layout by id, then persist. Backs the in-game editor's Save / Save As.
    fun upsert(layout: GamepadLayout) {
        val index = entries.indexOfFirst { it.id == layout.id }
        if (index >= 0) entries[index] = layout else entries.add(layout)
        save()
    }

    // Serializes a single layout to pretty-printed JSON for sharing/export
    fun exportToJson(layout: GamepadLayout): String = json.encodeToString(layout)

    // Parses a shared layout JSON and stores it as a brand-new copy. The id is always regenerated so an
    // import never overwrites an existing layout nor collides with a built-in default (which would be shadowed
    // on the next load). Throws if the text isn't a valid layout. Returns the stored copy.
    fun importFromJson(text: String): GamepadLayout {
        val parsed = json.decodeFromString<GamepadLayout>(text)
        val copy = parsed.copy(id = UUID.randomUUID())
        upsert(copy)
        return copy
    }

    // True for the two built-in layouts, which are re-seeded on every load and can't be edited or deleted
    fun isBuiltIn(id: UUID): Boolean = id == DEFAULT_PORTRAIT_LAYOUT || id == DEFAULT_LANDSCAPE_LAYOUT

    // Deletes a user layout. Built-in defaults are re-seeded on every load, so they can't be removed.
    // Games still pointing at the deleted id fall back to the matching default at launch (see GameActivity)
    fun remove(id: UUID) {
        require(!isBuiltIn(id)) { "Cannot delete a built-in layout" }
        entries.removeAll { it.id == id }
        save()
    }

    // Built-in defaults are re-seeded on every load, so we never write them out - that keeps them
    // upgradeable and out of layouts.json. Only user layouts are persisted.
    private fun save() {
        val persistable = entries.filter { !isBuiltIn(it.id) }
        try {
            indexFile.writeText(json.encodeToString(persistable), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $indexFile", e)
        }
    }
}
