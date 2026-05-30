package net.perfectdreams.butterscotch.android.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.perfectdreams.butterscotch.android.VirtualKeyState
import net.perfectdreams.butterscotch.android.layouts.Gamepad
import net.perfectdreams.butterscotch.android.layouts.GamepadElement
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.GamepadStick
import net.perfectdreams.butterscotch.android.layouts.GmlKey
import net.perfectdreams.butterscotch.android.layouts.InputBinding
import net.perfectdreams.butterscotch.android.layouts.KeyTrigger
import java.util.UUID

// The editor: every element becomes a draggable / long-pressable stand-in, plus a toolbar (add /
// save / save as) and the per-element + "save as" dialogs. Elements are addressed by their stable
// [GamepadElement.id], never by list index, so adding or deleting one can never make an in-flight
// drag or open dialog point at the wrong element.
@Composable
fun BoxWithConstraintsScope.GamepadEditor(
    layout: GamepadLayout,
    keys: VirtualKeyState,
    onLayoutChange: (GamepadLayout) -> Unit,
    onExitEditMode: () -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
    onSaveAs: (String) -> Unit
) {
    // Id of the element whose editor dialog is open, or null.
    var editingId by remember { mutableStateOf<UUID?>(null) }
    var showSaveAsDialog by remember { mutableStateOf(false) }

    // Container size in pixels, used to convert drag deltas (px) into 0..1 position fractions.
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()

    // A pointerInput block captures its surroundings once (it is not re-keyed on every layout edit),
    // so reads inside the drag handler must go through these always-latest snapshots rather than the
    // captured-at-launch parameters, or mid-drag reads would be stale.
    val currentLayout by rememberUpdatedState(layout)
    val currentOnChange by rememberUpdatedState(onLayoutChange)

    // Replace the element sharing [element]'s id with the new value, leaving the list order intact.
    fun update(element: GamepadElement) {
        val l = currentLayout
        currentOnChange(l.copy(element = l.element.map { if (it.id == element.id) element else it }))
    }

    fun delete(id: UUID) {
        val l = currentLayout
        currentOnChange(l.copy(element = l.element.filter { it.id != id }))
        editingId = null
    }

    // Append a freshly-built element at the overlay center and open its editor straight away.
    fun add(element: GamepadElement) {
        val l = currentLayout
        currentOnChange(l.copy(element = l.element + element))
        editingId = element.id
    }

    layout.element.forEach { element ->
        // Two gesture detectors on the same element: drag moves it (immediately), a long press with no movement opens its editor.
        // Movement past touch slop cancels the long press, so the two do not fight.
        val editModifier = placementOf(element)
            .pointerInput(element.id, widthPx, heightPx) {
                // Accumulate position locally (synchronous, immune to recomposition timing)
                // seeded from the latest committed position at drag start, pushing each step
                // to the model.
                var px = 0.0
                var py = 0.0
                detectDragGestures(
                    onDragStart = {
                        currentLayout.element.firstOrNull { it.id == element.id }?.let {
                            px = it.positionX
                            py = it.positionY
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val el = currentLayout.element.firstOrNull { it.id == element.id }
                        if (el != null) {
                            px = (px + dragAmount.x / widthPx).coerceIn(0.0, 1.0)
                            py = (py + dragAmount.y / heightPx).coerceIn(0.0, 1.0)
                            update(el.movedTo(px, py))
                        }
                    }
                )
            }
            .pointerInput(element.id) {
                detectTapGestures(onLongPress = { editingId = element.id })
            }

        when (element) {
            is GamepadElement.Joystick -> Joystick(
                up = element.up,
                down = element.down,
                left = element.left,
                right = element.right,
                keys = keys,
                interactive = false,
                modifier = editModifier
            )

            is GamepadElement.AnalogJoystick -> AnalogJoystick(
                stick = element.stick,
                device = element.device,
                keys = keys,
                interactive = false,
                modifier = editModifier
            )

            is GamepadElement.Key -> {
                ActionButton(
                    defaultLabelFor(element.binding),
                    element.binding,
                    element.trigger,
                    keys,
                    false,
                    editModifier
                )
            }
            is GamepadElement.Menu -> {
                MenuButton(false, {}, editModifier)
            }
        }
    }

    Column(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Edit mode - drag to move, long-press to edit",
            color = Color.White,
            style = TextStyle(fontSize = 13.sp)
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 16.dp)) {
            Box {
                var addMenuExpanded by remember { mutableStateOf(false) }
                Button(onClick = { addMenuExpanded = true }) { Text("Add") }
                DropdownMenu(
                    expanded = addMenuExpanded,
                    onDismissRequest = { addMenuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Button") }, onClick = {
                        addMenuExpanded = false
                        add(GamepadElement.Key(
                            positionX = 0.5,
                            positionY = 0.5,
                            scale = 0.22,
                            opacity = 1.0,
                            label = null,
                            trigger = KeyTrigger.Press,
                            binding = InputBinding.Keyboard(GmlKey.Z.code),
                            id = UUID.randomUUID()
                        ))
                    })
                    DropdownMenuItem(text = { Text("Joystick") }, onClick = {
                        addMenuExpanded = false
                        add(GamepadElement.Joystick(
                            positionX = 0.5,
                            positionY = 0.5,
                            scale = 0.42,
                            opacity = 1.0,
                            up = InputBinding.Keyboard(GmlKey.UP.code),
                            down = InputBinding.Keyboard(GmlKey.DOWN.code),
                            left = InputBinding.Keyboard(GmlKey.LEFT.code),
                            right = InputBinding.Keyboard(GmlKey.RIGHT.code),
                            id = UUID.randomUUID()
                        ))
                    })
                    DropdownMenuItem(text = { Text("Gamepad Button") }, onClick = {
                        addMenuExpanded = false
                        add(GamepadElement.Key(
                            positionX = 0.5,
                            positionY = 0.5,
                            scale = 0.22,
                            opacity = 1.0,
                            label = null,
                            trigger = KeyTrigger.Press,
                            binding = InputBinding.GamepadButton(device = 0, button = Gamepad.Button.FACE1.index),
                            id = UUID.randomUUID()
                        ))
                    })
                    DropdownMenuItem(text = { Text("Gamepad Joystick") }, onClick = {
                        addMenuExpanded = false
                        add(GamepadElement.AnalogJoystick(
                            positionX = 0.5,
                            positionY = 0.5,
                            scale = 0.42,
                            opacity = 1.0,
                            stick = GamepadStick.LEFT,
                            device = 0,
                            id = UUID.randomUUID()
                        ))
                    })
                    DropdownMenuItem(text = { Text("Menu") }, onClick = {
                        addMenuExpanded = false
                        add(GamepadElement.Menu(
                            positionX = 0.5,
                            positionY = 0.5,
                            scale = 0.22,
                            opacity = 1.0,
                            id = UUID.randomUUID()
                        ))
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                onSave()
                onExitEditMode()
            }, enabled = canSave) { Text("Save") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { showSaveAsDialog = true }) { Text("Save As") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { onExitEditMode() }) { Text("Exit") }
        }
    }

    // key(editing.id) so the dialog's internal field state resets when a different element is picked.
    val editing = editingId?.let { id -> layout.element.firstOrNull { it.id == id } }
    if (editing != null) {
        key(editing.id) {
            ElementEditDialog(
                element = editing,
                onChange = { update(it) },
                onDelete = { delete(editing.id) },
                onDismiss = { editingId = null }
            )
        }
    }

    if (showSaveAsDialog) {
        SaveAsLayoutDialog(
            initialName = layout.fancyName,
            onConfirm = { name ->
                showSaveAsDialog = false
                onSaveAs(name)
                onExitEditMode()
            },
            onDismiss = { showSaveAsDialog = false }
        )
    }
}

