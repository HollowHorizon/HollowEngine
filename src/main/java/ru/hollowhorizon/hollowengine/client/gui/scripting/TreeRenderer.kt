package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.launchOnMainThread
import kotlinx.coroutines.delay
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.utils.lang

fun EditPopup(label: String, hint: String, onClick: (FileNode, String) -> Unit) = ItemPopupMenu<FileNode>(label).apply {
    popupContent = Composable {
        modifier.align(AlignmentX.Center, AlignmentY.Center).layout(ColumnLayout)
            .background(RoundRectBackground(colors.backgroundMid, sizes.gap))
            .border(RoundRectBorder(colors.primaryVariant, sizes.gap, sizes.borderWidth))
            .padding(sizes.gap)
        var text by remember { mutableStateOf("") }
        Text(label.lang) {
            modifier.margin(sizes.smallGap)
        }
        TextField {
            modifier.text(text)
                .onChange { text = it }
                .size(Grow.Std, FitContent)
                .margin(sizes.smallGap)
                .hint(hint)
        }
        ConfirmWidget(this@apply) {
            onClick(it, text)
            launchOnMainThread {
                delay(500)
                it.parent?.toggleExpanded()
                it.parent?.toggleExpanded()
            }
        }
    }
}

fun WarningModalPopup(label: String, onClick: (FileNode) -> Unit) = ItemPopupMenu<FileNode>(label).apply {
    popupContent = Composable {
        modifier.align(AlignmentX.Center, AlignmentY.Center).layout(ColumnLayout)
            .background(RoundRectBackground(colors.backgroundMid, sizes.gap))
            .border(RoundRectBorder(colors.primaryVariant, sizes.gap, sizes.borderWidth))
            .padding(sizes.gap)
        Text(label.lang) {
            modifier.margin(sizes.smallGap)
        }
        ConfirmWidget(this@apply) {
            onClick(it)
            launchOnMainThread {
                delay(500)
                it.parent?.toggleExpanded()
                it.parent?.toggleExpanded()
            }
        }
    }
}

private fun UiScope.ConfirmWidget(
    itemPopupMenu: ItemPopupMenu<FileNode>,
    onClick: (FileNode) -> Unit,
) = Row {
    modifier.margin(sizes.smallGap)

    Button("Подтвердить") {
        modifier.margin(sizes.smallGap)
            .onClick {
                itemPopupMenu.item?.let { item ->
                    onClick(item)
                    surface.triggerUpdate()
                }
                itemPopupMenu.hide()
            }
    }
    Box(width = Grow.Std) {}
    Button("Отмена") {
        modifier.margin(sizes.smallGap).alignX(AlignmentX.End)
            .onClick { itemPopupMenu.hide() }
    }
}