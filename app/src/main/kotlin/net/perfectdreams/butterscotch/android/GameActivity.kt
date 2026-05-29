package net.perfectdreams.butterscotch.android

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import net.perfectdreams.butterscotch.android.components.GameControls
import net.perfectdreams.butterscotch.android.components.MenuOverlay
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import java.util.UUID

/**
 * Hosts a [SurfaceView] that the native side draws into, with a Compose-based virtual gamepad
 * overlay on top.
 *
 * The native side owns the EGL context + render thread. We forward [SurfaceHolder.Callback]
 * lifecycle events to [ButterscotchNative], and that's the whole bridge — there's no Kotlin-side
 * renderer to manage, no GLSurfaceView, no preserveEGLContextOnPause hack. Rotation and re-parent
 * just destroy/recreate the underlying Android Surface; the native context keeps its GL state.
 *
 * Launched with [EXTRA_GAME_ID]; the WAD path and saves directory are resolved through
 * [net.perfectdreams.butterscotch.android.library.GameLibrary] from that id. Finishes immediately if the id is missing or unknown (e.g. the
 * library entry was removed between the launcher's snapshot and the click).
 */
class GameActivity : ComponentActivity() {
    var butterscotchRunner: ButterscotchDroidRunner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Libraries.loadGameLibrary(this.applicationContext)
        Libraries.loadLayoutLibrary(this.applicationContext)

        // Keep the screen on while the game runs - matches the GLFW build's default behavior.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val gameId = intent.getStringExtra(EXTRA_GAME_ID)
        if (gameId == null) {
            Log.e(TAG, "GameActivity launched without $EXTRA_GAME_ID extra")
            finish()
            return
        }
        val library = Libraries.getGameLibrary()
        val entry = library.findById(gameId)
        if (entry == null) {
            Log.e(TAG, "No library entry for gameId=$gameId")
            finish()
            return
        }
        val wadFile = library.wadPath(entry)
        val savesDir = library.savesDir(entry).apply { mkdirs() }
        if (!wadFile.exists()) {
            Log.e(TAG, "WAD file missing at $wadFile (library entry is stale)")
            finish()
            return
        }

        // Reset the exit latch — hasExited is process-singleton state, so a previous session's
        // exit would otherwise immediately finish() us via the LaunchedEffect below.
        ButterscotchNative.resetExitLatch()

        val butterscotchRunner = ButterscotchDroidRunner(wadFile.absolutePath, savesDir.absolutePath)
        this.butterscotchRunner = butterscotchRunner

