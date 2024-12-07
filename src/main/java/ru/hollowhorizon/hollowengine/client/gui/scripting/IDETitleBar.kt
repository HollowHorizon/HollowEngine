package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.editor.ui.backgroundMid
import de.fabmax.kool.editor.ui.heightTitleBar
import de.fabmax.kool.editor.ui.hoverBg
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*

object IDETitleBar : Composable {
    override fun UiScope.compose() {
        Row(width = Grow.Std, height = sizes.heightTitleBar) {
            modifier.backgroundColor(colors.backgroundMid).padding(sizes.smallGap)

            menuItem("File") {
                menuItem("Закрыть")
            }
            divider()
            menuItem("Edit")
            divider()
            menuItem("Search")
            divider()
            menuItem("Settings")

            Box {
                modifier.width(Grow.Std)
            }
        }
    }

    fun UiScope.menuItem(label: String, block: UiScope.() -> Unit = {}) {

        Text(label) {
            val popup by remember { mutableStateOf(AutoPopup()) }
            var isHovered by remember { mutableStateOf(false) }

            val node = uiNode

            modifier.align(AlignmentX.Center, AlignmentY.Center)
                .onEnter { isHovered = true }.onExit { isHovered = false }
                .onClick { popup.show(Vec2f(node.leftPx, node.bottomPx)) }
                .margin(horizontal = sizes.smallGap)

            popup.popupContent = Composable {
                Column(FitContent, FitContent) {
                    modifier.background(RoundRectBackground(colors.backgroundMid, sizes.smallGap))
                        .border(RoundRectBorder(colors.secondaryVariant, sizes.smallGap, sizes.borderWidth))
                        .padding(sizes.smallGap)
                    block()
                }
            }



            if (isHovered) {
                modifier.background(RoundRectBackground(colors.hoverBg, sizes.smallGap))
            }

            popup()
        }

    }
}