package net.perfectdreams.butterscotch.android

import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.Executors

// The Butterscotch Android API is actually "global bound", but we use a class to help managing things here (and will be useful if we refactor down the road)
class ButterscotchDroidRunner(val dataWinPath: String, val savesPath: String, val osType: Int) {
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
    private val inputChannel = Channel<InputEvent>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST,)

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

                var lastFrameNs = System.nanoTime()

                // Don't worry about the egl.hasSurface check, if the surface dies, the finally block will be executed and (hopefully) a new surface will be created,
                // starting a whole new render loop :3
                while (isActive && egl.hasSurface) {
                    val frameStartNs = System.nanoTime()
                    val audioDt = ((frameStartNs - lastFrameNs) / 1_000_000_000.0)
                        .coerceIn(0.0, 0.1)
                        .toFloat()

                    ButterscotchNative.beginFrame()
                    drainPendingInput()
                    val stepStatus = ButterscotchNative.stepAndDraw(egl.width, egl.height, audioDt)

                    when (stepStatus) {
                        ButterscotchNative.BUTTERSCOTCH_DROID_CONTINUE,
                        ButterscotchNative.BUTTERSCOTCH_DROID_CONTINUE_NO_SWAP -> {
                            if (stepStatus == ButterscotchNative.BUTTERSCOTCH_DROID_CONTINUE)
                                egl.swapBuffers()

                            val hz = ButterscotchNative.getTargetFrameHz()
                            if (hz > 0) {
                                val targetNs = 1_000_000_000L / hz
                                val nextDeadline = lastFrameNs + targetNs
                                val remaining = nextDeadline - System.nanoTime()
                                if (remaining > 2_000_000L) {
                                    Thread.sleep((remaining - 1_000_000L) / 1_000_000L)
                                }
                                while (System.nanoTime() < nextDeadline) {
                                    // spin spin spin!
                                }
                                lastFrameNs = nextDeadline
                            } else {
                                lastFrameNs = System.nanoTime()
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
            }
        }
    }
}