        setContent {
            // VirtualKeyState lives here (not inside the gamepad) so we can release-all on layout
            // changes that reflow the gamepad's Composable tree. If it lived inside GameControls
            // and Compose discarded that subtree across an orientation flip, held keys could "leak"
            // into the runner with no Composable left to release them.
            val keys = remember { VirtualKeyState(butterscotchRunner) }

            var editMode by remember { mutableStateOf(false) }
            // Toggling edit mode drops any held keys so a press in flight does not stick.
            LaunchedEffect(editMode) { keys.releaseAll() }

            // Auto-finish when the native runner reports it has exited (game quit, fatal error).
            val hasExited = ButterscotchNative.hasExited
            LaunchedEffect(hasExited) {
                if (hasExited)
                    finish()
            }

            // movableContentOf wraps the SurfaceView so re-parenting it between Overlay/Stacked
            // layouts doesn't destroy and recreate the View. The SurfaceView's underlying Android
            // Surface MAY still be recreated by the system (and that's fine — the native side
            // handles surfaceDestroyed/Created), but keeping the View instance means we don't
            // re-attach the SurfaceHolder.Callback every time and the transition is cheaper.
            val gameSurface = remember {
                movableContentOf<Modifier> { modifier ->
                    AndroidView(
                        modifier = modifier,
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        butterscotchRunner.startRenderLoop(holder.surface)
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        butterscotchRunner.stopRenderLoop()
                                    }
                                })
                            }
                        }
                    )
                }
            }

            Box(
                Modifier
                    .background(Color.Black)
                    .fillMaxSize()
            ) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val gameSize = ButterscotchNative.currentGameSize
                    val gameAspect = if (gameSize != null && gameSize.height > 0) {
                        gameSize.width.toFloat() / gameSize.height.toFloat()
                    } else {
                        4f / 3f
                    }
                    val deviceAspect = constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat()
                    val layoutMode = pickLayoutMode(gameAspect, deviceAspect)

                    LaunchedEffect(layoutMode) {
                        keys.releaseAll()
                    }

                    val layoutLib = Libraries.getLayoutLibrary()
                    val isPortrait = constraints.maxHeight >= constraints.maxWidth

                    val liveEntry = requireNotNull(library.findById(gameId))

                    // Resolve this game's assigned layout for the current orientation, falling back to the built-in default if the id is missing.
                    // Keyed on (isPortrait, assignedId) so it  re-resolves on rotation AND when the assignment changes (after Save As).
                    // An Overlay/Stacked reflow changes neither, so in-progress edits survive it.
                    val assignedId = if (isPortrait) liveEntry.portraitLayout else liveEntry.landscapeLayout
                    val fallbackId = if (isPortrait) LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT else LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT

                    Log.i(TAG, "Using $assignedId for the gamepad layout... The fallback ID is $fallbackId")

                    var layout by remember(isPortrait, assignedId) { mutableStateOf(layoutLib.findById(assignedId) ?: layoutLib.findById(fallbackId)!!) }
                    // Save only applies to user layouts; the built-in defaults are read-only.
                    val canSave = layout.id != LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT && layout.id != LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT
                    val onSave = { layoutLib.upsert(layout) }
                    val onSaveAs = { name: String ->
                        // Fork to a fresh id with the given name, persist it, and point this game's
                        // orientation-specific layout at the copy so it loads next time. Keep editing it.
                        val forked = layout.copy(id = UUID.randomUUID(), fancyName = name)
                        layoutLib.upsert(forked)
                        library.update(entry.id) { e ->
                            if (isPortrait) e.copy(portraitLayout = forked.id) else e.copy(landscapeLayout = forked.id)
                        }
                        layout = forked
                    }

                    when (layoutMode) {
                        LayoutMode.Overlay -> {
                            Box(Modifier.fillMaxSize()) {
                                gameSurface(Modifier.fillMaxSize())
                                GameControls(
                                    layout = layout,
                                    editMode = editMode,
                                    onLayoutChange = { layout = it },
                                    onExitEditMode = { editMode = false },
                                    canSave = canSave,
                                    onSave = onSave,
                                    onSaveAs = onSaveAs,
                                    keys = keys,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        LayoutMode.Stacked -> {
                            Column(Modifier.fillMaxSize()) {
                                gameSurface(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(gameAspect)
                                )
                                GameControls(
                                    layout = layout,
                                    editMode = editMode,
                                    onLayoutChange = { layout = it },
                                    onExitEditMode = { editMode = false },
                                    canSave = canSave,
                                    onSave = onSave,
                                    onSaveAs = onSaveAs,
                                    keys = keys,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .safeDrawingPadding()
                                )
                            }
                        }
                    }
                }

                MenuOverlay(
                    onExitGame = {
                        // Non-blocking: request exit. The render thread winds down on its own,
                        // marks ButterscotchNative.hasExited, and the LaunchedEffect above sees the
                        // change and calls finish(). onDestroy then joins via stopAndJoin.
                        // Doing the blocking join here directly would prevent recomposition.
                        butterscotchRunner.requestExit()
                    },
                    releaseAllKeys = { keys.releaseAll() },
                    onEditLayout = { editMode = true }
                )
            }
        }
    }

    override fun onDestroy() {
        butterscotchRunner?.requestExit()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "GameActivity"
        const val EXTRA_GAME_ID = "net.perfectdreams.butterscotch.android.EXTRA_GAME_ID"
    }
}
