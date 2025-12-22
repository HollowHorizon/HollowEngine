package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.PointerEvent
import de.fabmax.kool.modules.ui2.ScrollPaneNode
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiScope
import ru.hollowhorizon.hollowengine.client.audio.UIAudio
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

class BlockController {
    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()
    private var potentialAction: DropAction? = null

    var draggingBlock: BlockModel? = null
    var dragStartOffset = MutableVec2f()

    fun update() {
        dropTargets.clear()
    }

    fun isDragging(block: BlockModel) = draggingBlock in block.parentsWithSelf
    fun canAttachBefore(block: BlockModel) = (potentialAction as? DropAction.InsertBefore)?.target == block
    fun canAttachAfter(block: BlockModel) = (potentialAction as? DropAction.AttachAfter)?.target == block
    fun canAttachToInput(block: BlockModel, inputName: String) =
        (potentialAction as? DropAction.AttachToInput)?.let { it.target == block && it.inputName == inputName } == true

    val isStatementSlot: Boolean
        get() = (potentialAction as? DropAction.AttachToInput)?.isStatementSlot == true


    context(editor: BlockEditor, scope: UiScope) fun handleDragStart(block: BlockModel, ev: PointerEvent) {
        draggingBlock = block
        val visualScreenPos = scope.uiNode.toScreen(Vec2f.ZERO)
        dragStartOffset.set(ev.screenPosition.x - visualScreenPos.x, ev.screenPosition.y - visualScreenPos.y)
        val scrollPane = scope.uiNode.findParentOfType<ScrollPaneNode>()
        if (scrollPane != null) {
            val targetVisualScreenX = ev.screenPosition.x - dragStartOffset.x
            val targetVisualScreenY = ev.screenPosition.y - dragStartOffset.y
            detachBlock(block, scrollPane.toLocal(Vec2f(targetVisualScreenX, targetVisualScreenY)))
        }
    }

    context(editor: BlockEditor, scope: UiScope) fun handleDrag(block: BlockModel, ev: PointerEvent) {
        if (draggingBlock != block) return
        val scrollPane = scope.uiNode.findParentOfType<ScrollPaneNode>() ?: return
        val targetVisualScreenX = ev.screenPosition.x - dragStartOffset.x
        val targetVisualScreenY = ev.screenPosition.y - dragStartOffset.y
        val local = scrollPane.toLocal(Vec2f(targetVisualScreenX, targetVisualScreenY))
        block.positionX.set(local.x)
        block.positionY.set(local.y)

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
    }

    context(editor: BlockEditor) fun handleDragEnd(block: BlockModel) {
        potentialAction?.let { action ->
            triggerSnapEffect(action)
            UIAudio.CONNECT.play()
            when (action) {
                is DropAction.InsertBefore -> insertBlockBefore(action.target, block)
                is DropAction.AttachAfter -> attachBlockAfter(action.target, block as StatementBlock)
                is DropAction.AttachToInput -> attachBlockToInput(action.target, action.inputName, block)
            }
        }
        draggingBlock = null
        potentialAction = null
        editor.notifyChanged()
    }

    context(editor: BlockEditor)
    private fun attachBlockToInput(target: BlockModel, slotName: String, newBlock: BlockModel) {
        editor.rootBlocks.remove(newBlock)
        val existingBlock = target.inputs[slotName]
        if (existingBlock != null) {
            target.inputs[slotName] = newBlock
            newBlock.parentBlock = target
            newBlock.parentInputName = slotName


            if (newBlock.isStatement()) {
                newBlock.parent = null
                var tail: StatementBlock = newBlock
                while (tail.next != null) tail = tail.next!!
                tail.next = existingBlock as? StatementBlock ?: return
                existingBlock.parent = tail
            }
        } else {
            target.attachInput(slotName, newBlock)
        }
    }

    context(editor: BlockEditor)
    private fun attachBlockAfter(target: StatementBlock, newBlock: StatementBlock) {
        editor.rootBlocks.remove(newBlock)
        val oldNext = target.next
        target.next = newBlock
        newBlock.parent = target
        var tail = newBlock
        while (tail.next != null) tail = tail.next!!
        if (oldNext != null) {
            tail.next = oldNext
            oldNext.parent = tail
        }
    }

