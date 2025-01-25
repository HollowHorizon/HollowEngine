package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.mixins.kool.DragAndDropContextAccessor

open class DndHandler(
    override val dropTarget: UiNode,
) : DragAndDropHandler<FileNode> {

    val isHovered = mutableStateOf(false)
    val isDrag = mutableStateOf(false)

    override fun onDragStart(
        dragItem: FileNode,
        dragPointer: PointerEvent,
        source: DragAndDropHandler<FileNode>?
    ) {
        if (isMatchingFlavor(dragItem)) {
            onMatchingDragStart(dragItem, dragPointer, source)
        }
    }

    override fun onDrag(
        dragItem: FileNode,
        dragPointer: PointerEvent,
        source: DragAndDropHandler<FileNode>?,
        isHovered: Boolean
    ) {
        if (isHovered && isMatchingFlavor(dragItem)) {
            onMatchingHover(dragItem, dragPointer, source, true)
        } else {
            onMatchingHover(dragItem, dragPointer, source, false)
        }
    }

    override fun onDragEnd(
        dragItem: FileNode,
        dragPointer: PointerEvent,
        source: DragAndDropHandler<FileNode>?,
        target: DragAndDropHandler<FileNode>?,
        success: Boolean
    ) {
        isHovered.set(false)
        isDrag.set(false)
    }

    override fun receive(dragItem: FileNode, dragPointer: PointerEvent, source: DragAndDropHandler<FileNode>?): Boolean {
        if (isMatchingFlavor(dragItem)) {
            onMatchingReceive(dragItem, dragPointer, source)
            return true
        }
        return false
    }

    protected open fun isMatchingFlavor(dragItem: FileNode): Boolean {
        return true
    }

    protected open fun onMatchingReceive(
        dragItem: FileNode,
        dragPointer: PointerEvent,
        source: DragAndDropHandler<FileNode>?
    ) { }

    protected open fun onMatchingDragStart(
        dragItem: FileNode,
        dragPointer: PointerEvent,
        source: DragAndDropHandler<FileNode>?
    ) {
        isDrag.set(true)
    }

    protected open fun onMatchingHover(
        dragItem: FileNode,
        dragPointer: PointerEvent,
        source: DragAndDropHandler<FileNode>?,
        isHovered: Boolean
    ) {
        this.isHovered.set(isHovered)
    }
}

@Suppress("UNCHECKED_CAST")
fun <T: Any> DragAndDropContext<T>.dragItem() = (this as DragAndDropContextAccessor).dragItem as T?