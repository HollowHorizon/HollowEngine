package ru.hollowhorizon.hollowengine.client.gui.kool

import de.fabmax.kool.input.KeyEvent
import de.fabmax.kool.modules.ui2.*

context(scope: UiScope)
fun UiModifier.onKeyEvent(handler: (KeyEvent) -> Unit) {
    val handler = scope.remember { KeyHandler(handler) }
    surface.requestFocus(handler)
}

class KeyHandler(val handler: (KeyEvent) -> Unit) : Focusable {
    override val isFocused = mutableStateOf(false)

    override fun onKeyEvent(keyEvent: KeyEvent) = handler(keyEvent)
}