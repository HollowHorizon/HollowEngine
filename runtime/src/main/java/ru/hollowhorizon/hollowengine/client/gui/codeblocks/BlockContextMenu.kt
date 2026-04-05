package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.Clipboard
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.PointerEvent
import de.fabmax.kool.modules.ui2.ScrollPaneNode
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.SubMenuItem
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.coroutines.LaunchPolicy

object BlockContextMenu {
    private val blockPopup = ItemPopupMenu<Vec2f>("BlockContextMenu")

    context(editor: BlockEditor)
    fun show(event: PointerEvent, uiNode: UiNode, block: BlockModel): Unit = with(editor) {
        val count = controller.selectedBlocks.size
        if (!controller.selectedBlocks.contains(block)) {
            controller.selectSingle(block)
        }

        val relativePos = (uiNode.findParentOfType<ScrollPaneNode>() ?: uiNode).toLocal(event.screenPosition)
        blockPopup.show(Vec2f(event.screenPosition), buildMenu(uiNode, block, count), Vec2f(relativePos))
    }

    context(scope: UiScope)
    fun draw() = with(scope) {
        blockPopup()
    }

    private fun LaunchPolicy.next(): LaunchPolicy {
        val policies = LaunchPolicy.entries
        return policies[(ordinal + 1) % policies.size]
    }

    private fun LaunchPolicy.label(): String {
        return name.lowercase().replace('_', ' ')
    }

    context(editor: BlockEditor)
    private fun buildMenu(uiNode: UiNode, block: BlockModel, count: Int): SubMenuItem<Vec2f> = with(editor) {
        return SubMenuItem("hollowengine.gui.block_context.block".lang, null) {
            if (controller.selectedBlocks.size > 1) {
                item("hollowengine.gui.block_context.delete_selected".lang.format(count)) { controller.deleteSelected() }
                item("hollowengine.gui.block_context.copy_uuid".lang) {
                    val uuids = controller.selectedBlocks.joinToString(", ") { it.uuid.toString() }
                    Clipboard.copyToClipboard(uuids)
                }
                return@SubMenuItem
            }

            if (block.isCollapsed.use(uiNode.surface)) {
                item("hollowengine.gui.block_context.expand".lang) { block.isCollapsed.set(false) }
            } else {
                item("hollowengine.gui.block_context.collapse".lang) { block.isCollapsed.set(true) }
            }

            (block as? StartBlock)?.let { startBlock ->
                item("Launch: ${startBlock.repeatPolicy.label()}", closeOnClick = false) {
                    startBlock.repeatPolicy = startBlock.repeatPolicy.next()
                    notifyChanged()
                    uiNode.surface.triggerUpdate()
                    blockPopup.updateMenu(buildMenu(uiNode, block, 1))
                }
            }

            item("hollowengine.gui.block_context.duplicate".lang) { controller.duplicateBlock(block, it) }
            item("hollowengine.gui.block_context.copy_uuid".lang) { Clipboard.copyToClipboard(block.uuid.toString()) }
            item("hollowengine.gui.block_context.delete".lang) { controller.deleteSelected() }
        }
    }
}
