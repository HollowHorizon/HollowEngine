package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.modules.ui2.RowScope
import ru.hollowhorizon.hollowengine.common.events.ClientEvent

open class TitleBarCreationEvent(private val scope: RowScope) : ClientEvent {
    fun append(block: RowScope.() -> Unit) {
        scope.block()
    }

    class Start(scope: RowScope) : TitleBarCreationEvent(scope)
    class Center(scope: RowScope) : TitleBarCreationEvent(scope)
    class End(scope: RowScope) : TitleBarCreationEvent(scope)
}