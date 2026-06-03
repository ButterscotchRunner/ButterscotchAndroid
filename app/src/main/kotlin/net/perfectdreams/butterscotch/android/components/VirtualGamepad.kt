package net.perfectdreams.butterscotch.android.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.perfectdreams.butterscotch.android.ButterscotchDroidRunner
import net.perfectdreams.butterscotch.android.ButterscotchNative
import net.perfectdreams.butterscotch.android.VirtualKeyState
import java.util.UUID
import net.perfectdreams.butterscotch.android.layouts.Gamepad
import net.perfectdreams.butterscotch.android.layouts.GamepadElement
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.GmlKey
import net.perfectdreams.butterscotch.android.layouts.InputBinding

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
    activeFastForwardButtonId: UUID?,
    onLayoutChange: (GamepadLayout) -> Unit,
    onExitEditMode: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    onSaveAs: (String) -> Unit,
    onMenuOpen: () -> (Unit),
    onFastForwardPress: (GamepadElement.FastForward) -> (Unit),
    onFastForwardRelease: (GamepadElement.FastForward) -> (Unit),
    keys: VirtualKeyState,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.clipToBounds()) {
        if (editMode) {
            GamepadEditor(
                layout = layout,
                keys = keys,
                onLayoutChange = onLayoutChange,
                onExitEditMode = onExitEditMode,
                canSave = canSave,
                onSave = onSave,
                onSaveAs = onSaveAs
            )
        } else {
            PlayableGamepad(layout = layout, keys = keys, activeFastForwardButtonId, onFastForwardPress = onFastForwardPress, onFastForwardRelease = onFastForwardRelease) {
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
private fun BoxWithConstraintsScope.PlayableGamepad(
    layout: GamepadLayout,
    keys: VirtualKeyState,
    activeFastForwardButtonId: UUID?,
    onFastForwardPress: (GamepadElement.FastForward) -> (Unit),
    onFastForwardRelease: (GamepadElement.FastForward) -> (Unit),
    onMenuOpen: () -> (Unit)
) {
    layout.elements.forEach { element ->
        val placement = placementOf(element).alpha(element.opacity.toFloat())
        when (element) {
            is GamepadElement.Joystick -> Joystick(
                up = element.up,
                down = element.down,
                left = element.left,
                right = element.right,
                keys = keys,
                interactive = true,
                modifier = placement
            )

            is GamepadElement.AnalogJoystick -> AnalogJoystick(
                stick = element.stick,
                device = element.device,
                keys = keys,
                interactive = true,
                modifier = placement
            )

            is GamepadElement.Key -> ActionButton(
                label = element.label ?: defaultLabelFor(element.binding),
                binding = element.binding,
                trigger = element.trigger,
                keys = keys,
                interactive = true,
                modifier = placement
            )

            is GamepadElement.Menu -> {
                MenuButton(
                    interactive = true,
                    {
                        onMenuOpen.invoke()
                    },
                    placement
                )
            }

            is GamepadElement.FastForward -> {
                FastForwardButton(
                    activeFastForwardButtonId == element.id,
                    true,
                    element,
                    onFastForwardPress,
                    onFastForwardRelease,
                    placement
                )
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
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
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