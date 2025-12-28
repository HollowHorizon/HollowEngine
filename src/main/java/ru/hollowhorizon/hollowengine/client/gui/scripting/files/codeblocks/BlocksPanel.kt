package ru.hollowhorizon.hollowengine.client.gui.scripting.files.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.AccordionLayout
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategory
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.CategoryItem
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import kotlin.math.max
import kotlin.math.min

object BlocksPanel {
    context(scope: UiScope, editor: BlockEditor)
    fun Item(item: CategoryItem) {
        when (item) {
            is BlockCategory -> CategoryHeader(item)
            is BlockEntry<*> -> BlockEntry(item as BlockEntry<BlockModel>)
        }
    }

    context(scope: UiScope, editor: BlockEditor)
    fun BlockEntry(entry: BlockEntry<BlockModel>): Unit = with(scope) {
        val item = remember { entry.factory() }

        Box(scopeName = "CodeBlockRenderer") {
            modifier.padding(Dimensions.PaddingMedium)

            editor.renderBlockRecursively(item)
        }
    }

    context(scope: UiScope, editor: BlockEditor)
    fun CategoryHeader(category: BlockCategory): Unit = with(scope) {
        Column(Grow.Std, scopeName = category.name) {
            var isExpanded by remember(false)
            modifier.margin(Dimensions.PaddingMedium)

            Row(width = Grow.Std) {
                val isHovered by modifier.hoverable()
                val color by animateColorAsState(
                    if (isHovered) category.color.mix(
                        Color.WHITE,
                        0.33f
                    ) else category.color
                )

                modifier.padding(Dimensions.PaddingMedium)
                    .background(
                        BlockRoundRectBackground(
                            color,
                            Dimensions.PaddingMedium,
                            Dimensions.PaddingNormal + Dimensions.PaddingSmall,
                            isExpanded
                        )
                    )
                    .onClick {
                        isExpanded = !isExpanded
                    }

                category.icon?.let {
                    Image(it) {
                        modifier.size(Dimensions.PaddingLarge, Dimensions.PaddingLarge)
                            .margin(horizontal = Dimensions.PaddingHuge)
                            .tint(category.color)
                    }
                }

                Text(category.name) {
                    modifier
                        .font(remember {
                            MsdfFont(
                                ColorTheme.Fonts.MONOCRAFT,
                                Dimensions.FontNormal,
                                MsdfFont.ITALIC_NONE,
                                MsdfFont.WEIGHT_EXTRA_BOLD
                            )
                        })
                        .textColor(Color.WHITE)
                        .align(AlignmentX.Start, AlignmentY.Center)
                }

                Box(Grow.Std) { }

                Arrow {
                    modifier.rotation(if (isExpanded) ArrowScope.ROTATION_UP else ArrowScope.ROTATION_DOWN)
                        .size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .margin(horizontal = Dimensions.PaddingMedium)
                        .alignY(AlignmentY.Center)
                        .colors(category.color, category.color.mix(Color.WHITE, 0.5f))
                }
            }

            val animation by animateFloatAsState(if(isExpanded) 1f else 0f)
            if (isExpanded || animation > 0f) {
                Column(Grow.Std) {
                    modifier.padding(horizontal=Dimensions.PaddingNormal)
                        .layout(AccordionLayout(animation))
                        .backgroundColor(category.color.mix(Color.BLACK, 0.5f).mix(ColorTheme.UI.BackgroundSecondary, 0.5f))

                    category.items(editor).forEach { item ->
                        Item(item)
                    }
                }
            }
        }
    }
}

class BlockRoundRectBackground(val backgroundColor: Color, val cornerRadius: Dp, val border: Dp, val isExpanded: Boolean) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            val c = cornerRadius.px
            val lt = max(leftPx, clipLeftPx - c)
            val rt = min(rightPx, clipRightPx + c)
            val tp = max(topPx, clipTopPx - c)
            val bt = min(bottomPx, clipBottomPx + c)

            node.getUiPrimitives(UiSurface.LAYER_BACKGROUND).apply {
                if(isExpanded) rect(
                    lt, tp + (bt - tp) / 2f,
                    rt-lt, (bt + tp) / 2f,
                    clipBoundsPx, backgroundColor.mix(Color.BLACK, 0.5f).mix(ColorTheme.UI.BackgroundSecondary, 0.5f)
                )
                roundRect(
                    lt,
                    tp,
                    rt - lt,
                    bt - tp,
                    c,
                    clipBoundsPx,
                    backgroundColor.mix(Color.WHITE, 0.33f).mix(ColorTheme.UI.BackgroundSecondary, 0.75f)
                )
                roundRect(lt, tp, border.px, bt - tp, c, clipBoundsPx, backgroundColor)
            }
        }
    }
}