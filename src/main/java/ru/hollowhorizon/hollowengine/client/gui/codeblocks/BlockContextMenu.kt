package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.Clipboard
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.PointerEvent
import de.fabmax.kool.modules.ui2.ScrollPaneNode
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel

object BlockContextMenu {
    private val blockPopup = ItemPopupMenu<Vec2f>("BlockContextMenu")

    context(editor: BlockEditor)
    fun show(event: PointerEvent, uiNode: UiNode, block: BlockModel): Unit = with(editor) {
        val count = controller.selectedBlocks.size
        if (!controller.selectedBlocks.contains(block)) {
            controller.selectSingle(block)
        }

        val menuItems = SubMenuItem("Блок", null) {
            if (controller.selectedBlocks.size > 1) {
                item("Удалить выбранные ($count)") { controller.deleteSelected() }
                item("Копировать UUID") {
                    val uuids = controller.selectedBlocks.joinToString(", ") { it.uuid.toString() }
                    Clipboard.copyToClipboard(uuids)
                }
            } else {
                item("Дублировать") { controller.duplicateBlock(block, it) }
                item("Копировать UUID") { Clipboard.copyToClipboard(block.uuid.toString()) }
                item("Удалить") { controller.removeBlock(block) }
            }
        }

        val relativePos = (uiNode.findParentOfType<ScrollPaneNode>() ?: uiNode).toLocal(event.screenPosition)
        blockPopup.show(Vec2f(event.screenPosition), menuItems, Vec2f(relativePos))
    }

    context(editor: BlockEditor, scope: UiScope)
    fun draw() = with(scope) {
        blockPopup()
    }
}