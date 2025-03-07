package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.Image
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.ThemeEditor
import ru.hollowhorizon.hollowengine.client.utils.lang

class IDETitleBar(val scene: Scene) : Composable {
    var currentItemIndex = 0

    override fun UiScope.compose() {
        val ideSurface = surface

        Row(width = Grow.Std, height = 10.dp) {
            modifier.padding(horizontal = sizes.smallGap * 0.5f)
            modifier.background(
                RectGradientBackground(colors.background.mulRgb(2f), colors.background, 0.dp, 20.dp, 500.dp, 500.dp)
            )

            menuItem("hollowengine.gui.ide.file".lang) {
                menuItem("Закрыть")
            }
            Divider()
            menuItem("hollowengine.gui.ide.edit".lang)
            Divider()
            menuItem("hollowengine.gui.ide.search".lang)
            Divider()
            menuItem("hollowengine.gui.ide.settings".lang) {
                menuItem("hollowengine.gui.ide.settings.theme".lang,
                    onClick = {
                        val themeSurface = scene.addWindowSurface(ThemeEditor.dockable, ideColors, ideSizes) {
                            ThemeEditor.ideSurface = ideSurface
                            ThemeEditor()
                        }
                        ThemeEditor.onRemove = {
                            scene.removeNode(themeSurface)
                        }
                    }
                )
            }
        }

        Box {
            modifier.width(Grow.Std)
        }

        if (IDEStorage.files.any { it.value is TextFileData }) {
            ComboBox {
                modifier.selectedIndex(currentItemIndex)
                    .onItemSelected { currentItemIndex = it }
                    .items(IDEStorage.files.map { it.key.substringAfterLast('/') })
                    .size(FitContent, sizes.gap)
                    .align(AlignmentX.End, AlignmentY.Center)
            }
            Divider()
            Image("hollowengine:textures/gui/icons/play.png") {
                modifier.size(sizes.gap, sizes.gap)
                    .padding(sizes.smallGap * 0.25f)
                    .alignY(AlignmentY.Center)
                var isHovered by remember { mutableStateOf(false) }

                modifier
                    .onEnter { isHovered = true }.onExit { isHovered = false }
                    .onClick { if (IDEStorage.files.isNotEmpty()) StartScriptPacket(IDEStorage.files.map { it.key }[currentItemIndex]).send() }

                if (isHovered) {
                    val color = Color("FFFFFF33")
                    modifier.background(RoundRectBackground(color, sizes.smallGap * 0.5f))
                }
            }
        }
    }
}

fun UiScope.menuItem(label: String, block: UiScope.() -> Unit = {}, onClick: (() -> Unit)? = null) {

    Text(label) {
        val popup by remember { mutableStateOf(AutoPopup()) }
        var isHovered by remember { mutableStateOf(false) }

        val node = uiNode

        modifier.align(AlignmentX.Center, AlignmentY.Center)
            .onEnter { isHovered = true }.onExit { isHovered = false }
            .onClick {
                if (onClick != null) {
                    onClick()
                } else {
                    popup.show(Vec2f(node.leftPx, node.bottomPx))
                }
            }

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

private fun UiScope.Divider() {
    Box {
        modifier.size(sizes.borderWidth * .5f, Grow.Std)
            .margin(horizontal = sizes.smallGap * 0.5f, vertical = sizes.smallGap * 0.25f)
            .backgroundColor(Color.WHITE)
    }
}