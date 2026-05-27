package net.perfectdreams.butterscotch.android

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.perfectdreams.butterscotch.android.library.GameLibrary
import java.io.File

/**
 * Copies a user-picked folder (via Storage Access Framework tree Uri) into the app's per-game
 * bundle directory and detects which GameMaker WAD filename it contains.
 *
 * Why copy and not just hold the tree Uri? `takePersistableUriPermission` is fragile (cleared on
 * reboot in some OEM ROMs, lost if the source folder moves) and `DocumentFile` access is slow.
 * Copying once at import time lets the runner read files via plain POSIX paths forever after.
 *
 * Validation policy: the only header check is "does a known WAD filename exist in the picked
 * folder?". The actual parse via [ParsedDataWin.parseLight] runs on the copied file *after*
 * staging — and may crash the process if the file isn't a real WAD (see
 * `datawin-parse-fatal-on-error` project memory). We accept that for now.
 */
object GameImporter {
    private const val TAG = "GameImporter"

    /** Filenames the runner recognizes as the GameMaker WAD across export targets. */
    val WAD_FILENAMES = listOf(
        "data.win",   // Windows
        "game.unx",   // Linux
        "game.ios",   // iOS
        "game.droid", // Android
        "game.psp",   // PSP / PSVita / older console targets
        "game.osx",   // macOS
    )

    sealed interface Result {
        /**
         * Folder was successfully copied into [stagedDir]. The detected WAD filename is
         * [wadFilename]. [suggestedTitle] is the GEN8 displayName/name if non-empty, else null
         * (caller falls back to folder name). [wadVersion] is the raw GEN8 wadVersion byte; the
         * configure screen displays it.
         *
         * IMPORTANT: the caller must either call [net.perfectdreams.butterscotch.android.library.GameLibrary.commit] with the staged game (passed
         * back via [staged]) or [net.perfectdreams.butterscotch.android.library.GameLibrary.discardStaging] — leaving it unhandled leaks the
         * copied bundle on disk.
         */
        data class Success(
            val staged: GameLibrary.StagedGame,
            val wadFilename: String,
            val suggestedTitle: String?,
            val wadVersion: Int,
            val folderName: String,
        ) : Result

        /** The picked folder had none of [WAD_FILENAMES] at its top level. */
        data class MissingWad(val folderName: String) : Result

        /** Tree Uri was unreadable, or copy failed midway. Staged dir (if any) is deleted. */
        data class Failure(val message: String) : Result
    }

    /**
     * Pick a folder → copy its contents into a fresh staging dir → peek the WAD metadata. Runs on
     * IO dispatcher.
     */
    suspend fun import(
        context: Context,
        treeUri: Uri,
        library: GameLibrary,
    ): Result = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
        if (root == null || !root.isDirectory) {
            return@withContext Result.Failure("Selected location is not a readable folder.")
        }

        val folderName = root.name ?: "Imported Game"

        val wadDoc = root.listFiles().firstOrNull { doc ->
            doc.isFile && doc.name in WAD_FILENAMES
        } ?: return@withContext Result.MissingWad(folderName)

        val wadFilename = wadDoc.name!!
        val staged = library.beginStaging()

        try {
            copyTree(context, root, staged.bundleDir)
        } catch (e: Exception) {
            Log.e(TAG, "Copy failed for $treeUri", e)
            library.discardStaging(staged)
            return@withContext Result.Failure("Couldn't copy folder: ${e.message}")
        }

        val wadFile = File(staged.bundleDir, wadFilename)
        if (!wadFile.exists()) {
            // Shouldn't happen — we just confirmed the WAD doc existed and copyTree didn't throw.
            // But if it does, fail loud rather than letting the native parser explode.
            library.discardStaging(staged)
            return@withContext Result.Failure("WAD vanished after copy (this is a bug).")
        }

        // Peek metadata. parseLight may exit() on a corrupt WAD — accepted (see memory).
        val (suggestedTitle, wadVersion) = ParsedDataWin.parseLight(wadFile.absolutePath)?.use { dw ->
            val name = (dw.displayName ?: dw.name)?.takeIf { it.isNotBlank() }
            name to dw.wadVersion
        } ?: (null to -1)

        Result.Success(
            staged = staged,
            wadFilename = wadFilename,
            suggestedTitle = suggestedTitle,
            wadVersion = wadVersion,
            folderName = folderName,
        )
    }

    /**
     * Recursive DocumentFile → File copy. Mirrors `GameActivity.extractAssetTree`'s shape but
     * sources from the SAF tree instead of assets/.
     */
    private fun copyTree(context: Context, src: DocumentFile, dest: File) {
        if (!dest.exists()) dest.mkdirs()
        for (child in src.listFiles()) {
            val name = child.name ?: continue
            val target = File(dest, name)
            if (child.isDirectory) {
                copyTree(context, child, target)
            } else if (child.isFile) {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Could not open child $name for reading")
            }
        }
    }
}
