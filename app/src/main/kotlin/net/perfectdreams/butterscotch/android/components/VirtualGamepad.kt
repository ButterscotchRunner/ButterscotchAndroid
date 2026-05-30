package net.perfectdreams.butterscotch.android.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.perfectdreams.butterscotch.android.ButterscotchDroidRunner
import net.perfectdreams.butterscotch.android.ButterscotchNative
import net.perfectdreams.butterscotch.android.VirtualKeyState
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt
import net.perfectdreams.butterscotch.android.layouts.Gamepad
import net.perfectdreams.butterscotch.android.layouts.GamepadElement
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.GamepadStick
import net.perfectdreams.butterscotch.android.layouts.GmlKey
import net.perfectdreams.butterscotch.android.layouts.InputBinding
import net.perfectdreams.butterscotch.android.layouts.KeyTrigger
import kotlin.collections.iterator

/**
 * Just the on-screen gameplay controls (joystick + action buttons), no menu. Renders into whatever
 * area its [modifier] gives it - the parent decides whether that's the whole screen (Overlay layout)
 * or a dedicated controls strip below the game (Stacked layout).
 *
 * The caller owns the [VirtualKeyState] so it can release all held keys on lifecycle events (e.g.
 * the menu opening, or an orientation change recomposing the layout).
 *
 * [layout] is owned by the caller (GameActivity) so edits survive an Overlay/Stacked reflow. When
 * [editMode] is on, controls become draggable and long-pressable instead of playable, and edits are
 * pushed back through [onLayoutChange]. We do not persist layouts yet - this is purely in-memory.
 */
@Composable
fun GameControls(
    layout: GamepadLayout,
    editMode: Boolean,
    onLayoutChange: (GamepadLayout) -> Unit,
    onExitEditMode: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    onSaveAs: (String) -> Unit,
    onMenuOpen: () -> (Unit),
    keys: VirtualKeyState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier) {
        if (editMode) {
            GamepadEditor(
                layout = layout,
                onLayoutChange = onLayoutChange,
                onExitEditMode = onExitEditMode,
                canSave = canSave,
                onSave = onSave,
                onSaveAs = onSaveAs
            )
        } else {
            PlayableGamepad(layout = layout, keys = keys) {
                onMenuOpen.invoke()
            }
        }
    }
}

// Resolve an element's position/scale into the offset+size modifier that places it in the overlay.
// Shared by both the play and edit renderers so the two never drift on where a control lands. Scale
// is relative to the overlay's shorter side so controls keep their proportions across orientations.
fun BoxWithConstraintsScope.placementOf(element: GamepadElement): Modifier {
    val ref = if (maxWidth < maxHeight) maxWidth else maxHeight
    val sizeDp = ref * element.scale.toFloat()
    val centerX = maxWidth * element.positionX.toFloat()
    val centerY = maxHeight * element.positionY.toFloat()
    return Modifier
        .offset(x = centerX - sizeDp / 2f, y = centerY - sizeDp / 2f)
        .size(sizeDp)
}

// Fallback label for a Key whose label is null. Keyboard letters/digits show their glyph; arrows
// show an arrow symbol; anything else falls back to its raw code. Gamepad buttons have no natural
// glyph, so they show a placeholder until we have real button artwork.
fun defaultLabelFor(binding: InputBinding): String = when (binding) {
    is InputBinding.Keyboard -> when (binding.vk) {
        in 48..57, in 65..90 -> binding.vk.toChar().toString() // 0-9, A-Z (ASCII)
        GmlKey.LEFT.code -> "←"
        GmlKey.UP.code -> "↑"
        GmlKey.RIGHT.code -> "→"
        GmlKey.DOWN.code -> "↓"
        GmlKey.SPACE.code -> "␣"
        else -> binding.vk.toString()
    }
    is InputBinding.GamepadButton -> Gamepad.Button.fromIndex(binding.button)?.shortLabel ?: "B${binding.button}"
}

