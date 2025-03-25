package ru.hollowhorizon.hollowengine.client.gui.scripting.files

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent

class DocFileData(fileName: String, filePath: String, val content: Composable) :
    FileData(IdeContent, fileName, filePath) {
    override fun save() {}

    override fun UiScope.compose() {
        modifier.backgroundColor(colors.backgroundMid)
        val state = rememberListState()
        Box {
            modifier.size(Grow.Std, Grow.Std).onWheelX {
                    state.scrollDpX(it.pointer.delta.x * -20f)
                }.onWheelY {
                    state.scrollDpY(it.pointer.delta.y * -50f)
                }

            ScrollPane(state) {
                modifier.width(Grow.Std)

                Column(Grow.Std) {
                    content()
                }
            }

            VerticalScrollbar {
                lazyListAware(
                    state, ScrollbarOrientation.Vertical, ListOrientation.Vertical, null
                ) { it.width(10.dp).margin(5.dp) }
            }
        }
    }
}