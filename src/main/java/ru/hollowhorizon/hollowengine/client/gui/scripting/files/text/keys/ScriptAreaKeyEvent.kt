package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import de.fabmax.kool.input.KeyCode
import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAreaNode
import ru.hollowhorizon.hollowengine.common.events.ClientEvent

class ScriptAreaKeyEvent(
    val keyCode: KeyCode,
    val localKeyCode: KeyCode,
    val event: Int,
    val modifiers: Int,
    val typedChar: Char,
    val area: TextAreaNode,
) : ClientEvent, Cancelable {
    override var isCanceled = false
    val isPressed: Boolean get() = (event and KeyboardInput.KEY_EV_DOWN) != 0
    val isRepeated: Boolean get() = (event and KeyboardInput.KEY_EV_REPEATED) != 0
    val isReleased: Boolean get() = (event and KeyboardInput.KEY_EV_UP) != 0
    val isCharTyped: Boolean get() = (event and KeyboardInput.KEY_EV_CHAR_TYPED) != 0

    val isShiftDown: Boolean get() = (modifiers and KeyboardInput.KEY_MOD_SHIFT) != 0
    val isCtrlDown: Boolean get() = (modifiers and KeyboardInput.KEY_MOD_CTRL) != 0
    val isAltDown: Boolean get() = (modifiers and KeyboardInput.KEY_MOD_ALT) != 0
    val isSuperDown: Boolean get() = (modifiers and KeyboardInput.KEY_MOD_SUPER) != 0
}

fun ScriptAreaKeyEvent.toKool() = KeyEvent(keyCode, localKeyCode, event, modifiers, typedChar)
fun KeyEvent.toEngine(area: TextAreaNode) = ScriptAreaKeyEvent(keyCode, localKeyCode, event, modifiers, typedChar, area)