// Editor dialog for one element: size, opacity, the bound key(s), and delete. Edits are applied live
// through [onChange] so the change is visible behind the dialog.
@Composable
private fun ElementEditDialog(
    element: GamepadElement,
    onChange: (GamepadElement) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFE53935)) } },
        title = {
            Text(
                when (element) {
                    is GamepadElement.Key -> "Edit button"
                    is GamepadElement.Joystick -> "Edit joystick"
                    is GamepadElement.AnalogJoystick -> "Edit analog stick"
                    is GamepadElement.Menu -> "Edit menu"
                }
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Size: ${percent(element.scale)}")
                Slider(
                    value = element.scale.toFloat(),
                    onValueChange = { onChange(element.withScale(it.toDouble())) },
                    valueRange = 0.05f..1f
                )
                Text("Opacity: ${percent(element.opacity)}")
                Slider(
                    value = element.opacity.toFloat(),
                    onValueChange = { onChange(element.withOpacity(it.toDouble())) },
                    valueRange = 0f..1f
                )
                Spacer(Modifier.height(8.dp))
                when (element) {
                    // The binding type is fixed at creation ("Button" -> keyboard, "Gamepad Button" ->
                    // gamepad); BindingEditor shows the matching picker for whichever it currently is.
                    is GamepadElement.Key -> BindingEditor("Button", element.binding) { onChange(element.copy(binding = it)) }
                    is GamepadElement.Joystick -> {
                        VkField("Up", element.up) { onChange(element.copy(up = it)) }
                        VkField("Down", element.down) { onChange(element.copy(down = it)) }
                        VkField("Left", element.left) { onChange(element.copy(left = it)) }
                        VkField("Right", element.right) { onChange(element.copy(right = it)) }
                    }
                    is GamepadElement.AnalogJoystick -> {
                        SlotField(element.device) { onChange(element.copy(device = it)) }
                        TextButton(onClick = { onChange(element.copy(stick = GamepadStick.LEFT)) }) {
                            Text("Left stick" + if (element.stick == GamepadStick.LEFT) " ✓" else "")
                        }
                        TextButton(onClick = { onChange(element.copy(stick = GamepadStick.RIGHT)) }) {
                            Text("Right stick" + if (element.stick == GamepadStick.RIGHT) " ✓" else "")
                        }
                    }

                    is GamepadElement.Menu -> {}
                }
            }
        }
    )
}

