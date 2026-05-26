package net.perfectdreams.butterscotch.android

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

// GameMaker vk_* constants. Match the keycodes the runner already understands from a USB keyboard,
// so the C side doesn't need a touch-specific path - virtual joystick presses become regular key events.
private const val KEY_LEFT  = 37
private const val KEY_UP    = 38
private const val KEY_RIGHT = 39
private const val KEY_DOWN  = 40
private const val KEY_C     = 67
private const val KEY_X     = 88
private const val KEY_Z     = 90

/**
 * Tracks which virtual keys are currently held and forwards only edge transitions to JNI.
 * Without this, a finger dragging across the joystick would re-fire "key down" on every pointer
 * move event, which would spuriously re-trigger GameMaker's `keyboard_check_pressed` flag.
 *
 * Refcounting handles the case where two pointers happen to map to the same key at once (e.g. two
 * fingers both landing on the joystick area mapped to "up"): the key stays down until *all*
 * pointers release it. Matches what the WebKT reference frontend does with pressedRefs.
 */
class VirtualKeyState {
    private val refs = HashMap<Int, Int>()

    @Synchronized
    fun acquire(keyCode: Int) {
        val newCount = (refs[keyCode] ?: 0) + 1
        refs[keyCode] = newCount
        if (newCount == 1) {
            ButterscotchNative.onKey(keyCode, isDown = true)
        }
    }

    @Synchronized
    fun release(keyCode: Int) {
        val newCount = (refs[keyCode] ?: return) - 1
        if (newCount <= 0) {
            refs.remove(keyCode)
            ButterscotchNative.onKey(keyCode, isDown = false)
        } else {
            refs[keyCode] = newCount
        }
    }

    /** Apply a new "currently-pressed" set for a single pointer, emitting only the delta. */
    fun transition(oldKeys: Set<Int>, newKeys: Set<Int>) {
        if (oldKeys == newKeys) return
        for (k in oldKeys) if (k !in newKeys) release(k)
        for (k in newKeys) if (k !in oldKeys) acquire(k)
    }

    @Synchronized
    fun releaseAll() {
        for ((k, _) in refs) ButterscotchNative.onKey(k, isDown = false)
        refs.clear()
    }
}

/**
 * Just the on-screen gameplay controls (joystick + action buttons), no menu. Renders into whatever
 * area its [modifier] gives it - the parent decides whether that's the whole screen (Overlay layout)
 * or a dedicated controls strip below the game (Stacked layout).
 *
 * The caller owns the [VirtualKeyState] so it can release all held keys on lifecycle events (e.g.
 * the menu opening, or an orientation change recomposing the layout).
 */
@Composable
fun GameControls(keys: VirtualKeyState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        // Scale every control dimension off the smaller of width/height so the gamepad reads at the
        // right size whether the parent is a landscape Overlay (whole screen) or a Stacked-portrait
        // controls strip (narrow + tall). The min/max clamps stop controls from becoming tiny on
        // phones or absurdly large on tablets.
        //
        // For reference, on a ~360dp-wide portrait phone in Stacked mode the strip's min dimension
        // is ~360dp, giving joystick ~150dp and buttons ~79dp. Without the row-versus-column switch
        // below, joystick (~150) + 3 buttons (~237) + paddings would still overflow 360, which was
        // the original bug. The switch reroutes the buttons into a vertical column whenever the
        // available width can't host both side-by-side with breathing room.
        val ref = if (maxWidth < maxHeight) maxWidth else maxHeight
        val joystickSize = (ref * 0.42f).coerceIn(140.dp, 200.dp)
        val buttonSize   = (ref * 0.22f).coerceIn(56.dp, 80.dp)
        val buttonGap    = buttonSize * 0.22f
        val edgePad      = (ref * 0.05f).coerceIn(16.dp, 32.dp)
        // Threshold: joystick + 3 buttons side-by-side + 2 gaps + 3 paddings. If that doesn't fit,
        // stack the action buttons vertically so the joystick keeps its size and they stop colliding.
        val rowActionsWidth = joystickSize + buttonSize * 3 + buttonGap * 2 + edgePad * 3
        val verticalActions = rowActionsWidth > maxWidth

        Joystick(
            keys = keys,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(edgePad)
                .size(joystickSize)
        )
        ActionButtons(
            keys = keys,
            buttonSize = buttonSize,
            gap = buttonGap,
            vertical = verticalActions,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(edgePad)
        )
    }
}

/**
 * Menu button (hamburger) + slide-in sidebar. Always sits on top of everything, full-screen, so
 * the sidebar can cover both the game viewport and the controls regardless of which layout mode
 * GameActivity picked.
 *
 * Owns the menuOpen state and the BackHandler. Calls [releaseAllKeys] right before opening so the
 * runner doesn't end up with a stuck "walk forward" while the player reads the menu.
 */
