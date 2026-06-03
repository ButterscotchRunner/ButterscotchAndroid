package net.perfectdreams.butterscotch.android

import android.util.Log
import android.view.Surface
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.Executors

// The Butterscotch Android API is actually "global bound", but we use a class to help managing things here (and will be useful if we refactor down the road)
class ButterscotchDroidRunner(val dataWinPath: String, val savesPath: String, val osType: Int, val enablePhysicalControllers: Boolean, val enablePhysicalKeyboard: Boolean, var enableWidescreenHack: Boolean) {
    companion object {
        private const val TAG = "ButterscotchRenderLoop"

        private val glExec = Executors.newSingleThreadExecutor { Thread(it, "ButterscotchGLRender") }
        private val glDispatcher = glExec.asCoroutineDispatcher()
        private val glScope = CoroutineScope(glDispatcher + SupervisorJob())
    }

    private val egl = ButterscotchEGL()
    private var renderJob: Job? = null
    private var runnerStarted = false
    private var started = false
    var paused = MutableStateFlow(false)
    private val inputChannel = Channel<InputEvent>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST,)
    val gamepadRouter = GamepadRouter(this)
    val keyboardRouter = KeyboardRouter(this)
    var fastForwardSpeed = 1.0f
    var surfaceSize = IntSize.Zero
    val freeCamera = MutableStateFlow(FreeCameraState())

    data class FreeCameraState(
        val active: Boolean = false,
        val panX: Float = 0.0f,
        val panY: Float = 0.0f,
        val zoom: Float = 1.0f
    )

    /**
     * Forward a SurfaceHolder size change to the EGL layer so the next frame renders at the right resolution.
     *
     * This NEEDS to be synchronized with the render thread, which is why we use a coroutine.
     */
    fun onSurfaceResized(width: Int, height: Int) {
        glScope.launch {
            egl.updateSize(width, height)
        }
    }

    fun startRenderLoop(surface: Surface) {
        require(renderJob == null) { "Trying to start a renderJob while one is already active! Bug?" }

        Log.i(TAG, "Starting rendering loop...")

        renderJob = glScope.launch {
            try {
                Log.i(TAG, "Binding surface to window...")

                if (!egl.bindWindow(surface)) {
                    Log.e(TAG, "Failed to bind EGL surface! Aborting render loop...")
                    return@launch
                }

                if (!runnerStarted) {
                    // We only start the runner here because we NEED to have an EGL context, because the GLRenderer needs it on glInit
                    Log.i(TAG, "Starting runner...")
                    ButterscotchNative.startRunner(dataWinPath, savesPath, osType)
                    runnerStarted = true
                }

                var lastFrameStartNs = System.nanoTime()

                // Don't worry about the egl.hasSurface check, if the surface dies, the finally block will be executed and (hopefully) a new surface will be created,
                // starting a whole new render loop :3
                while (isActive && egl.hasSurface) {
                    if (this@ButterscotchDroidRunner.paused.value) {
                        this@ButterscotchDroidRunner.paused.first { !it } // Wait until we are NOT paused
                        // Reset the frame clock so resuming doesn't inject a giant delta_time spike
                        lastFrameStartNs = System.nanoTime()
                    }

                    val freeCam = this@ButterscotchDroidRunner.freeCamera.value

                    if ((this@ButterscotchDroidRunner.enableWidescreenHack || freeCam.active) && this@ButterscotchDroidRunner.surfaceSize.width != 0 && this@ButterscotchDroidRunner.surfaceSize.height != 0) {
                        ButterscotchNative.setWidescreenHackAspectRatio(this@ButterscotchDroidRunner.surfaceSize.width / this@ButterscotchDroidRunner.surfaceSize.height.toFloat())
                    } else {
                        ButterscotchNative.setWidescreenHackAspectRatio(0.0f)
                    }

                    ButterscotchNative.setFreeCamera(freeCam.panX, freeCam.panY, freeCam.zoom)

                    val frameStartNs = System.nanoTime()
                    val deltaTimeSeconds = ((frameStartNs - lastFrameStartNs) / 1_000_000_000.0).toFloat()
                    lastFrameStartNs = frameStartNs

                    ButterscotchNative.beginFrame()
                    drainPendingInput()
                    val stepStatus = ButterscotchNative.stepAndDraw(egl.width, egl.height, deltaTimeSeconds)

                    when (stepStatus) {
                        ButterscotchNative.BUTTERSCOTCH_DROID_CONTINUE,
                        ButterscotchNative.BUTTERSCOTCH_DROID_CONTINUE_NO_SWAP -> {
                            if (stepStatus == ButterscotchNative.BUTTERSCOTCH_DROID_CONTINUE)
                                egl.swapBuffers()

                            val hz = (ButterscotchNative.getTargetFrameHz() * this@ButterscotchDroidRunner.fastForwardSpeed).toLong()
                            if (hz > 0) {
                                val targetNs = 1_000_000_000L / hz
                                // lastFrameStartNs is this frame's start (set above), so we pace one period from there
                                val nextDeadline = lastFrameStartNs + targetNs
                                val remaining = nextDeadline - System.nanoTime()
                                if (remaining > 2_000_000L) {
                                    Thread.sleep((remaining - 1_000_000L) / 1_000_000L)
                                }
                                while (System.nanoTime() < nextDeadline) {
                                    // spin spin spin!
                                }
                            }

                            // Yield so other tasks (like our clean up task) can be executed
                            // The reason for that is because we don't have any suspension points here on the loop, because Butterscotch sleeps the thread
                            // (probably my first time using yield tbh)
                            yield()
                        }

                        ButterscotchNative.BUTTERSCOTCH_DROID_SHOULD_EXIT -> {
                            // Game requested exit
                            requestExitInternal()
                            break
                        }
                        
                        else -> error("Unsupported return status! $stepStatus")
                    }
                }
            } finally {
                egl.unbindWindow()
            }
        }
    }

    // Blocks the main thread because we NEED to block it to avoid crashes because we need to release the old surface
    fun stopRenderLoop() {
        Log.i(TAG, "Stopping rendering loop...")
        runBlocking {
            withContext(glDispatcher) {
                renderJob?.cancelAndJoin()
                renderJob = null
            }
        }
    }

    fun requestExit() {
        Log.i(TAG, "Requesting exit!")

        runBlocking {
            withContext(glDispatcher) {
                renderJob?.cancelAndJoin()

                requestExitInternal()
            }
        }
    }

    fun setGameSurfaceSize(surfaceSize: IntSize) {
        runBlocking {
            withContext(glDispatcher) {
                this@ButterscotchDroidRunner.surfaceSize = surfaceSize
            }
        }
    }

    // Should ONLY be called in the render thread!
    fun requestExitInternal() {
        ButterscotchNative.stopRunner()
        egl.teardown()
        ButterscotchNative.markExited()

        renderJob = null
        started = false
        runnerStarted = false
    }

    sealed interface InputEvent {
        data class Key(val code: Int, val isDown: Boolean) : InputEvent
        data class Char(val codePoint: Int) : InputEvent
        data class GamepadButton(val device: Int, val button: Int, val isDown: Boolean) : InputEvent
        data class GamepadAxis(val device: Int, val axis: Int, val value: Float) : InputEvent
        data class GamepadConnected(val device: Int, val name: String?) : InputEvent
        data class GamepadDisconnected(val device: Int) : InputEvent
    }

    /**
     * Queue a key event for the runner.
     *
     * Safe to use from the UI thread because this only queues the input.
     */
    fun onKey(keyCode: Int, isDown: Boolean) {
        inputChannel.trySend(InputEvent.Key(keyCode, isDown))
    }

    /**
     * Queue a typed character (Unicode codepoint) for the runner, backing keyboard_lastchar.
     *
     * Safe to use from the UI thread because this only queues the input.
     */
    fun onChar(codePoint: Int) {
        inputChannel.trySend(InputEvent.Char(codePoint))
    }

    // Gamepad queueing. All safe to call from the UI thread (just enqueues into the Channel). The
    // render thread drains them in drainPendingInput and forwards to the native gamepad feed. The
    // Channel preserves ordering, so a Connected enqueued before its buttons is applied first.

    fun onGamepadButton(device: Int, button: Int, isDown: Boolean) {
        inputChannel.trySend(InputEvent.GamepadButton(device, button, isDown))
    }

    fun onGamepadAxis(device: Int, axis: Int, value: Float) {
        inputChannel.trySend(InputEvent.GamepadAxis(device, axis, value))
    }

    fun onGamepadConnected(device: Int, name: String?) {
        inputChannel.trySend(InputEvent.GamepadConnected(device, name))
    }

    fun onGamepadDisconnected(device: Int) {
        inputChannel.trySend(InputEvent.GamepadDisconnected(device))
    }

    /**
     * Drains all queued inputs and forwards it to the runner.
     *
     * Should ONLY be called from the render thread!
     */
    fun drainPendingInput() {
        while (true) {
            val event = inputChannel.tryReceive().getOrNull() ?: break
            when (event) {
                is InputEvent.Key -> if (event.isDown)
                    ButterscotchNative.onKeyDown(event.code)
                else
                    ButterscotchNative.onKeyUp(event.code)
                is InputEvent.Char -> ButterscotchNative.onCharacter(event.codePoint)
                is InputEvent.GamepadButton -> ButterscotchNative.gamepadButton(event.device, event.button, event.isDown)
                is InputEvent.GamepadAxis -> ButterscotchNative.gamepadAxis(event.device, event.axis, event.value)
                is InputEvent.GamepadConnected -> ButterscotchNative.gamepadConnected(event.device, event.name)
                is InputEvent.GamepadDisconnected -> ButterscotchNative.gamepadDisconnected(event.device)
            }
        }
    }
}
