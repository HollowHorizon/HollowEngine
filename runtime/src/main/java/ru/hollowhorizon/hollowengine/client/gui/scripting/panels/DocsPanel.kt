package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.generated.Assets

class DocsPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.docs", dock) {
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.DOCS_SVG
    var filter = mutableStateOf("")

    val items = buildList {
        add(DocCategory(icons.TUTORIAL, "hollowengine.gui.docs.category.beginners".lang))
        add(DocCategory(icons.FILE_KTS, "hollowengine.gui.docs.category.scripting".lang))
        add(DocCategory(icons.FILE_CODEBLOCKS, "hollowengine.gui.docs.category.codeblocks".lang))
        add(DocCategory(icons.GRAPH, "hollowengine.gui.docs.category.states".lang))
        add(DocCategory(icons.TIMER, "hollowengine.gui.docs.category.coroutines".lang))
        add(DocCategory(icons.NPCS, "hollowengine.gui.docs.category.npcs".lang))
        add(DocCategory(icons.PLAYERS, "hollowengine.gui.docs.category.players".lang))
        add(DocCategory(icons.RECIPES, "hollowengine.gui.docs.category.recipes".lang))

    }

    override fun UiScope.compose() {
        Column(Grow.Std, Grow.Std) {
            modifier.margin(Dimensions.PaddingNormal)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingNormal))

            Row(Grow.Std) {
                modifier.padding(Dimensions.PaddingMedium)
                    .margin(Dimensions.PaddingMedium)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingHuge))

                Image(Assets.Hollowengine.Textures.Gui.Icons.SEARCH) {
                    modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                        .alignY(AlignmentY.Center).margin(start = Dimensions.PaddingMedium)
                }

                TextField(filter.use()) {
                    modifier.alignY(AlignmentY.Center)
                        .size(Grow.Std, Grow.Std)
                        .colors(lineColor = Color.BLACK.withAlpha(0f), lineColorFocused = Color.BLACK.withAlpha(0f))
                        .hint("hollowengine.message.filter_docs".lang)
                        .onEnterPressed { surface.requestFocus(null) }
                        .onChange { filter.set(it) }
                        .margin(start = Dimensions.PaddingMedium)
                }
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
                items(items) {
                    DocItem(it)
                }
            }
        }
    }

    private fun UiScope.DocItem(category: DocCategory) {
        Row(Grow.Std) {
            modifier.background(RoundRectBackground(ColorTheme.UI.BackgroundElements, Dimensions.PaddingNormal))
                .margin(vertical=Dimensions.PaddingNormal)

            Image(category.icon) {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(Dimensions.PaddingMedium)
            }

            Text(category.label) {
                modifier.margin(Dimensions.PaddingMedium)
            }

            Box(Grow.Std) {}

            Arrow {
                modifier.size(Dimensions.PaddingHuge, Dimensions.PaddingHuge)
                    .margin(Dimensions.PaddingMedium)
                    .colors(ColorTheme.UI.BackgroundAccent, ColorTheme.UI.WhiteReplacement)
            }
        }
    }

    class DocCategory(val icon: ResourceLocation, val label: String)
}