@Composable
fun MenuOverlay(
    onExitGame: () -> Unit,
    releaseAllKeys: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(menuOpen) {
        if (menuOpen) releaseAllKeys()
    }

    // Back button: if the menu is open, close it. Otherwise open it. Same toggle the hamburger does.
    // Setting enabled=true unconditionally means we always intercept back - the alternative ("when
    // menu closed, let back fall through to default activity finish") would silently exit the game
    // without teardown. Always-route-through-menu keeps the exit path clean.
    BackHandler(enabled = true) {
        menuOpen = !menuOpen
    }

    Box(modifier.fillMaxSize()) {
        MenuButton(
            onClick = { menuOpen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
        // MenuSidebar is a BoxScope extension so it can align the panel to the right edge.
        MenuSidebar(
            open = menuOpen,
            onDismiss = { menuOpen = false },
            onExitGame = onExitGame
        )
    }
}

/**
 * Hamburger button that opens the menu sidebar. We use Compose's regular [clickable] (not a raw
 * pointer-input gesture) because it gives us ripple + accessibility for free, and we want a proper
 * tap (down + up without movement) rather than press-and-hold semantics.
 */
@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "☰",  // ☰
            color = Color.White,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/**
 * Slide-in menu panel anchored to the right edge, with a tap-to-dismiss scrim covering the rest of
 * the screen. Built as a plain Box stack rather than [ModalNavigationDrawer] because the M3 drawer
 * defaults to the start (left) edge, and forcing it to the right side requires either a
 * LayoutDirection hack or fighting its built-in gestures. The custom version is ~30 lines and gives
 * us exactly the behavior we want.
 *
 * Touch handling:
 *   - The scrim is a full-screen clickable Box; tapping it dismisses.
 *   - The panel itself uses a clickable-with-no-indication so taps land in the panel without
 *     bubbling up to the scrim's clickable (which would dismiss).
 */
@Composable
private fun BoxScope.MenuSidebar(
    open: Boolean,
    onDismiss: () -> Unit,
    onExitGame: () -> Unit
) {
    var value by remember { mutableIntStateOf(0) }

    // Scrim - separate AnimatedVisibility so it can fade independently of the panel slide.
    // matchParentSize so the fade-in container covers the full screen while it's animating.
    AnimatedVisibility(
        visible = open,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.matchParentSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                // Raw pointerInput instead of clickable {} so we get the dismiss-on-tap behavior
                // without the Material ripple animation. A ripple originating from a finger and
                // spreading across the entire screen looks distracting and out-of-place on a scrim
                // that's purely meant to be "an empty area you can tap to close the menu."
                .pointerInput(onDismiss) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        // Fire on down rather than up - matches how a scrim "feels" (instant dismiss)
                        // and avoids the awkward case where the user holds and slides off.
                        onDismiss()
                    }
                }
        )
    }

    // Panel - slides in from the right edge. align(CenterEnd) pins the AnimatedVisibility container
    // itself to the right side of the parent Box; slideInHorizontally { it } then animates the
    // content from "fully offset to the right of the container" (initial) to centered (final).
    AnimatedVisibility(
        visible = open,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it }),
        modifier = Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(Color(0xFF1E1E1E))
                // Eat all pointer events so they don't fall through to the scrim underneath. Using a
                // raw pointerInput rather than clickable {} because clickable adds ripple visuals
                // we don't want on the panel background.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                    }
                }
        ) {
            // Live title: ButterscotchNative.currentTitle is backed by mutableStateOf and updated
            // by a native -> Kotlin callback whenever GameMaker's window_set_caption fires. Reading
            // it here registers this Composable as an observer; any title change recomposes us
            // automatically, even if the menu is currently open.
            val title = ButterscotchNative.currentTitle ?: "Butterscotch"
            Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                Text(
                    text = title,
                    color = Color.White,
                    style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                MenuItem(label = "Exit", onClick = {
                    onDismiss()
                    onExitGame()
                })

                MenuItem(label = "Click Me! ($value)") {
                    value++
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = TextStyle(fontSize = 18.sp)
        )
    }
}

// ===[ Joystick ]===

/**
 * 8-way directional joystick. Mirrors the WebKT reference: map the finger position relative to the
 * joystick centre into an angle, snap that angle to one of 8 sectors (45 degrees each), and emit the
 * corresponding GameMaker arrow-key combo. A circular deadzone in the middle suppresses jitter.
 *
 * Visually, the thumb glyph follows the finger (clamped to the base radius) for tactile feedback.
 */
