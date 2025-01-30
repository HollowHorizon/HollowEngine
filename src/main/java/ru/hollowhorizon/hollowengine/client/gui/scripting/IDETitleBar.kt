package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.Image
import ru.hollowhorizon.hollowengine.client.gui.kool.TitleBgRenderer
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.kool.heightTitleBar
import ru.hollowhorizon.hollowengine.client.utils.lang

object IDETitleBar : Composable {
    var currentItemIndex = 0

    override fun UiScope.compose() {
        Row(width = Grow.Std, height = sizes.heightTitleBar - sizes.smallGap) {
            modifier.backgroundColor(colors.backgroundMid).padding(sizes.smallGap)
            modifier.background(
                TitleBgRenderer(
                    colors.backgroundMid,
                    Color.LIGHT_BLUE,
                    fade = TitleBgRenderer.fadeProps(Vec2f(0f, 0f), 100f, 1f)
                )
            )
            modifier.margin(vertical = sizes.smallGap)

            menuItem("hollowengine.gui.ide.file".lang) {
                menuItem("Закрыть")
            }
            divider(verticalMargin = 0.dp)
            menuItem("hollowengine.gui.ide.edit".lang)
            divider(verticalMargin = 0.dp)
            menuItem("hollowengine.gui.ide.search".lang)
            divider(verticalMargin = 0.dp)
            menuItem("hollowengine.gui.ide.settings".lang)

            Box {
                modifier.width(Grow.Std)
            }

            ComboBox {
                modifier.selectedIndex(currentItemIndex)
                    .onItemSelected { currentItemIndex = it }
                    .items(IDEGuiV2.files.map { it.fileName })
                    .height(sizes.heightTitleBar)
                    .width(250.dp)
                    .margin(end = sizes.gap).align(AlignmentX.End, AlignmentY.Center)
            }
            Image("hollowengine:textures/gui/icons/play.png") {
                modifier.size(sizes.heightTitleBar - sizes.smallGap * 2, sizes.heightTitleBar - sizes.smallGap * 2)
                    .margin(end = sizes.gap).alignY(AlignmentY.Center)
                var isHovered by remember { mutableStateOf(false) }

                modifier
                    .onEnter { isHovered = true }.onExit { isHovered = false }
                    .onClick { if (IDEGuiV2.files.isNotEmpty()) StartScriptPacket(IDEGuiV2.files[currentItemIndex].filePath).send() }

                if (isHovered) {
                    val color = Color("FFFFFF33")
                    modifier.background(RoundRectBackground(color, sizes.smallGap))
                        .tint(color)
                }
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
                modifier.background(RoundRectBackground(Color("FFFFFF33"), sizes.smallGap))
            }

            popup()
        }

    }
}