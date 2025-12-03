package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.StringValueBlock

sealed interface DropAction {
    val target: CodeBlock
    data class InsertBefore(override val target: CodeBlock) : DropAction
    data class AttachAfter(override val target: CodeBlock) : DropAction
    data class AttachToInput(override val target: CodeBlock, val inputName: String) : DropAction
}

class BlockEditor {
    val rootBlocks = mutableStateListOf<CodeBlock>()

    var draggingBlock: CodeBlock? = null
    val dragStartOffset = MutableVec2f()
    var potentialAction: DropAction? = null

    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()
    private val tmpLocal = MutableVec2f()

    companion object {
        const val EXPRESSION_HORIZONTAL_OFFSET = PuzzleShapes.TAB_WIDTH
    }

    init {
        // Тест
        val b1 = PrintBlock("Default Text").apply { setPosition(100f, 100f) }
        rootBlocks.add(b1)
        rootBlocks.add(StringValueBlock("Free String").apply { setPosition(100f, 200f) })
    }

    fun UiScope.EditorLayout() {
        dropTargets.clear()

        ScrollPane(rememberScrollState()) {
            modifier.layout(CellLayout)
            modifier.width(Grow.Std).height(Grow.Std)
            modifier.onClick { potentialAction = null }

            rootBlocks.use().forEach { block ->
                renderBlockRecursively(block)
            }
        }
    }

    private fun UiScope.renderBlockRecursively(block: CodeBlock, isGhost: Boolean = false) {
        Column {
            val isRoot = rootBlocks.contains(block)
            val isDragging = draggingBlock == block

            if (isRoot) {
                modifier.zLayer(if (isDragging) UiSurface.LAYER_FLOATING else UiSurface.LAYER_DEFAULT)
                modifier.margin(start = Dp.fromPx(block.positionX.use()), top = Dp.fromPx(block.positionY.use()))
            }

            val action = potentialAction
            val isDraggingOther = draggingBlock != null && !isDragging

            if (!block.isExpression) {
                if (isDraggingOther && action is DropAction.InsertBefore && action.target == block) {
                    GhostPlaceholder()
                    addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
                }
            }

            Box {
                modifier.layout(CellLayout)

                BlockVisual(block, isGhost) {
                    modifier
                        .onDragStart { ev -> handleDragStart(block, ev) }
                        .onDrag { ev -> handleDrag(block, ev) }
                        .onDragEnd { handleDragEnd(block) }
                }
            }

            if (!block.isExpression) {
                if (isDraggingOther && action is DropAction.AttachAfter && action.target == block) {
                    GhostPlaceholder()
                }
                block.next?.let { next ->
                    renderBlockRecursively(next, isGhost)
                }
            }
        }
    }

    private fun UiScope.BlockVisual(block: CodeBlock, isGhost: Boolean, blockModifier: UiModifier.() -> Unit = {}) {
        Box {
            val marginLeft = if (block.isExpression) Dp.fromPx(EXPRESSION_HORIZONTAL_OFFSET) else 0.dp

            modifier
                .width(FitContent)
                .margin(start = marginLeft)
                .apply(blockModifier)

            val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color

            modifier.background(ScratchBlockBackground(
                color = bgColor,
                isExpression = block.isExpression,
                hasNext = !block.isExpression
            ))

            if(!block.isExpression) Box {
                modifier
                    .width(Grow.Std)
                    .height(sizes.smallGap)
                    .alignY(AlignmentY.Top)

                addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
            }
            Row {
                modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                modifier.alignY(AlignmentY.Center)

                with(InputSlotScope(this, block)) {
                    with(block) { composeContent() }
                }
            }
            if(!block.isExpression) Box {
                modifier
                    .width(Grow.Std)
                    .height(sizes.smallGap)
                    .alignY(AlignmentY.Bottom)

                addDropTargetOnce(DropAction.AttachAfter(block), uiNode)
            }
        }
    }

    private fun UiScope.GhostPlaceholder() {
        Box {
            modifier.height(40.dp).width(100.dp)
            modifier.background(ScratchBlockBackground(Color.WHITE.withAlpha(0.2f), false, true))
        }
    }


    inner class InputSlotScope(val uiScope: UiScope, val parentBlock: CodeBlock): UiScope by uiScope {
        fun InputSlot(name: String) {
            val attachedBlock = parentBlock.inputs[name]
            val action = potentialAction
            val isTargeted = action is DropAction.AttachToInput &&
                    action.target == parentBlock &&
                    action.inputName == name

            uiScope.Box {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(horizontal = 2.dp)

                addDropTargetOnce(DropAction.AttachToInput(parentBlock, name), uiNode)

                if (attachedBlock != null) {
                    if (draggingBlock == attachedBlock) {
                        EmptySlotVisual(isTargeted)
                    } else {
                        renderBlockRecursively(attachedBlock)

                        if (isTargeted) modifier.border(RectBorder(Color.WHITE, 2.dp))
                    }
                } else {
                    if (isTargeted && draggingBlock != null) {
                        Box {
                            modifier
                                .height(30.dp).width(50.dp)
                                .margin(start = Dp.fromPx(PuzzleShapes.TAB_WIDTH)) // Имитация ушка
                                .background(ScratchBlockBackground(draggingBlock!!.color.withAlpha(0.5f), true, false))
                        }
                    } else {
                        EmptySlotVisual(false)
                    }
                }
            }
        }

        private fun UiScope.EmptySlotVisual(highlight: Boolean) {
            Box {
                modifier
                    .size(40.dp, 30.dp)
                    .background(SlotBackground(
                        color = parentBlock.color.mix(Color.BLACK, 0.3f),
                        isHovered = highlight
                    ))
                if (highlight) modifier.border(RectBorder(Color.WHITE, 2.dp))
            }
        }
    }


