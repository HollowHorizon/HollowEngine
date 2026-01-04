package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry

class DragState(val editor: BlockEditor) : Composable {
    val isDragging = mutableStateOf(false)
    val dragPosition = mutableStateOf(Vec2f.ZERO)
    var entry: BlockEntry<*>? = null

    fun startDrag(entry: BlockEntry<*>, pos: Vec2f) {
        isDragging.set(true)
        dragPosition.set(pos)
        this.entry = entry
    }

    fun endDrag(screenPosition: Vec2f) {
        isDragging.set(false)
        entry?.let { entry ->
            val item = entry.createItem()
            val (x, y) = editor.controller.toLocal(screenPosition - dragPosition.value)
            // Отступы взяты из Box'а внутри Panel
            item.positionX.set(x + Dimensions.PaddingMedium.px)
            item.positionY.set(y + Dimensions.PaddingMedium.px)
            editor.rootBlocks.add(item)
            editor.notifyChanged()
        }
        entry = null
    }

    override fun UiScope.compose() {
        if (isDragging.use()) {
            val (offsetX, offsetY) = dragPosition.use()
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