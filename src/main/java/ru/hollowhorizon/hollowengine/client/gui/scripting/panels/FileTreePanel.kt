package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.generated.Assets

class FileTreePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.project_tree", dock) {
    override val icon = Assets.Hollowengine.Textures.Gui.Icons.CODE_EDITOR
    var filter = mutableStateOf("")

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
                        .hint("hollowengine.message.filter".lang)
                        .onEnterPressed { surface.requestFocus(null) }
                        .onChange { filter.set(it) }
                        .margin(start=Dimensions.PaddingMedium)
                }
            }
            IdeContent.fileTree.apply {
                draw(filter.use())
            }
        }
    }
}