package net.perfectdreams.butterscotch.android.layouts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.perfectdreams.butterscotch.UUIDAsStringSerializer
import java.util.UUID

@Serializable
sealed class GamepadElement {
    /**
     * Stable identity for this element, independent of its position in the layout list.
     *
     * The editor addresses elements by this id (drag/edit/delete) so mutating the list (add/remove) can never make an in-flight edit point at the wrong element.
     */
    abstract val id: UUID
    /**
     * The X position of the element on the overlay (percentage, 0..1, based on the overlay width)
     */
    abstract val positionX: Double
    /**
     * The Y position of the element on the overlay (percentage, 0..1, based on the overlay height)
     */
    abstract val positionY: Double
    /**
     * The scale (X and Y) of the element (scaled by min(overlayWidth, overlayHeight))
     */
    abstract val scale: Double
    /**
     * The opacity of the element
     */
    abstract val opacity: Double

    @Serializable
    @SerialName("Key")
    data class Key(
        @Serializable(with = UUIDAsStringSerializer::class)
        override val id: UUID,
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,

        /**
         * The label of the button. If null falls back to a value derived from the [binding].
         */
        val label: String?,
        val trigger: KeyTrigger,
        val binding: InputBinding,
    ) : GamepadElement()

    @Serializable
    @SerialName("Joystick")
    data class Joystick(
        @Serializable(with = UUIDAsStringSerializer::class)
        override val id: UUID,
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,
        val up: InputBinding,
        val down: InputBinding,
        val left: InputBinding,
        val right: InputBinding,
    ) : GamepadElement()

    @Serializable
    @SerialName("AnalogJoystick")
    data class AnalogJoystick(
        @Serializable(with = UUIDAsStringSerializer::class)
        override val id: UUID,
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,
        val stick: GamepadStick,
        /** Which controller slot (0-based) this stick feeds. The on-screen pad is player 1 by default. */
        val device: Int = 0,
    ) : GamepadElement()

    @Serializable
    @SerialName("Menu")
    data class Menu(
        @Serializable(with = UUIDAsStringSerializer::class)
        override val id: UUID,
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,
    ) : GamepadElement()

    @Serializable
    @SerialName("MouseButton")
    data class MouseButton(
        @Serializable(with = UUIDAsStringSerializer::class)
        override val id: UUID,
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,
        val button: GmlMouseButton,
        val toggle: Boolean
    ) : GamepadElement()

    @Serializable
    @SerialName("FastForward")
    data class FastForward(
        @Serializable(with = UUIDAsStringSerializer::class)
        override val id: UUID,
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,
        val speed: Float,
        val toggle: Boolean
    ) : GamepadElement()
}