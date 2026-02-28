package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.utils.lang

fun EditPopup(labelKey: String, hintKey: String, onClick: (FileNode, String) -> Unit) =
    ItemPopupMenu<FileNode>(labelKey).apply {
        popupContent = Composable {
            modifier.align(AlignmentX.Center, AlignmentY.Center).layout(ColumnLayout)
                .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingMedium))
                .border(
                    RoundRectBorder(
                        ColorTheme.UI.BackgroundAccent,
                        Dimensions.PaddingMedium,
                        Dimensions.PaddingSmall
                    )
                )
                .padding(Dimensions.PaddingMedium)
            var text by remember { mutableStateOf("") }
            Text(labelKey.lang) {
                modifier.margin(Dimensions.PaddingNormal).padding(Dimensions.PaddingNormal)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }
            TextField {
                modifier.text(text)
                    .onChange { text = it }
                    .size(Grow.Std, FitContent).padding(Dimensions.PaddingNormal)
                    .margin(Dimensions.PaddingNormal)
                    .hint(hintKey.lang)
                    .colors(
                        lineColor = ColorTheme.UI.BackgroundElements.withAlpha(0.5f),
                        lineColorFocused = ColorTheme.UI.BackgroundAccent.withAlpha(0.5f)
                    )
            }
            ConfirmWidget(this@apply) {
                onClick(it, text)
            }
        }
    }

fun WarningModalPopup(labelKey: String, onClick: (FileNode) -> Unit) = ItemPopupMenu<FileNode>(labelKey).apply {
    popupContent = Composable {
        modifier.align(AlignmentX.Center, AlignmentY.Center).layout(ColumnLayout)
            .background(RoundRectBackground(ColorTheme.UI.BackgroundSecondary, Dimensions.PaddingMedium))
            .border(RoundRectBorder(ColorTheme.UI.BackgroundAccent, Dimensions.PaddingMedium, Dimensions.PaddingSmall))
            .padding(Dimensions.PaddingMedium)
        Text(labelKey.lang) {
            modifier.margin(Dimensions.PaddingSmall)
                .textColor(ColorTheme.UI.WhiteReplacement)
        }
        ConfirmWidget(this@apply) {
            onClick(it)
        }
    }
}

private fun UiScope.ConfirmWidget(
    itemPopupMenu: ItemPopupMenu<FileNode>,
    onClick: (FileNode) -> Unit,
) = Row {
    modifier.margin(Dimensions.PaddingNormal).padding(Dimensions.PaddingNormal)

    Button("hollowengine.gui.ide.popups.confirm".lang) {
        modifier.margin(Dimensions.PaddingSmall)
            .colors(
                ColorTheme.UI.BackgroundElements,
                ColorTheme.UI.WhiteReplacement,
                ColorTheme.UI.BackgroundAccent,
                Color.WHITE
            )
            .onClick {
                itemPopupMenu.item?.let { item ->
                    onClick(item)
                    surface.triggerUpdate()
                }
                itemPopupMenu.hide()
            }
    }
    Box(width = Grow.Std) {}
    Button("hollowengine.gui.ide.popups.cancel".lang) {
        modifier.margin(Dimensions.PaddingSmall).alignX(AlignmentX.End)
            .colors(
                ColorTheme.UI.BackgroundElements,
                ColorTheme.UI.WhiteReplacement,
                ColorTheme.UI.BackgroundAccent,
                Color.WHITE
            )
            .onClick { itemPopupMenu.hide() }
    }
}
