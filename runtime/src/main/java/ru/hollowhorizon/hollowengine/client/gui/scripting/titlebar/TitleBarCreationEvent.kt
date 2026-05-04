package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.modules.ui2.RowScope
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

open class TitleBarCreationEvent(private val scope: RowScope) : ClientEvent {
    fun append(block: RowScope.() -> Unit) {
        scope.block()
    }

    class Start(scope: RowScope) : TitleBarCreationEvent(scope) {
        companion object : EventHandler<Start>()
    }

    class Center(scope: RowScope) : TitleBarCreationEvent(scope) {
        companion object : EventHandler<Center>()
    }
    class End(scope: RowScope) : TitleBarCreationEvent(scope) {
        companion object : EventHandler<End>()
    }
}