package net.perfectdreams.butterscotch.android

import android.content.res.AssetManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File
import java.io.FileOutputStream

/**
 * Hosts a [SurfaceView] that the native side draws into, with a Compose-based virtual gamepad
 * overlay on top.
 *
 * The native side owns the EGL context + render thread. We forward [SurfaceHolder.Callback]
 * lifecycle events to [ButterscotchNative], and that's the whole bridge — there's no Kotlin-side
 * renderer to manage, no GLSurfaceView, no preserveEGLContextOnPause hack. Rotation and re-parent
 * just destroy/recreate the underlying Android Surface; the native context keeps its GL state.
 *
 * On launch we extract `assets/undertale/` into `filesDir/butterscotch/undertale/` if it isn't
 * there yet, point the runner at it, and start the render thread once.
 */
class GameActivity : ComponentActivity() {
    var butterscotchRunner: ButterscotchDroidRunner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on while the game runs - matches the GLFW build's default behavior.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val gameDir = File(filesDir, GAME_DIR_NAME).apply { mkdirs() }
        val bundleDir = File(gameDir, GAME_ASSET_DIR).apply { mkdirs() }
        val dataWin = File(bundleDir, "data.win")
        val savesDir = File(gameDir, "saves").apply { mkdirs() }

        if (!dataWin.exists()) {
            try {
                extractAssetTree(GAME_ASSET_DIR, bundleDir)
                Log.i(TAG, "Extracted $GAME_ASSET_DIR/ to $bundleDir")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract $GAME_ASSET_DIR from assets", e)
                finish()
                return
            }
            if (!dataWin.exists()) {
                Log.e(TAG, "Expected data.win at $dataWin after extraction; assets are probably empty")
                finish()
                return
            }
        }

        // Reset the exit latch — hasExited is process-singleton state, so a previous session's
        // exit would otherwise immediately finish() us via the LaunchedEffect below.
        ButterscotchNative.resetExitLatch()

        val butterscotchRunner = ButterscotchDroidRunner(dataWin.absolutePath, savesDir.absolutePath)
        this.butterscotchRunner = butterscotchRunner

        setContent {
            // VirtualKeyState lives here (not inside the gamepad) so we can release-all on layout
            // changes that reflow the gamepad's Composable tree. If it lived inside GameControls
            // and Compose discarded that subtree across an orientation flip, held keys could "leak"
            // into the runner with no Composable left to release them.
            val keys = remember { VirtualKeyState() }

            // Auto-finish when the native runner reports it has exited (game quit, fatal error).
            val hasExited = ButterscotchNative.hasExited
            LaunchedEffect(hasExited) {
                if (hasExited) finish()
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

                    LaunchedEffect(layoutMode) { keys.releaseAll() }

                    when (layoutMode) {
                        LayoutMode.Overlay -> {
                            Box(Modifier.fillMaxSize()) {
                                gameSurface(Modifier.fillMaxSize())
                                GameControls(keys = keys, modifier = Modifier.fillMaxSize())
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
                    releaseAllKeys = { keys.releaseAll() }
                )
            }
        }
    }

    override fun onDestroy() {
        butterscotchRunner?.requestExit()
        super.onDestroy()
    }

    private fun extractAssetTree(assetPath: String, destDir: File) {
        val children = assets.list(assetPath) ?: emptyArray()
        if (children.isEmpty()) {
            destDir.parentFile?.mkdirs()
            assets.open(assetPath, AssetManager.ACCESS_STREAMING).use { input ->
                FileOutputStream(destDir).use { output -> input.copyTo(output) }
            }
            return
        }
        destDir.mkdirs()
        for (child in children) {
            val childAssetPath = "$assetPath/$child"
            val childDest = File(destDir, child)
            val grandChildren = assets.list(childAssetPath) ?: emptyArray()
            if (grandChildren.isEmpty()) {
                assets.open(childAssetPath, AssetManager.ACCESS_STREAMING).use { input ->
                    FileOutputStream(childDest).use { output -> input.copyTo(output) }
                }
            } else {
                extractAssetTree(childAssetPath, childDest)
            }
        }
    }

    companion object {
        private const val TAG = "GameActivity"
        private const val GAME_DIR_NAME = "butterscotch"
        private const val GAME_ASSET_DIR = "undertale"
    }
}