// Name prompt for "Save As". The entered name becomes the layout's fancyName, which the layout
// manager will surface later. Confirm is disabled on a blank name.
@Composable
private fun SaveAsLayoutDialog(initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Save layout as") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true
            )
        }
    )
}

// Key picker for the binding behind a control: a Material exposed dropdown (outlined field with a
// floating label + trailing chevron) listing the keys the runner understands, so a user chooses by
// name instead of typing a raw vk code. Selecting one always produces a keyboard binding. If the
// current code is not in the list (e.g. a gamepad button) we show the raw code.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VkField(label: String, binding: InputBinding, onChange: (InputBinding) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = GmlKey.fromCode(binding.code())
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = current?.label ?: binding.code().toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GmlKey.entries.forEach { gmlKey ->
                DropdownMenuItem(
                    text = { Text(gmlKey.label) },
                    onClick = {
                        onChange(InputBinding.Keyboard(gmlKey.code))
                        expanded = false
                    }
                )
            }
        }
    }
}

private const val VIRTUAL_SLOT_COUNT = 16

// Picker for a Key's binding. The binding's concrete type is fixed when the element is added, so we
// just render the matching editor: a keyboard key picker, or a controller slot + gamepad button pair.
@Composable
private fun BindingEditor(label: String, binding: InputBinding, onChange: (InputBinding) -> Unit) {
    when (binding) {
        is InputBinding.Keyboard -> VkField(label, binding, onChange)
        is InputBinding.GamepadButton -> {
            SlotField(binding.device) { onChange(binding.copy(device = it)) }
            GamepadButtonField(binding.button) { onChange(binding.copy(button = it)) }
        }
    }
}

// Picks which controller slot (player) a gamepad element feeds.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotField(device: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    fun labelFor(slot: Int) = "Player ${slot + 1} (slot $slot)"
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = labelFor(device),
            onValueChange = {},
            readOnly = true,
            label = { Text("Controller slot") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (slot in 0 until VIRTUAL_SLOT_COUNT) {
                DropdownMenuItem(
                    text = { Text(labelFor(slot)) },
                    onClick = {
                        onChange(slot)
                        expanded = false
                    }
                )
            }
        }
    }
}

// Picks which gamepad button a "Gamepad Button" element sends. Stores the canonical button index.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GamepadButtonField(button: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = Gamepad.Button.fromIndex(button)
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = current?.label ?: button.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Button") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Gamepad.Button.entries.forEach { gpButton ->
                DropdownMenuItem(
                    text = { Text(gpButton.label) },
                    onClick = {
                        onChange(gpButton.index)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun percent(value: Double): String = "${(value * 100).toInt()}%"

// Position/size/opacity copies. GamepadElement is sealed with no copy on the base, so each variant
// has to be copied explicitly.
private fun GamepadElement.movedTo(px: Double, py: Double): GamepadElement = when (this) {
    is GamepadElement.Key -> copy(positionX = px, positionY = py)
    is GamepadElement.Joystick -> copy(positionX = px, positionY = py)
    is GamepadElement.AnalogJoystick -> copy(positionX = px, positionY = py)
    is GamepadElement.Menu -> copy(positionX = px, positionY = py)
}
private fun GamepadElement.withScale(s: Double): GamepadElement = when (this) {
    is GamepadElement.Key -> copy(scale = s)
    is GamepadElement.Joystick -> copy(scale = s)
    is GamepadElement.AnalogJoystick -> copy(scale = s)
    is GamepadElement.Menu -> copy(scale = s)
}
private fun GamepadElement.withOpacity(o: Double): GamepadElement = when (this) {
    is GamepadElement.Key -> copy(opacity = o)
    is GamepadElement.Joystick -> copy(opacity = o)
    is GamepadElement.AnalogJoystick -> copy(opacity = o)
    is GamepadElement.Menu -> copy(opacity = o)
}

// Read the integer code behind a binding (keyboard vk or gamepad button number), used to find the
// matching entry for the key dropdown.
private fun InputBinding.code(): Int = when (this) {
    is InputBinding.Keyboard -> vk
    is InputBinding.GamepadButton -> button
}