// The playable controls: each element renders as its interactive composable, dispatching input
// through [keys]. No edit affordances here at all.
@Composable
private fun BoxWithConstraintsScope.PlayableGamepad(layout: GamepadLayout, keys: VirtualKeyState, onMenuOpen: () -> (Unit)) {
    layout.element.forEach { element ->
        val placement = placementOf(element).alpha(element.opacity.toFloat())
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
                trigger = element.trigger,
                keys = keys,
                modifier = placement
            )
            is GamepadElement.AnalogJoystick -> AnalogJoystick(
                stick = element.stick,
                device = element.device,
                keys = keys,
                modifier = placement
            )

            is GamepadElement.Menu -> {
                Box(
                    modifier = placement
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                down.consume()
                                onMenuOpen.invoke()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open menu",
                        tint = Color.White,
                        // The Box is sized by placement (scale * shorter side), so size the glyph as a fraction of it
                        modifier = Modifier.fillMaxSize(0.55f)
                    )
                }
            }
        }
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
    runner: ButterscotchDroidRunner,
    menuOpen: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onExitGame: () -> Unit,
    onEditLayout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Back button: if the menu is open, close it. Otherwise open it. Same toggle the hamburger does.
    // Setting enabled=true unconditionally means we always intercept back - the alternative ("when
    // menu closed, let back fall through to default activity finish") would silently exit the game
    // without teardown. Always-route-through-menu keeps the exit path clean.
    BackHandler(enabled = true) {
        onMenuToggle.invoke(!menuOpen)
    }

    Box(modifier.fillMaxSize()) {
        // MenuSidebar is a BoxScope extension so it can align the panel to the right edge.
        MenuSidebar(
            open = menuOpen,
            onDismiss = { onMenuToggle.invoke(false) },
            onExitGame = onExitGame,
            onEditLayout = onEditLayout
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
    onExitGame: () -> Unit,
    onEditLayout: () -> Unit
) {
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
                MenuItem(label = "Edit Layout", onClick = {
                    onDismiss()
                    onEditLayout()
                })

                MenuItem(label = "Exit", onClick = {
                    onDismiss()
                    onExitGame()
                })
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
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
private fun JoystickBase(
    modifier: Modifier = Modifier,
    modifierWithOffset: Modifier.(thumbOffset: MutableState<Offset>) -> Modifier
) {
    val thumbOffset = remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .modifierWithOffset(thumbOffset)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            // Outer ring
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = radius * 0.95f,
                center = center,
                style = Stroke(width = 4f)
            )
            // Thumb
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = radius * 0.40f,
                center = center + thumbOffset.value
            )
        }
    }
}

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
    JoystickBase(modifier) { thumbOffset ->
        this.pointerInput(up, down, left, right) {
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
                    thumbOffset.value = if (dist > radiusPx) delta * (radiusPx / dist) else delta

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
                        val angleDeg =
                            (Math.toDegrees(atan2(delta.y, delta.x).toDouble()) + 360.0) % 360.0
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
                thumbOffset.value = Offset.Zero
            }
        }
    }
}

// ===[ Analog Joystick ]===

/**
 * On-screen *analog* stick: unlike the digital [Joystick] (which snaps the finger angle to 4 arrow
 * keys), this reports the thumb's continuous position as two raw axis values in [-1, 1] straight to
 * the runner's gamepad feed (which deadzones on read). Pushing up is negative V, left is negative H,
 * matching GameMaker's gp_axis* convention and the physical-controller mapping.
 *
 * The thumb (and the reported magnitude) is clamped to the base radius so neither axis exceeds 1.0.
 * On release it re-centers to (0, 0). [VirtualKeyState.setAxis] also re-centers it if this composable
 * is torn down mid-drag (e.g. an Overlay/Stacked reflow).
 */
@Composable
private fun AnalogJoystick(
    stick: GamepadStick,
    device: Int,
    keys: VirtualKeyState,
    modifier: Modifier = Modifier
) {
    JoystickBase(modifier) { thumbOffset ->
        this.pointerInput(stick) {
            awaitEachGesture {
                val downPointer = awaitFirstDown(requireUnconsumed = false)
                downPointer.consume()
                val center = Offset(size.width / 2f, size.height / 2f)
                val radiusPx = min(size.width, size.height) / 2f

                fun update(position: Offset) {
                    val delta = position - center
                    val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                    // Clamp to the base radius so the normalized value never exceeds 1.0.
                    val clamped = if (dist > radiusPx) delta * (radiusPx / dist) else delta
                    thumbOffset.value = clamped
                    val x = if (radiusPx > 0f) (clamped.x / radiusPx).coerceIn(-1f, 1f) else 0f
                    val y = if (radiusPx > 0f) (clamped.y / radiusPx).coerceIn(-1f, 1f) else 0f
                    keys.setAxis(device, stick.horizontalAxisIndex, x)
                    keys.setAxis(device, stick.verticalAxisIndex, y)
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
                // Pointer released or cancelled - re-center the stick.
                keys.setAxis(device, stick.horizontalAxisIndex, 0f)
                keys.setAxis(device, stick.verticalAxisIndex, 0f)
                thumbOffset.value = Offset.Zero
            }
        }
    }
}

// Tiny helper to keep the gesture loop readable - Compose Change doesn't expose this directly.
private fun PointerInputChange.positionChanged(): Boolean =
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
    trigger: KeyTrigger,
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
