package ru.hollowhorizon.hollowengine.client.gui.scripting.tools

import de.fabmax.kool.modules.ui2.*

context(scope: UiScope)
fun UiModifier.hoverable(): MutableStateValue<Boolean> = with(scope) {
    val listener = remember { mutableStateOf(false) }
    onEnter { listener.set(true) }
    onExit { listener.set(false) }
    return@with listener
}