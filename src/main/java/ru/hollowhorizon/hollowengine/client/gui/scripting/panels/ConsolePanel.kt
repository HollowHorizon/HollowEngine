package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import kotlinx.datetime.Instant
import org.slf4j.event.Level
import ru.hollowhorizon.hollowengine.HollowCore

class ConsolePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.console", dock) {
    override val icon = "hollowengine:textures/gui/icons/code_editor.svg"
    private val isScrollLock = mutableStateOf(true)


    override fun UiScope.compose() {
        val listState = rememberListState()
        TextArea(
            lineProvider = ListTextLineProvider(),
            state = listState,
            scrollPaneModifier = { it.margin(horizontal = sizes.gap) },
        ) {
            modifier
                .lastLineBottomPadding(sizes.largeGap)
                .backgroundColor(null)
                .onWheelY { ev ->
                    if (ev.pointer.scroll.y > 0.0) {
                        isScrollLock.set(false)
                    } else if (ev.pointer.scroll.y < 0.0 && listState.itemsTo == listState.numTotalItems - 1) {
                        isScrollLock.set(true)
                    }
                }

            installDefaultSelectionHandler()

            linesHolder.modifier.isAutoScrollToEnd(isScrollLock.use())
        }
    }
}