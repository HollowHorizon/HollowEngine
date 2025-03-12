package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import kotlin.math.min

object ComboBox {
    fun UiScope.comboBox(preview: String, items: List<Composable>) {
        var itemIndex by remember(-1)

        val popupMenu = remember { AutoPopup() }
        popupMenu.popupContent = Composable {
            var hoveredIndex by remember(-1)

            modifier
                .zLayer(UiSurface.LAYER_POPUP)
                .background(RoundRectBackground(colors.background, sizes.smallGap))
                .border(RoundRectBorder(colors.backgroundVariant, sizes.smallGap, sizes.borderWidth))
                .padding(sizes.smallGap)
                .height((Dp.fromPx(sizes.normalText.lineHeight) + sizes.smallGap) * min(7, items.size) + sizes.gap)

            LazyList(
                withHorizontalScrollbar = false,
                isScrollableHorizontal = false,
                vScrollbarModifier = { it.zLayer(UiSurface.LAYER_POPUP + UiSurface.LAYER_FLOATING) }
            ) {
                itemsIndexed(items) { i, item ->
                    Box {
                        items[i]()

                        modifier
                            .width(Grow.Std)
                            .padding(horizontal = sizes.gap, vertical = sizes.smallGap * 0.5f)
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
                    }
                }
            }
        }

        Row {
            var isHovered by remember { mutableStateOf(false) }
            modifier.padding(horizontal=sizes.smallGap).onEnter { isHovered = true }.onExit { isHovered = false }
                .alignY(AlignmentY.Center)
            if (isHovered) modifier.background(RoundRectBackground(IdeTheme.hoveredColors.background, sizes.smallGap))

            if(itemIndex == -1) {
                Text(preview) {
                    modifier.alignY(AlignmentY.Center)
                    modifier.margin(end=sizes.smallGap)
                }
            } else {
                items[itemIndex]()
            }

            modifier.onClick {
                popupMenu.show(Vec2f(uiNode.leftPx, uiNode.bottomPx))
            }

            Box {
                modifier.margin(horizontal = sizes.smallGap)
                    .alignY(AlignmentY.Center).padding(sizes.smallGap*0.5f)

                Arrow {
                    modifier.rotation(90f)
                        .align(AlignmentX.End, AlignmentY.Center)
                        .size(18f.dp, 18f.dp)
                }
            }
        }

        popupMenu()
    }
}