@Composable
private fun Joystick(keys: VirtualKeyState, modifier: Modifier = Modifier) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radiusPx = min(size.width, size.height) / 2f
                    val deadzonePx = radiusPx * 0.30f

                    var currentKeys = emptySet<Int>()

                    fun update(position: Offset) {
                        val delta = position - center
                        val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                        // Visual thumb position: clamp to base radius so it stays inside the circle.
                        thumbOffset = if (dist > radiusPx) delta * (radiusPx / dist) else delta

                        val newKeys = if (dist < deadzonePx) {
                            emptySet()
                        } else {
                            // Asymmetric 8-way sectors: cardinals are 60 degrees wide, diagonals are
                            // only 30 degrees wide. The WebKT reference uses equal 45/45 sectors,
                            // which makes "pure down" so narrow (only +-22.5 degrees from straight
                            // down) that natural thumb-pivot offset usually lands you in a diagonal.
                            // That breaks menus like Undertale's name entry, which dispatch each
                            // arrow as a discrete keyboard_check_pressed event - diagonal combos
                            // get mis-handled. Widening cardinals to 60 degrees gives much more
                            // forgiving "I'm pushing this way" detection while still leaving room
                            // for intentional diagonals (needed in DELTARUNE bullet boards, etc.).
                            //
                            // Angle is normalized to [0, 360) where 0=right, 90=down, 180=left, 270=up.
                            val angleDeg = (Math.toDegrees(atan2(delta.y, delta.x).toDouble()) + 360.0) % 360.0
                            val cardinalHalfWidth = 30.0  // cardinal zone = +-30 around its axis
                            newKeysForAngle(angleDeg, cardinalHalfWidth)
                        }
                        keys.transition(currentKeys, newKeys)
                        currentKeys = newKeys
                    }

                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        if (change.positionChanged()) {
                            change.consume()
                            update(change.position)
                        }
                    }
                    // Pointer released or cancelled - drop all keys this joystick was holding.
                    keys.transition(currentKeys, emptySet())
                    thumbOffset = Offset.Zero
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            // Outer ring
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = radius * 0.95f,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
            )
            // Thumb
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = radius * 0.40f,
                center = center + thumbOffset
            )
        }
    }
}

// Tiny helper to keep the gesture loop readable - Compose Change doesn't expose this directly.
private fun androidx.compose.ui.input.pointer.PointerInputChange.positionChanged(): Boolean =
    position != previousPosition

/**
 * Map a normalized polar angle (0..360, 0=right, 90=down) to the arrow-key set for that direction,
 * using asymmetric sectors: cardinals get `2 * cardinalHalfWidth` degrees, diagonals get the rest.
 *
 * With cardinalHalfWidth=30, each cardinal covers 60 degrees and each diagonal covers 30 degrees -
 * so as long as the finger is within 30 degrees of "straight down", we emit pure DOWN with no
 * spurious LEFT/RIGHT companion press.
 */
private fun newKeysForAngle(angleDeg: Double, cardinalHalfWidth: Double): Set<Int> = when {
    angleDeg < cardinalHalfWidth || angleDeg >= 360.0 - cardinalHalfWidth -> setOf(KEY_RIGHT)
    angleDeg < 90.0 - cardinalHalfWidth                                    -> setOf(KEY_RIGHT, KEY_DOWN)
    angleDeg < 90.0 + cardinalHalfWidth                                    -> setOf(KEY_DOWN)
    angleDeg < 180.0 - cardinalHalfWidth                                   -> setOf(KEY_DOWN, KEY_LEFT)
    angleDeg < 180.0 + cardinalHalfWidth                                   -> setOf(KEY_LEFT)
    angleDeg < 270.0 - cardinalHalfWidth                                   -> setOf(KEY_LEFT, KEY_UP)
    angleDeg < 270.0 + cardinalHalfWidth                                   -> setOf(KEY_UP)
    else                                                                    -> setOf(KEY_UP, KEY_RIGHT)
}

// ===[ Action Buttons ]===

@Composable
private fun ActionButtons(
    keys: VirtualKeyState,
    buttonSize: Dp,
    gap: Dp,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    // Layout direction matters for the visual reading of "primary action goes last": in a Row the
    // primary key (Z) sits rightmost; in a Column we want it bottommost so the thumb's resting
    // position naturally falls on the most-used button. Same key order in source, different visual
    // result based on `vertical`.
    if (vertical) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            ActionButton("C", KEY_C, keys, buttonSize)
            ActionButton("X", KEY_X, keys, buttonSize)
            ActionButton("Z", KEY_Z, keys, buttonSize)
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            ActionButton("C", KEY_C, keys, buttonSize)
            ActionButton("X", KEY_X, keys, buttonSize)
            ActionButton("Z", KEY_Z, keys, buttonSize)  // Rightmost = primary action (Z confirms in Undertale/DR)
        }
    }
}

@Composable
private fun ActionButton(label: String, keyCode: Int, keys: VirtualKeyState, size: Dp) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                if (pressed) Color.White.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.22f)
            )
            .pointerInput(keyCode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    keys.acquire(keyCode)
                    try {
                        // Hold the press as long as this specific pointer stays down. We don't care
                        // about position once it's grabbed - sliding off the button still keeps the
                        // key held (matches console gamepad UX better than "release on slide-off").
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                        }
                    } finally {
                        pressed = false
                        keys.release(keyCode)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
        )
    }
}
