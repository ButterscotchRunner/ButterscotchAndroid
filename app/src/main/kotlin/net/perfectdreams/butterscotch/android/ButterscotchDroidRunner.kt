package net.perfectdreams.butterscotch.android

import android.util.Log
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.concurrent.Executors
import kotlin.plus

// The Butterscotch Android API is actually "global bound", but we use a class to help managing things here (and will be useful if we refactor down the road)
class ButterscotchDroidRunner(val dataWinPath: String, val savesPath: String) {
    companion object {
        private const val TAG = "ButterscotchRenderLoop"

        private val glExec = Executors.newSingleThreadExecutor { Thread(it, "ButterscotchGLRender") }
        private val glDispatcher = glExec.asCoroutineDispatcher()
        private val glScope = CoroutineScope(glDispatcher + SupervisorJob())
    }

    private var renderJob: Job? = null
    private var runnerStarted = false
    private var started = false

    fun startRenderLoop(surface: Surface) {
        require(renderJob == null) { "Trying to start a renderJob while one is already active! Bug?" }

        Log.i(TAG, "Starting rendering loop...")

        renderJob = glScope.launch {
            try {
                Log.i(TAG, "Binding surface to window...")

                ButterscotchNative.bindWindow(surface)

                if (!runnerStarted) {
                    // We only start the runner here because we NEED to have an EGL context, because the GLRenderer needs it on glInit
                    Log.i(TAG, "Starting runner...")
                    ButterscotchNative.startRunner(dataWinPath, savesPath)
                    runnerStarted = true
                }

                while (isActive) {
                    Log.i(TAG, "Tick!")

                    val keepRunning = ButterscotchNative.runOneFrame()

                    Log.i(TAG, "Ran one frame! Keep running? $keepRunning")

                    if (!keepRunning)
                        break // Game requested exit

                    // Yield so other tasks (like our clean up task) can be executed
                    // The reason for that is because we don't have any suspension points here on the loop, because Butterscotch sleeps the thread
                    // (probably my first time using yield tbh)
                    yield()
                }
            } finally {
                ButterscotchNative.unbindWindow()
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

                ButterscotchNative.stopRunner()
                ButterscotchNative.teardownEgl()
                ButterscotchNative.markExited()

                renderJob = null
                started = false
                runnerStarted = false
            }
        }
    }
}
