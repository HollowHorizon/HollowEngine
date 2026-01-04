package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.audio.UIAudio
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.model.*
import kotlin.math.max
import kotlin.math.min

class BlockController(val editor: BlockEditor) {
    val scrollState = ScrollState()
    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()
    private var potentialAction: DropAction? = null
    val scrollPaneBounds = MutableVec4f()

    // --- Selection State ---
    val selectedBlocks = mutableStateListOf<BlockModel>()
    var isSelecting = mutableStateOf(false)

    val selectionStart = MutableVec2f()
    val selectionCurr = MutableVec2f()

    val blockBounds = mutableMapOf<BlockModel, BlockRect>()

    var draggingBlock: BlockModel? = null
    var dragStartOffset = MutableVec2f()

    // --- Block Panel State ---
    val filter = mutableStateOf("")
    val isBlockPanelMinimized = mutableStateOf(false)

    data class BlockRect(val x: Float, val y: Float, val w: Float, val h: Float)

    fun update() {
        dropTargets.clear()
        blockBounds.clear()
    }

    fun toLocal(screenPosition: Vec2f): Vec2f = (screenPosition - scrollPaneBounds.xy) / editor.scale
    fun toLocal(screenX: Float, screenY: Float): Vec2f = toLocal(Vec2f(screenX, screenY))
    operator fun contains(screenPosition: Vec2f): Boolean =
        screenPosition.x in scrollPaneBounds.x + Dimensions.PaddingMedium.px ..scrollPaneBounds.x + scrollPaneBounds.z - Dimensions.PaddingMedium.px &&
                screenPosition.y in scrollPaneBounds.y + Dimensions.PaddingMedium.px..scrollPaneBounds.y + scrollPaneBounds.w - Dimensions.PaddingMedium.px

    fun startSelection(screenPos: Vec2f, contentNode: UiNode, zoom: Float) {
        selectedBlocks.clear()
        isSelecting.set(true)

        val local = contentNode.toLocal(screenPos)
        val logicX = local.x / zoom
        val logicY = local.y / zoom

        selectionStart.set(logicX, logicY)
        selectionCurr.set(logicX, logicY)
    }

    fun updateSelection(screenPos: Vec2f, contentNode: UiNode, zoom: Float) {
        val local = contentNode.toLocal(screenPos)
        val logicX = local.x / zoom
        val logicY = local.y / zoom

        selectionCurr.set(logicX, logicY)
        calculateSelectionIntersection()
    }

    fun endSelection() {
        isSelecting.set(false)
    }

    fun toggleSelection(block: BlockModel) {
        if (selectedBlocks.contains(block)) selectedBlocks.remove(block)
        else selectedBlocks.add(block)
    }

    fun selectSingle(block: BlockModel) {
        selectedBlocks.clear()
        selectedBlocks.add(block)
    }

    fun clearSelection() {
        if (selectedBlocks.isNotEmpty()) selectedBlocks.clear()
    }

    private fun calculateSelectionIntersection() {
        val scrollX = scrollState.xScrollDp.value * UiScale.measuredScale
        val scrollY = scrollState.yScrollDp.value * UiScale.measuredScale

        val xMin = min(selectionStart.x, selectionCurr.x) + scrollX
        val xMax = max(selectionStart.x, selectionCurr.x) + scrollX
        val yMin = min(selectionStart.y, selectionCurr.y) + scrollY
        val yMax = max(selectionStart.y, selectionCurr.y) + scrollY

        val newSelection = mutableListOf<BlockModel>()

        blockBounds.forEach { (block, bounds) ->
            if (xMin < bounds.x + bounds.w && xMax > bounds.x &&
                yMin < bounds.y + bounds.h && yMax > bounds.y
            ) {
                newSelection.add(block)
            }
        }

        if (selectedBlocks.size != newSelection.size || !selectedBlocks.containsAll(newSelection)) {
            selectedBlocks.clear()
            selectedBlocks.addAll(newSelection)
        }
    }

    fun registerBlockBounds(block: BlockModel, node: UiNode, zoom: Float) {
        val (x, y) = toLocal(node.leftPx, node.topPx)

        blockBounds[block] = BlockRect(
            x,
            y,
            node.widthPx / zoom,
            node.heightPx / zoom
        )
    }

    fun isDragging(block: BlockModel) =
        draggingBlock in block.parentsWithSelf || editor.dragState.entry?.previewItem == block

    fun canAttachBefore(block: BlockModel): Boolean {
        val target = (potentialAction as? DropAction.InsertBefore)?.target
        if (draggingBlock is EndBlock || target is StartBlock) return false
        return target == block
    }

    fun canAttachAfter(block: BlockModel): Boolean {
        val target = (potentialAction as? DropAction.AttachAfter)?.target
        if (draggingBlock is StartBlock || target is EndBlock) return false
        return target == block
    }

    fun canAttachToInput(block: BlockModel, inputName: String) =
        (potentialAction as? DropAction.AttachToInput)?.let { it.target == block && it.inputName == inputName } == true

