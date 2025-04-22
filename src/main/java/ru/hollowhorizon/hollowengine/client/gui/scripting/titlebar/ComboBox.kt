package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverColors
import kotlin.math.min

object ComboBox {
    fun UiScope.comboBox(preview: String, items: List<Composable>) {
        var itemIndex by remember(-1)

        val popupMenu = remember { AutoPopup() }
        popupMenu.popupContent = Composable {
            var hoveredIndex by remember(-1)

            modifier
                .zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING)
                .background(RoundRectBackground(colors.background, sizes.smallGap))
                .border(RoundRectBorder(Color("3C3C4AFF"), sizes.smallGap, sizes.borderWidth))
                .padding(sizes.smallGap)
                .height((24.dp + sizes.smallGap * 2) * min(7, items.size) + sizes.gap)

            LazyColumn(
                withHorizontalScrollbar = false,
                isScrollableHorizontal = false,
                containerModifier = { it.background(null).margin(sizes.smallGap) },
                vScrollbarModifier = { it.zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING) }
            ) {
                itemsIndexed(items) { i, item ->
                    Box {
                        modifier
                            .padding(sizes.smallGap * 0.5f)
                            .margin(sizes.smallGap * 0.5f)
                            .width(Grow.Std)
                            .onEnter { hoveredIndex = i }
                            .onExit { hoveredIndex = -1 }
                            .onClick {
                                itemIndex = i
                                popupMenu.hide()
                                hoveredIndex = -1
                            }
                        if (i == hoveredIndex) {
                            modifier
                                .background(RoundRectBackground(IdeTheme.hoveredColors.background, sizes.smallGap))
                        }
                        if (items.size > 7) {
                            // make some space for the scrollbar
                            modifier.margin(end = sizes.gap)
                        }

                        item()
                    }
                }
            }
        }

        Row {
            modifier.padding(horizontal = sizes.smallGap)
                .alignY(AlignmentY.Center)
            modifier.background(RoundRectBackground(hoverColors(color=colors.background, hoverColor=IdeTheme.hoveredColors.background), sizes.smallGap))

            if (popupMenu.isVisible.use()) modifier.background(RoundRectBackground(IdeTheme.hoveredColors.background, sizes.smallGap))

            if (itemIndex == -1) {
                Text(preview) {
                    modifier.alignY(AlignmentY.Center)
                        .margin(end = sizes.smallGap)
                }
            } else {
                items[itemIndex]()
            }

            modifier.onClick {
                popupMenu.show(Vec2f(uiNode.leftPx, uiNode.bottomPx))
            }

            Box {
                modifier.margin(horizontal = sizes.smallGap)
                    .align(AlignmentX.End, AlignmentY.Center)
                    .padding(sizes.smallGap * 0.5f)

                Arrow {
                    modifier.rotation(90f)
                        .colors(arrowColor = colors.onBackground)
                        .alignY(AlignmentY.Center)
                        .size(18f.dp, 18f.dp)
                }
            }
        }

        popupMenu()
    }
}