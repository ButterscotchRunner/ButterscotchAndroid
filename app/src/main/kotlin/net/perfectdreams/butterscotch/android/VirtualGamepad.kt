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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
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
import java.util.UUID
import net.perfectdreams.butterscotch.android.layouts.GamepadElement
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.InputBinding
import net.perfectdreams.butterscotch.android.layouts.KeyTrigger

// GameMaker vk_* constants. Match the keycodes the runner already understands from a USB keyboard,
// so the C side doesn't need a touch-specific path - virtual joystick presses become regular key events.
private const val KEY_LEFT  = 37
private const val KEY_UP    = 38
private const val KEY_RIGHT = 39
private const val KEY_DOWN  = 40
private const val KEY_C     = 67
private const val KEY_X     = 88
private const val KEY_Z     = 90

// Tracks which input bindings are currently held and forwards only edge transitions to JNI.
// Without this, a finger dragging across the joystick would re-fire "key down" on every pointer
// move event, which would spuriously re-trigger GameMaker's keyboard_check_pressed flag.
//
// Refcounting handles the case where two pointers happen to map to the same binding at once (e.g.
// two fingers both landing on the joystick area mapped to "up"): the binding stays down until *all*
// pointers release it. Matches what the WebKT reference frontend does with pressedRefs.
//
// Bindings are the digital InputBinding model (keyboard key or gamepad button). The data classes
// give us correct equals/hashCode, so they work directly as refcount/set keys.
class VirtualKeyState(val runner: ButterscotchDroidRunner) {
    private val refs = HashMap<InputBinding, Int>()

    // We do NOT need to use @Synchronized here because there is only one UI Thread, and we only dispatch it to a channel

    fun acquire(binding: InputBinding) {
        val newCount = (refs[binding] ?: 0) + 1
        refs[binding] = newCount
        if (newCount == 1) {
            dispatch(binding, isDown = true)
        }
    }

    fun release(binding: InputBinding) {
        val newCount = (refs[binding] ?: return) - 1
        if (newCount <= 0) {
            refs.remove(binding)
            dispatch(binding, isDown = false)
        } else {
            refs[binding] = newCount
        }
    }

    // Apply a new "currently-pressed" set for a single pointer, emitting only the delta.
    fun transition(oldKeys: Set<InputBinding>, newKeys: Set<InputBinding>) {
        if (oldKeys == newKeys) return
        for (k in oldKeys) if (k !in newKeys) release(k)
        for (k in newKeys) if (k !in oldKeys) acquire(k)
    }

    fun releaseAll() {
        for ((binding, _) in refs)
            dispatch(binding, isDown = false)
        refs.clear()
    }

    // Forward a binding edge to the runner. Keyboard goes through the existing key path. Gamepad
    // buttons have no host->runner transport yet (that needs a gamepad JNI bridge), so they are
    // dropped for now - the current default layout only uses keyboard bindings.
    private fun dispatch(binding: InputBinding, isDown: Boolean) {
        when (binding) {
            is InputBinding.Keyboard -> runner.onKey(binding.vk, isDown)
            is InputBinding.GamepadButton -> {} // TODO: wire once the gamepad axis/button JNI bridge exists
        }
    }
}

/**
 * Just the on-screen gameplay controls (joystick + action buttons), no menu. Renders into whatever
 * area its [modifier] gives it - the parent decides whether that's the whole screen (Overlay layout)
 * or a dedicated controls strip below the game (Stacked layout).
 *
 * The caller owns the [VirtualKeyState] so it can release all held keys on lifecycle events (e.g.
 * the menu opening, or an orientation change recomposing the layout).
 *
 * We do not load or edit layouts yet, so this renders the built-in [defaultGamepadLayout], which
 * reproduces the old hardcoded joystick + C/X/Z gamepad expressed through the layout model.
 */
@Composable
fun GameControls(keys: VirtualKeyState, modifier: Modifier = Modifier) {
    GamepadLayoutView(defaultGamepadLayout, keys, modifier)
}

// Renders a GamepadLayout into the given area. Element geometry follows the model contract:
// position is a 0..1 fraction of the overlay width (X) / height (Y) measured from the element
// center, and scale is a fraction of min(width, height) applied to both dimensions (so circular
// controls stay circular on any overlay aspect). opacity is purely visual - alpha() does not gate
// pointer input, so an opacity-0 element is still tappable (useful later for skin hit zones).
@Composable
private fun GamepadLayoutView(layout: GamepadLayout, keys: VirtualKeyState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val ref = if (maxWidth < maxHeight) maxWidth else maxHeight
        layout.element.forEach { element ->
            val sizeDp = ref * element.scale.toFloat()
            val centerX = maxWidth * element.positionX.toFloat()
            val centerY = maxHeight * element.positionY.toFloat()
            val placement = Modifier
                .offset(x = centerX - sizeDp / 2f, y = centerY - sizeDp / 2f)
                .size(sizeDp)
                .alpha(element.opacity.toFloat())

            when (element) {
                is GamepadElement.Joystick -> Joystick(
                    up = element.up,
                    down = element.down,
                    left = element.left,
                    right = element.right,
                    keys = keys,
                    modifier = placement
                )
                is GamepadElement.Key -> ActionButton(
                    label = element.label ?: defaultLabelFor(element.binding),
                    binding = element.binding,
                    type = element.type,
                    keys = keys,
                    modifier = placement
                )
                // Analog sticks need a continuous gamepad-axis transport that does not exist yet, so
                // they are not rendered. The default layout contains none.
                is GamepadElement.AnalogJoystick -> {}
            }
        }
    }
}