    val isStatementSlot: Boolean
        get() = (potentialAction as? DropAction.AttachToInput)?.isStatementSlot == true


    fun handleDragStart(block: BlockModel, blockPosition: Vec2f, localOffset: Vec2f) {
        if (!selectedBlocks.contains(block)) {
            selectSingle(block)
        }

        draggingBlock = block
        dragStartOffset.set(localOffset)

        detachBlock(block, toLocal(blockPosition))
    }

    fun handleDrag(block: BlockModel, screenPosition: Vec2f) {
        if (draggingBlock != block) return

        val targetVisualScreenX = screenPosition.x - dragStartOffset.x
        val targetVisualScreenY = screenPosition.y - dragStartOffset.y
        val local = toLocal(targetVisualScreenX, targetVisualScreenY)

        block.positionX.set(local.x)
        block.positionY.set(local.y)

        // TODO: Нужно двигать все выбранные блоки

        var bestAction: DropAction? = null
        for ((action, node) in dropTargets) {
            if (node.isInBounds(screenPosition)) {
                if (isValidDrop(block, action)) {
                    bestAction = action
                    break
                }
            }
        }
        potentialAction = bestAction
    }

    fun handleDragEnd(block: BlockModel) {
        potentialAction?.let { action ->
            when (action) {
                is DropAction.InsertBefore -> if (canAttachBefore(action.target)) insertBlockBefore(
                    action.target,
                    block as StatementBlock
                )

                is DropAction.AttachAfter -> if (canAttachAfter(action.target)) attachBlockAfter(
                    action.target,
                    block as StatementBlock
                )

                is DropAction.AttachToInput -> if (canAttachToInput(
                        action.target,
                        action.inputName
                    )
                ) attachBlockToInput(action.target, action.inputName, block)
            }
            UIAudio.CONNECT.play()
            triggerSnapEffect(action)
        }
        draggingBlock = null
        potentialAction = null
        editor.notifyChanged()
    }

    // --- Actions ---

    fun deleteSelected() {
        val toDelete = ArrayList(selectedBlocks)
        toDelete.forEach { block ->
            removeBlock(block)
        }
        selectedBlocks.clear()
    }

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

    private fun attachBlockAfter(target: StatementBlock, newBlock: StatementBlock) {
        editor.rootBlocks.remove(newBlock)
        val oldNext = target.next
        target.next = newBlock
        newBlock.parent = target
        var tail = newBlock
        while (tail.next != null) tail = tail.next!!
        if (oldNext != null) {
            if (oldNext is EndBlock) {
                oldNext.parent = null
                editor.rootBlocks.add(oldNext)
            } else {
                tail.next = oldNext
                oldNext.parent = tail
            }
        }
    }

    private fun insertBlockBefore(target: BlockModel, newBlock: StatementBlock) {
        editor.rootBlocks.remove(newBlock)
        val parent = (target as? StatementBlock)?.parent
        val parentBlock = target.parentBlock
        if (parent != null) {
            if (newBlock is StartBlock) {
                editor.rootBlocks.add(newBlock)
                parent.next = null
                target.parent = newBlock
                newBlock.next = target
                return
            } else {
                parent.next = newBlock
                newBlock.parent = parent
            }
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
        var tail = newBlock
        while (tail.next != null) tail = tail.next!!
        tail.next = target as? StatementBlock ?: return
        target.parent = tail
    }

    fun addDropTarget(action: DropAction, node: UiNode) {
        val exists = dropTargets.any { it.first == action && it.second == node }
        if (!exists) dropTargets.add(action to node)
    }

    private fun triggerSnapEffect(action: DropAction) {
        if (action is DropAction.InsertBefore) return
        val targetNode = dropTargets.find { it.first == action }?.second ?: return

        val centerX = targetNode.leftPx
        val centerY = targetNode.topPx
        val scrollPane = targetNode.findParentOfType<ScrollPaneNode>() ?: return

        val local = scrollPane.toLocal(Vec2f(centerX, centerY))

        val zoom = editor.scale
        val logicalX = local.x / zoom
        val logicalY = local.y / zoom

        val offsetX = if (action !is DropAction.AttachToInput) -7.5f else 0f
        val offsetY = if (action is DropAction.AttachToInput) -10f else 0f

        editor.triggerSnapEffect(SnapAnimation(logicalX + offsetX, logicalY + offsetY))
    }

    fun detachBlock(block: BlockModel, localPos: Vec2f) {
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

    fun duplicateBlock(block: BlockModel, localPos: Vec2f) {
        val newBlock = block.deepCopy(editor.provider)
        val zoom = editor.scale
        newBlock.positionX.set(localPos.x / zoom)
        newBlock.positionY.set(localPos.y / zoom)
        editor.rootBlocks.add(newBlock)
        editor.notifyChanged()
    }

    fun removeBlock(block: BlockModel) {
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