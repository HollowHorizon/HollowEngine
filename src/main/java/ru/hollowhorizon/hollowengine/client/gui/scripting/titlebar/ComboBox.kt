package ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar

import de.fabmax.kool.KeyValueStore
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import kotlin.math.min

object ComboBox {

    context(scope: UiScope)
    operator fun invoke(preview: String, items: List<Composable>, itemIndex: MutableStateValue<Int>): Unit = with(scope) {
        var index by itemIndex

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
                                index = i
                                KeyValueStore.setInt("ide.file_index", i)
                                popupMenu.hide()
                                hoveredIndex = -1
                            }
                        if (i == hoveredIndex) {
                            modifier
                                .background(RoundRectBackground(ColorTheme.UI.BackgroundGeneral, sizes.smallGap))
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
            val isHovered by modifier.hoverable()
            val color by animateColorAsState(if(isHovered) ColorTheme.UI.BackgroundElements else ColorTheme.UI.BackgroundSecondary, tween(easing = Easing.easeOutQuart))

            modifier.padding(horizontal=Dimensions.PaddingMedium, vertical=Dimensions.PaddingNormal)
                .margin(horizontal=Dimensions.PaddingMedium)
                .alignY(AlignmentY.Center)
            modifier.background(RoundRectBackground(color, sizes.smallGap))

            if (popupMenu.isVisible.use()) modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, sizes.smallGap))

            val id = index.coerceIn(-1, items.lastIndex)
            if (id == -1) {
                Text(preview) {
                    modifier.alignY(AlignmentY.Center)
                        .margin(end = sizes.smallGap)
                }
            } else {
                items[id]()
            }

            modifier.onClick {
                popupMenu.show(Vec2f(uiNode.leftPx, uiNode.bottomPx))
            }

            Box {
                modifier.margin(Dimensions.PaddingSmall)
                    .alignY(AlignmentY.Center)
                    .padding(Dimensions.PaddingSmall)

                Arrow {
                    modifier.rotation(90f)
                        .colors(ColorTheme.UI.BackgroundAccent, ColorTheme.UI.WhiteReplacement)
                        .align(AlignmentX.Center, AlignmentY.Center)
                        .size(Dimensions.PaddingMedium, Dimensions.PaddingMedium)
                }
            }
        }

        popupMenu()
    }
}