// Stable id for the built-in layout. We do not persist or load layouts yet; this is the hardcoded
// equivalent of the old fixed gamepad. Positions approximate the original adaptive bottom-left
// joystick + bottom-right C/X/Z cluster; a fixed layout cannot reproduce the old min/max size
// clamps or the row-to-column reflow, which is an accepted trade for being data-driven.
private val defaultGamepadLayout = GamepadLayout(
    id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    orientation = GamepadLayout.GamepadTargetOrientation.LANDSCAPE,
    element = listOf(
        GamepadElement.Joystick(
            positionX = 0.16, positionY = 0.74, scale = 0.42, opacity = 1.0,
            up = InputBinding.Keyboard(KEY_UP),
            down = InputBinding.Keyboard(KEY_DOWN),
            left = InputBinding.Keyboard(KEY_LEFT),
            right = InputBinding.Keyboard(KEY_RIGHT),
        ),
        GamepadElement.Key(positionX = 0.66, positionY = 0.78, scale = 0.22, opacity = 1.0, label = null, type = KeyTrigger.Press, binding = InputBinding.Keyboard(KEY_C)),
        GamepadElement.Key(positionX = 0.79, positionY = 0.78, scale = 0.22, opacity = 1.0, label = null, type = KeyTrigger.Press, binding = InputBinding.Keyboard(KEY_X)),
        GamepadElement.Key(positionX = 0.92, positionY = 0.78, scale = 0.22, opacity = 1.0, label = null, type = KeyTrigger.Press, binding = InputBinding.Keyboard(KEY_Z)),
    )
)

// Fallback label for a Key whose label is null. Keyboard letters/digits show their glyph; arrows
// show an arrow symbol; anything else falls back to its raw code. Gamepad buttons have no natural
// glyph, so they show a placeholder until we have real button artwork.
private fun defaultLabelFor(binding: InputBinding): String = when (binding) {
    is InputBinding.Keyboard -> when (binding.vk) {
        in 48..57, in 65..90 -> binding.vk.toChar().toString() // 0-9, A-Z (ASCII)
        KEY_LEFT  -> "←"
        KEY_UP    -> "↑"
        KEY_RIGHT -> "→"
        KEY_DOWN  -> "↓"
        32        -> "␣"
        else      -> binding.vk.toString()
    }
    is InputBinding.GamepadButton -> "B${binding.button}"
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
private fun Joystick(
    up: InputBinding,
    down: InputBinding,
    left: InputBinding,
    right: InputBinding,
    keys: VirtualKeyState,
    modifier: Modifier = Modifier
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .pointerInput(up, down, left, right) {
                awaitEachGesture {
                    val downPointer = awaitFirstDown(requireUnconsumed = false)
                    downPointer.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radiusPx = min(size.width, size.height) / 2f
                    val deadzonePx = radiusPx * 0.30f

                    var currentKeys = emptySet<InputBinding>()

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
                            bindingsForAngle(angleDeg, cardinalHalfWidth, up, down, left, right)
                        }
                        keys.transition(currentKeys, newKeys)
                        currentKeys = newKeys
                    }

                    update(downPointer.position)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == downPointer.id } ?: break
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

// Map a normalized polar angle (0..360, 0=right, 90=down) to the set of direction bindings for that
// direction, using asymmetric sectors: cardinals get 2 * cardinalHalfWidth degrees, diagonals get
// the rest.
//
// With cardinalHalfWidth=30, each cardinal covers 60 degrees and each diagonal covers 30 degrees -
// so as long as the finger is within 30 degrees of "straight down", we emit pure DOWN with no
// spurious LEFT/RIGHT companion press.
private fun bindingsForAngle(
    angleDeg: Double,
    cardinalHalfWidth: Double,
    up: InputBinding,
    down: InputBinding,
    left: InputBinding,
    right: InputBinding
): Set<InputBinding> = when {
    angleDeg < cardinalHalfWidth || angleDeg >= 360.0 - cardinalHalfWidth -> setOf(right)
    angleDeg < 90.0 - cardinalHalfWidth                                    -> setOf(right, down)
    angleDeg < 90.0 + cardinalHalfWidth                                    -> setOf(down)
    angleDeg < 180.0 - cardinalHalfWidth                                   -> setOf(down, left)
    angleDeg < 180.0 + cardinalHalfWidth                                   -> setOf(left)
    angleDeg < 270.0 - cardinalHalfWidth                                   -> setOf(left, up)
    angleDeg < 270.0 + cardinalHalfWidth                                   -> setOf(up)
    else                                                                    -> setOf(up, right)
}

// ===[ Action Buttons ]===

// A single round action button. The renderer sizes/positions it via [modifier] (which already
// carries the resolved size, offset, and opacity from the layout), so this only owns its look and
// press gesture.
//
// [type] is currently always Press: hold the binding while the pointer is down. RapidFire is part
// of the model but not wired yet - it needs a per-frame tick to emit repeat pulses, which the
// edge-only input pipeline does not have.
@Composable
private fun ActionButton(
    label: String,
    binding: InputBinding,
    type: KeyTrigger,
    keys: VirtualKeyState,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (pressed) Color.White.copy(alpha = 0.45f)
                else Color.White.copy(alpha = 0.22f)
            )
            .pointerInput(binding) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    keys.acquire(binding)
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
                        keys.release(binding)
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
