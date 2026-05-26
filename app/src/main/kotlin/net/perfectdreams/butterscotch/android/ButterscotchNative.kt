package net.perfectdreams.butterscotch.android

import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize

/**
 * Flat JNI surface — direct mirror of [src/web/main.c]'s shape. Each function does one thing and
 * returns; the loop, the lifecycle, and the dedicated render thread live in [ButterscotchDroidRunner] on the
 * JVM side.
 *
 * Threading contract: every render-side function ([bindWindow], [unbindWindow], [startRunner],
 * [runOneFrame], [stopRunner], [teardownEgl]) MUST be called from the same OS thread, because
 * `eglMakeCurrent` binds the EGL context to the calling thread. [ButterscotchDroidRunner] is the only thing
 * that should call these. The exception is [onKey], which is safe to call from any thread (its
 * events are queued under a mutex on the C side and drained inside `runOneFrame`).
 *
 * Native -> Kotlin push notifications ([onTitleChanged], [onGameSizeChanged]) come in via
 * @JvmStatic methods that the C side invokes through cached jmethodIDs. They fire from the render
 * thread; Compose's snapshot system handles the cross-thread state write safely.
 */
object ButterscotchNative {
    init {
        System.loadLibrary("butterscotch")
        init()
    }

    external fun init()

    // ===[ Render-side JNI — all must run on RenderLoop's thread ]===

    /** Bind a [Surface] to the EGL context (creates the context lazily on first call). */
    external fun bindWindow(surface: Surface): Boolean

    /** Destroy the EGL window surface. The context survives so GL state persists. */
    external fun unbindWindow()

    /** Parse data.win, build VM/renderer/audio, fire first room. Requires a bound surface. */
    external fun startRunner(dataWinPath: String, savesPath: String): Boolean

    /** Run one game tick + render + swap. Returns false when the runner has asked to exit. */
    external fun runOneFrame(): Boolean

    /** Tear down runner/renderer/audio. EGL context still alive. */
    external fun stopRunner()

    /** Destroy EGL context + display. Call last, after [stopRunner]. */
    external fun teardownEgl()

    // ===[ Thread-safe JNI ]===

    /** Forward a keyboard event (GameMaker vk_* code). Safe from any thread. */
    external fun onKey(keyCode: Int, isDown: Boolean)

    // ===[ Native -> Kotlin push state ]===

    var currentTitle: String? by mutableStateOf(null)
        private set

    @JvmStatic
    fun onTitleChanged(title: String) {
        currentTitle = title
    }

    var currentGameSize: IntSize? by mutableStateOf(null)
        private set

    @JvmStatic
    fun onGameSizeChanged(width: Int, height: Int) {
        currentGameSize = IntSize(width, height)
    }

    /**
     * Flips true when the runner has exited (either the game requested quit, or [ButterscotchDroidRunner] tore
     * it down on user request). The Activity observes this and calls finish().
     */
    var hasExited: Boolean by mutableStateOf(false)
        private set

    internal fun markExited() {
        hasExited = true
    }

    /** Clear the exit latch — process-singleton state, so a previous session would otherwise
     *  immediately finish a freshly-launched GameActivity. */
    fun resetExitLatch() {
        hasExited = false
    }
}