    private fun UiScope.handleDragStart(block: CodeBlock, ev: PointerEvent) {
        draggingBlock = block

        val visualScreenPos = uiNode.toScreen(Vec2f.ZERO)
        dragStartOffset.set(ev.screenPosition.x - visualScreenPos.x, ev.screenPosition.y - visualScreenPos.y)

        detachBlock(block, ev.screenPosition)
    }

    private fun UiScope.handleDrag(block: CodeBlock, ev: PointerEvent) {
        if (draggingBlock != block) return
        val scrollPane = uiNode.findParentOfType<ScrollPaneNode>() ?: return

        val targetVisualScreenX = ev.screenPosition.x - dragStartOffset.x
        val targetVisualScreenY = ev.screenPosition.y - dragStartOffset.y

        scrollPane.toLocal(Vec2f(targetVisualScreenX, targetVisualScreenY), tmpLocal)

        block.setPosition(tmpLocal.x, tmpLocal.y)

        var bestAction: DropAction? = null
        for ((action, node) in dropTargets) {
            if (node.isInBounds(ev.screenPosition)) {
                if (isValidDrop(block, action)) {
                    bestAction = action
                    break
                }
            }
        }
        potentialAction = bestAction
        surface.triggerUpdate()
    }

    private fun UiScope.detachBlock(block: CodeBlock, screenPos: Vec2f) {
        block.parent?.let { p ->
            if (p.next == block) p.next = null
            block.parent = null
        }
        block.parentBlock?.let { p ->
            p.inputs.remove(block.parentInputName)
            block.parentBlock = null
            block.parentInputName = null
        }

        if (!rootBlocks.contains(block)) {
            rootBlocks.add(block)

            val scrollPane = uiNode.findParentOfType<ScrollPaneNode>()
            if (scrollPane != null) {
                val targetVisualScreenX = screenPos.x - dragStartOffset.x
                val targetVisualScreenY = screenPos.y - dragStartOffset.y

                scrollPane.toLocal(Vec2f(targetVisualScreenX, targetVisualScreenY), tmpLocal)

                block.setPosition(tmpLocal.x, tmpLocal.y)
            }
        }
    }


    private fun isValidDrop(source: CodeBlock, action: DropAction): Boolean {
        if (source == action.target) return false
        if (isAncestorOf(source, action.target)) return false

        if (source.isExpression) {
            if (action !is DropAction.AttachToInput) return false
        } else {
            if (action is DropAction.AttachToInput) return false
        }
        return true
    }

    private fun handleDragEnd(block: CodeBlock) {
        potentialAction?.let { action ->
            when (action) {
                is DropAction.InsertBefore -> insertBlockBefore(action.target, block)
                is DropAction.AttachAfter -> attachBlockAfter(action.target, block)
                is DropAction.AttachToInput -> attachBlockToInput(action.target, action.inputName, block)
            }
        }
        draggingBlock = null
        potentialAction = null
    }

    private fun attachBlockToInput(target: CodeBlock, slotName: String, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
        target.inputs[slotName]?.let { oldBlock ->
            oldBlock.parentBlock = null
            oldBlock.parentInputName = null
            rootBlocks.add(oldBlock)
            oldBlock.setPosition(newBlock.positionX.value + 30, newBlock.positionY.value + 30)
        }
        target.attachInput(slotName, newBlock)
    }

    private fun attachBlockAfter(target: CodeBlock, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
        val oldNext = target.next
        target.next = newBlock
        newBlock.parent = target
        var tail = newBlock
        while(tail.next != null) tail = tail.next!!
        if (oldNext != null) {
            tail.next = oldNext
            oldNext.parent = tail
        }
    }

    private fun insertBlockBefore(target: CodeBlock, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
        val parent = target.parent
        if (parent != null) {
            parent.next = newBlock
            newBlock.parent = parent
        } else {
            rootBlocks.remove(target)
            rootBlocks.add(newBlock)
            newBlock.parent = null
        }
        var tail = newBlock
        while(tail.next != null) tail = tail.next!!
        tail.next = target
        target.parent = tail
    }

    private fun isAncestorOf(possibleParent: CodeBlock, child: CodeBlock): Boolean {
        var curr: CodeBlock? = child.parent ?: child.parentBlock
        while (curr != null) {
            if (curr == possibleParent) return true
            curr = curr.parent ?: curr.parentBlock
        }
        return false
    }

    private fun addDropTargetOnce(action: DropAction, node: UiNode) {
        val exists = dropTargets.any { it.first == action && it.second == node }
        if (!exists) dropTargets.add(action to node)
    }
}

private inline fun <reified T> UiNode.findParentOfType(): T? {
    var current: UiNode? = this
    while (current != null && current !is T) {
        current = current.parent
    }
    return current as? T
}