    context(editor: BlockEditor)
    private fun insertBlockBefore(target: BlockModel, newBlock: BlockModel) {
        editor.rootBlocks.remove(newBlock)
        val parent = (target as? StatementBlock)?.parent
        val parentBlock = target.parentBlock
        if (parent != null) {
            parent.next = newBlock as StatementBlock
            newBlock.parent = parent
        } else if (parentBlock != null) {
            val slotName = target.parentInputName!!
            parentBlock.inputs[slotName] = newBlock
            newBlock.parentBlock = parentBlock
            newBlock.parentInputName = slotName
            target.parentBlock = null
            target.parentInputName = null
        } else {
            editor.rootBlocks.remove(target)
            editor.rootBlocks.add(newBlock)
            if (newBlock.isStatement()) newBlock.parent = null
        }
        var tail = newBlock as? StatementBlock ?: return
        while (tail.next != null) tail = tail.next!!
        tail.next = target as? StatementBlock ?: return
        target.parent = tail
    }


    fun addDropTarget(action: DropAction, node: UiNode) {
        val exists = dropTargets.any { it.first == action && it.second == node }
        if (!exists) dropTargets.add(action to node)
    }

    context(editor: BlockEditor)
    private fun triggerSnapEffect(action: DropAction) {
        if (action is DropAction.InsertBefore) return
        val targetNode = dropTargets.find { it.first == action }?.second ?: return

        val centerX = targetNode.leftPx
        val centerY = targetNode.topPx

        val scrollPane = targetNode.findParentOfType<ScrollPaneNode>() ?: return

        val local = scrollPane.toLocal(Vec2f(centerX, centerY))

        val offsetX = if (action !is DropAction.AttachToInput) -7.5f else 0f
        val offsetY = if (action is DropAction.AttachToInput) -10f else 0f

        editor.triggerSnapEffect(SnapAnimation(local.x + offsetX, local.y + offsetY))
    }


    context(editor: BlockEditor) fun detachBlock(block: BlockModel, localPos: Vec2f) {
        if (block.isStatement()) {
            block.parent?.let { p ->
                if (p.next == block) p.next = null
                block.parent = null
            }
        }
        block.parentBlock?.let { p ->
            p.inputs.remove(block.parentInputName)
            block.parentBlock = null
            block.parentInputName = null
        }
        if (!editor.rootBlocks.contains(block)) {
            UIAudio.CONNECT.play()
            editor.rootBlocks.add(block)
            block.positionX.set(localPos.x)
            block.positionY.set(localPos.y)
        }
    }

    context(editor: BlockEditor) fun duplicateBlock(block: BlockModel, localPos: Vec2f) {
        val newBlock = block.deepCopy(editor.provider)

        newBlock.positionX.set(localPos.x)
        newBlock.positionY.set(localPos.y)
        editor.rootBlocks.add(newBlock)
        editor.notifyChanged()
    }

    context(editor: BlockEditor) fun removeBlock(block: BlockModel) {
        if (block.isStatement()) {
            val nextBlock = block.next

            block.parent?.let { parent ->
                parent.next = nextBlock
                nextBlock?.parent = parent
            } ?: run {
                if (nextBlock != null) {
                    editor.rootBlocks.add(nextBlock)
                    nextBlock.positionX.set(block.positionX.value)
                    nextBlock.positionY.set(block.positionY.value)
                    nextBlock.parent = null
                }
            }

            block.parent = null
            block.next = null
        }

        block.parentBlock?.let { parentContainer ->
            val slotName = block.parentInputName ?: return@let

            parentContainer.inputs.remove(slotName)
        }
        block.parentBlock = null
        block.parentInputName = null
        editor.rootBlocks.remove(block)


        editor.notifyChanged()
    }

    private fun isValidDrop(source: BlockModel, action: DropAction): Boolean {
        if (source == action.target) return false
        if (isAncestorOf(source, action.target)) return false

        return when (action) {
            is DropAction.InsertBefore, is DropAction.AttachAfter -> !source.isExpression()
            is DropAction.AttachToInput -> {
                if (source is ExpressionBlock) {
                    val requiredType = action.target.inputTypes[action.inputName] ?: return false
                    val returnType = source.expressionType
                    val typesMatch = requiredType.accepts(returnType) || returnType == AnyType
                    typesMatch && !action.isStatementSlot
                } else {
                    action.isStatementSlot
                }
            }
        }
    }

    private fun isAncestorOf(possibleParent: BlockModel, child: BlockModel): Boolean =
        possibleParent in child.parentsWithSelf

    fun resetAction() {
        potentialAction = null
    }
}