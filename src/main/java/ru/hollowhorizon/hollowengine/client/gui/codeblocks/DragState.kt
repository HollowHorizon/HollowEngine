package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel

class DragState(val editor: BlockEditor) : Composable {
    val isDragging = mutableStateOf(false)
    val dragOffset = mutableStateOf(Vec2f.ZERO)
    var entry: BlockEntry<*>? = null
    var item: BlockModel? = null

    fun startDrag(entry: BlockEntry<*>, pos: Vec2f) {
        isDragging.set(true)
        dragOffset.set(pos)
        this.entry = entry
    }

    fun drag(blockPosition: Vec2f) {
        val item = item
        val pos = blockPosition - dragOffset.value
        val (x, y) = editor.controller.toLocal(pos)
        if (item == null) {
            if (pos in editor.controller) {
                val newItem = entry?.createItem() ?: return
                // Отступы взяты из Box'а внутри Panel
                newItem.positionX.set(x + Dimensions.PaddingMedium.px)
                newItem.positionY.set(y + Dimensions.PaddingMedium.px)
                editor.rootBlocks.add(newItem)
                editor.notifyChanged()
                editor.controller.handleDragStart(newItem, blockPosition, dragOffset.value)
                this.item = newItem
            }
        } else {
            editor.controller.handleDrag(item, blockPosition + dragOffset.value)
        }
    }

    fun endDrag() {
        isDragging.set(false)

        item?.let {
            editor.controller.handleDragEnd(it)
        }
        item = null
        entry = null
    }

    override fun UiScope.compose() {
        if (isDragging.use() && item == null) {
            val (offsetX, offsetY) = dragOffset.use()
            val (x, y) = PointerInput.primaryPointer.pos
            Popup(x - offsetX, y - offsetY, layout = CellLayout) {
                modifier.background(null)
                    .zLayer(100_000_000)

                Box(scopeName = "CodeBlockRenderer") {
                    modifier.padding(Dimensions.PaddingMedium)
                    entry?.let {
                        editor.renderBlockTree(it.previewItem, canDrag = false)
                    }
                }
            }
            surface.triggerUpdate()
        }
    }
}