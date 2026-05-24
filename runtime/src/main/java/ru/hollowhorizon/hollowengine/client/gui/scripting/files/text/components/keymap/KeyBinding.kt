package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.keymap

import de.fabmax.kool.input.KeyCode
import de.fabmax.kool.input.KeyEvent

data class KeyBinding(
    val keyCode: KeyCode,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val trigger: Trigger = Trigger.Pressed,
) {
    fun matches(event: KeyEvent): Boolean {
        if (!trigger.matches(event)) return false
        if (!matchesKeyCode(event.keyCode.code) && !matchesKeyCode(event.localKeyCode.code)) return false
        if (event.isCtrlDown != ctrl) return false
        if (event.isShiftDown != shift) return false
        if (event.isAltDown != alt) return false
        return true
    }

    private fun matchesKeyCode(actual: Int): Boolean {
        if (actual == keyCode.code) return true

        val actualChar = actual.toChar()
        val expectedChar = keyCode.code.toChar()
        if (!actualChar.isLetter() || !expectedChar.isLetter()) return false

        return actualChar.uppercaseChar() == expectedChar.uppercaseChar()
    }

    enum class Trigger {
        Pressed,
        Released,
        CharTyped;

        fun matches(event: KeyEvent): Boolean = when (this) {
            Pressed -> event.isPressed
            Released -> event.isReleased
            CharTyped -> event.isCharTyped
        }
    }
}
