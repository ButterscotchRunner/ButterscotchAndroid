package net.perfectdreams.butterscotch.android.state

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class AppStateStore private constructor(
    private val indexFile: File,
    initial: AppState
) {
    companion object {
        private const val TAG = "AppStateStore"
        private const val ROOT_DIR_NAME = "butterscotch"
        private const val INDEX_FILE_NAME = "state.json"

        // encodeDefaults = true so "version" is always written out, even while it equals its default
        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun load(context: Context): AppStateStore {
            val rootDir = File(context.filesDir, ROOT_DIR_NAME).apply { mkdirs() }
            val indexFile = File(rootDir, INDEX_FILE_NAME)

            val initial = if (indexFile.exists()) {
                try {
                    json.decodeFromString<AppState>(indexFile.readText(Charsets.UTF_8))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse $indexFile; starting with defaults", e)
                    AppState()
                }
            } else {
                AppState()
            }

            val store = AppStateStore(indexFile, initial)
            // Generate the install id once on first run and persist it immediately so every later launch reuses the same value
            if (initial.installId.isBlank()) {
                store.update { copy(installId = UUID.randomUUID().toString()) }
            }
            return store
        }
    }

    // Compose-observable, reading this in a composable subscribes it to changes
    var state: AppState by mutableStateOf(initial)
        private set

    // Mutate via copy(), then persist, e.g. store.update { copy(isPlus = true) }
    fun update(transform: AppState.() -> AppState) {
        state = state.transform()
        save()
    }

    private fun save() {
        try {
            indexFile.writeText(json.encodeToString(state), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write $indexFile", e)
        }
    }
}
