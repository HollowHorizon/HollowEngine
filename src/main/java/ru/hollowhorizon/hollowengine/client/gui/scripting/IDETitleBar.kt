package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.Assets
import de.fabmax.kool.editor.ui.TitleBgRenderer
import de.fabmax.kool.editor.ui.backgroundMid
import de.fabmax.kool.editor.ui.heightTitleBar
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color

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

            Image(Texture2d {
                Assets.loadImage2d("hollowengine:textures/gui/icons/code_editor.png").getOrThrow()
            }) {
                modifier.size(sizes.heightTitleBar - sizes.smallGap * 2, sizes.heightTitleBar - sizes.smallGap * 2)
                    .margin(end=sizes.gap).alignY(AlignmentY.Center)
            }

            menuItem("File") {
                menuItem("Закрыть")
            }
            divider(verticalMargin = 0.dp)
            menuItem("Edit")
            divider(verticalMargin = 0.dp)
            menuItem("Search")
            divider(verticalMargin = 0.dp)
            menuItem("Settings")

            Box {
                modifier.width(Grow.Std)
            }

            ComboBox {
                modifier.selectedIndex(currentItemIndex)
                    .onItemSelected { currentItemIndex = it }
                    .items(IDEGuiV2.files.map { it.fileName })
                    .height(sizes.heightTitleBar)
                    .margin(end=sizes.gap).alignY(AlignmentY.Center)
            }
            Image(Texture2d {
                Assets.loadImage2d("hollowengine:textures/gui/icons/play.png").getOrThrow()
            }) {
                modifier.size(sizes.heightTitleBar - sizes.smallGap * 2, sizes.heightTitleBar - sizes.smallGap * 2)
                    .margin(end=sizes.gap).alignY(AlignmentY.Center)
            }
            Image(Texture2d {
                Assets.loadImage2d("hollowengine:textures/gui/icons/stop.png").getOrThrow()
            }) {
                modifier.size(sizes.heightTitleBar - sizes.smallGap * 2, sizes.heightTitleBar - sizes.smallGap * 2)
                    .margin(end=sizes.gap).alignY(AlignmentY.Center)
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