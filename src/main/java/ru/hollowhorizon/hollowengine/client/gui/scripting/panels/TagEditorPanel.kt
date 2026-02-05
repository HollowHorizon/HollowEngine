package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.Registries
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.tags.TagEditorFile
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.common.codeblocks.modules.icons
import ru.hollowhorizon.hollowengine.common.tags.TagManager

class TagEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.tags", dock) {
    override val icon = icons.RECIPES

    private val tagStats = mutableStateOf(TagStats(0, 0, 0))

    data class TagStats(
        val totalTags: Int,
        val blockTags: Int,
        val itemTags: Int
    )

    override fun UiScope.compose() {
        modifier.margin(Dimensions.PaddingSmall)

        Column(Grow.Std, Grow.Std) {
            Header()
            StatsSection()
            QuickActionsSection()
        }
    }

    private fun UiScope.Header() {
        Box(Grow.Std) {
            modifier.padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            Column {
                Text("Tag Editor") {
                    modifier.font(sizes.largeText)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                }
                Text("Manage game tags") {
                    modifier.font(sizes.smallText)
                        .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                        .margin(top = Dimensions.PaddingSmall)
                }
            }
        }
    }

    private fun UiScope.StatsSection() {
        Box(Grow.Std) {
            modifier.margin(top = Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            updateTagStats()

            Column {
                Text("Statistics") {
                    modifier.font(sizes.normalText)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                        .margin(bottom = Dimensions.PaddingNormal)
                }

                val stats = tagStats.use()

                StatItem("Total Tags", stats.totalTags, ColorTheme.Accents.Main)
                StatItem("Block Tags", stats.blockTags, ColorTheme.Icons.Data)
                StatItem("Item Tags", stats.itemTags, ColorTheme.Icons.Assets)
            }
        }
    }

    private fun UiScope.StatItem(label: String, value: Int, color: Color) {
        Row(Grow.Std) {
            modifier.margin(vertical = Dimensions.PaddingSmall)

            Box {
                modifier.size(Dimensions.PaddingMedium, Dimensions.PaddingMedium)
                    .margin(end = Dimensions.PaddingNormal)
                    .background(RoundRectBackground(color, Dimensions.PaddingSmall))
                    .alignY(AlignmentY.Center)
            }

            Column(Grow.Std) {
                Text(label) {
                    modifier.font(sizes.normalText)
                        .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.8f))
                }
                Text(value.toString()) {
                    modifier.font(sizes.largeText)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                }
            }
        }
    }

    private fun UiScope.QuickActionsSection() {
        Box(Grow.Std) {
            modifier.margin(top = Dimensions.PaddingMedium)
                .padding(Dimensions.PaddingMedium)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingSmall))

            Column(Grow.Std) {
                Text("Quick Actions") {
                    modifier.font(sizes.normalText)
                        .textColor(ColorTheme.UI.WhiteReplacement)
                        .margin(bottom = Dimensions.PaddingNormal)
                }

                ActionButton(
                    "Open Tag Editor",
                    "View and edit all tags",
                    ColorTheme.Accents.Main
                ) {
                    IdeContent.openFile(TagEditorFile("tags.editor"))
                }
            }
        }
    }

    private fun UiScope.ActionButton(
        title: String,
        description: String,
        accentColor: Color,
        onClick: () -> Unit
    ) {
        Box(Grow.Std) {
            val isHovered by modifier.hoverable()
            val bgColor by animateColorAsState(
                if (isHovered) ColorTheme.UI.BackgroundAccent else ColorTheme.UI.BackgroundElements,
                tween(easing = Easing.easeOutQuart)
            )

            modifier.padding(Dimensions.PaddingNormal)
                .margin(vertical = Dimensions.PaddingSmall)
                .background(RoundRectBackground(bgColor, Dimensions.PaddingSmall))
                .onClick { onClick() }

            Row(Grow.Std) {
                modifier.align(AlignmentX.Start, AlignmentY.Center)

                Box {
                    modifier.size(Dimensions.PaddingNormal, Dimensions.PaddingExtraLarge)
                        .margin(end = Dimensions.PaddingMedium)
                        .background(RoundRectBackground(accentColor, Dimensions.PaddingSmall))
                }

                Column(Grow.Std) {
                    Text(title) {
                        modifier.font(sizes.normalText)
                            .textColor(ColorTheme.UI.WhiteReplacement)
                    }
                    Text(description) {
                        modifier.font(sizes.smallText)
                            .textColor(ColorTheme.UI.WhiteReplacement.withAlpha(0.7f))
                            .margin(top = Dimensions.PaddingSmall)
                    }
                }

                Text("→") {
                    modifier.font(sizes.largeText)
                        .textColor(accentColor)
                        .alignY(AlignmentY.Center)
                }
            }
        }
    }

    private fun updateTagStats() {
        var totalTags = 0
        var blockTags = 0
        var itemTags = 0

        blockTags += TagManager.BLOCK_TAGS.size
        itemTags += TagManager.ITEM_TAGS.size

        val connection = Minecraft.getInstance().connection
        if (connection != null) {
            connection.registryAccess().registry(Registries.BLOCK).ifPresent { registry ->
                val vanillaBlockTags = registry.tags.count()
                blockTags = maxOf(blockTags, vanillaBlockTags.toInt())
            }

            connection.registryAccess().registry(Registries.ITEM).ifPresent { registry ->
                val vanillaItemTags = registry.tags.count()
                itemTags = maxOf(itemTags, vanillaItemTags.toInt())
            }
        }

        totalTags = blockTags + itemTags
        tagStats.set(TagStats(totalTags, blockTags, itemTags))
    }
}
