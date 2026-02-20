package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.AccordionColumnLayout
import ru.hollowhorizon.hollowengine.client.gui.scripting.AccordionRowLayout
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategory
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.CategoryItem
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.generated.Assets

class BlocksPanel(val editor: BlockEditor) {

    context(scope: UiScope)
    fun Item(item: CategoryItem) {
        when (item) {
            is BlockCategory -> CategoryHeader(item)
            is BlockEntry<*> -> BlockEntry(item as BlockEntry<BlockModel>)
        }
    }

    context(scope: UiScope)
    fun BlockEntry(entry: BlockEntry<BlockModel>): Unit = with(scope) {
        Box(scopeName = "CodeBlockRenderer") {
            modifier.padding(Dimensions.PaddingMedium)
                .onDragStart { editor.dragState.startDrag(entry, it.position) }
                .onDrag { editor.dragState.drag(it.screenPosition) }
                .onDragEnd { editor.dragState.endDrag() }

            editor.renderBlockTree(entry.previewItem, canDrag = false)

        }
    }

    context(scope: UiScope)
    fun CategoryHeader(category: BlockCategory): Unit = with(scope) {
        val isExpanded = category.isExpanded.use()

        Column(Grow.Std, scopeName = category.name) {
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
                            category.isExpanded.use()
                        )
                    )
                    .onClick {
                        category.isExpanded.set(!category.isExpanded.value)
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

            val animation by animateFloatAsState(if (isExpanded) 1f else 0f)
            if (isExpanded || animation > 0f) {
                Column(Grow.Std) {
                    modifier.padding(horizontal = Dimensions.PaddingNormal)
                        .layout(AccordionColumnLayout(animation))
                        .backgroundColor(
                            category.color.mix(Color.BLACK, 0.5f).mix(ColorTheme.UI.BackgroundSecondary, 0.5f)
                        )

                    category.items(editor).forEach { item ->
                        Item(item)
                    }
                }
            }
        }
    }

    context(scope: UiScope)
    operator fun invoke(expansion: Float): Unit = with(scope) {
        val filter = editor.controller.filter

        Column(FitContent, Grow.Std) {
            modifier.margin(horizontal = Dimensions.PaddingNormal)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingMedium))
                .layout(AccordionRowLayout(expansion))

            Row(Grow.Std) {
                modifier.margin(Dimensions.PaddingMedium)

                Row(Grow.Std) {
                    modifier.padding(Dimensions.PaddingMedium)
                        .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingHuge))

                    Image(Assets.Hollowengine.Textures.Gui.Icons.SEARCH) {
                        modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                            .alignY(AlignmentY.Center).margin(start = Dimensions.PaddingMedium)
                    }

                    TextField(filter.use()) {
                        modifier.alignY(AlignmentY.Center)
                            .size(Grow.Std, Grow.Std)
                            .colors(
                                lineColor = Color.BLACK.withAlpha(0f),
                                lineColorFocused = Color.BLACK.withAlpha(0f)
                            )
                            .hint("hollowengine.message.block_filter".lang)
                            .onEnterPressed { surface.requestFocus(null) }
                            .onChange { filter.set(it) }
                            .margin(start = Dimensions.PaddingMedium)
                    }
                }

                CollapseButton {}
            }

            LazyColumn(
                containerModifier = { it.backgroundColor(null) },
                scrollPaneModifier = { it.width(Grow.Std).margin(horizontal = Dimensions.PaddingNormal) },
                vScrollbarModifier = {
                    it.width(Dimensions.PaddingMedium).colors(
                        ColorTheme.UI.BackgroundElements,
                        ColorTheme.UI.BackgroundAccent,
                        Color.WHITE.withAlpha(0f),
                        ColorTheme.UI.BackgroundElements.withAlpha(0.3f),
                    )
                },
                hScrollbarModifier = {
                    it.height(Dimensions.PaddingMedium).colors(
                        ColorTheme.UI.BackgroundElements,
                        ColorTheme.UI.BackgroundAccent,
                        Color.WHITE.withAlpha(0f),
                        ColorTheme.UI.BackgroundElements.withAlpha(0.3f),
                    )
                },
                withHorizontalScrollbar = true
            ) {
                items(editor.provider.rootCategory.items(editor)) {
                    val oldScale = editor.scale
                    editor.scale = 1f

                    with(editor) {
                        Item(it)
                    }

                    editor.scale = oldScale
                }
            }
        }
    }

    context(scope: UiScope)
    fun CollapseButton(body: UiScope.() -> Unit) = with(scope) {
        val isMinimized = editor.controller.isBlockPanelMinimized
        Box {
            modifier.padding(Dimensions.PaddingMedium)
                .margin(start = Dimensions.PaddingNormal)
                .alignY(AlignmentY.Center)

            val isHovered by modifier.hoverable()
            val color by animateColorAsState(if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundSecondary)
            modifier.background(RoundRectBackground(color, Dimensions.PaddingNormal))
                .onClick { if (it.pointer.isLeftButtonEvent) isMinimized.set(!isMinimized.use()) }
                .zLayer(100_000_000)

            Image(if (isMinimized.use()) icons.MAXIMIZE else icons.MINIMIZE) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .align(AlignmentX.Center, AlignmentY.Center)
            }

            body()
        }
    }
}