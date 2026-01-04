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
    val history = HistoryManager(editor)

    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()
    private var potentialAction: DropAction? = null
    val scrollPaneBounds = MutableVec4f()

    // --- Selection & Clipboard ---
    val selectedBlocks = mutableStateListOf<BlockModel>()
    var isSelecting = mutableStateOf(false)
    private val clipboard = mutableListOf<BlockModel>()

    val selectionStart = MutableVec2f()
    val selectionCurr = MutableVec2f()
    val blockBounds = mutableMapOf<BlockModel, BlockRect>()

    // --- Draggable State ---
    var draggingBlock: BlockModel? = null
    var dragStartOffset = MutableVec2f()
    private val initialBlockPositions = mutableMapOf<BlockModel, Vec2f>()
    private var dragStartConnectionState: ConnectionState? = null

    private val dragStartScreenPos = MutableVec2f()

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
        screenPosition.x in scrollPaneBounds.x + Dimensions.PaddingMedium.px..scrollPaneBounds.x + scrollPaneBounds.z - Dimensions.PaddingMedium.px &&
                screenPosition.y in scrollPaneBounds.y + Dimensions.PaddingMedium.px..scrollPaneBounds.y + scrollPaneBounds.w - Dimensions.PaddingMedium.px

    fun startSelection(screenPos: Vec2f, contentNode: UiNode, zoom: Float) {
        selectedBlocks.clear()
        isSelecting.set(true)
        val local = contentNode.toLocal(screenPos)
        selectionStart.set(local.x / zoom, local.y / zoom)
        selectionCurr.set(selectionStart)
    }

    fun updateSelection(screenPos: Vec2f, contentNode: UiNode, zoom: Float) {
        val local = contentNode.toLocal(screenPos)
        selectionCurr.set(local.x / zoom, local.y / zoom)
        calculateSelectionIntersection()
    }

    fun endSelection() {
        isSelecting.set(false)
    }

    fun toggleSelection(block: BlockModel) {
        if (selectedBlocks.contains(block)) selectedBlocks.remove(block) else selectedBlocks.add(block)
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
        blockBounds[block] = BlockRect(x, y, node.widthPx / zoom, node.heightPx / zoom)
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


    fun handleDragStart(block: BlockModel, screenPosition: Vec2f, localOffset: Vec2f) {
        if (!selectedBlocks.contains(block)) {
            selectSingle(block)
        }

        draggingBlock = block
        dragStartOffset.set(localOffset)
        dragStartScreenPos.set(screenPosition)

        initialBlockPositions.clear()
        dragStartConnectionState = captureConnectionState(block)

        val topLevelMovers = selectedBlocks.filter { !isParentSelected(it) }

        topLevelMovers.forEach { mover ->
            if (!editor.rootBlocks.contains(mover)) {
                val bounds = blockBounds[mover]
                detachBlockInternal(mover)
                mover.positionX.set(bounds?.x ?: 0f)
                mover.positionY.set(bounds?.y ?: 0f)
            }
            if (!editor.rootBlocks.contains(mover)) editor.rootBlocks.add(mover)

            initialBlockPositions[mover] = Vec2f(mover.positionX.value, mover.positionY.value)
        }
    }

    fun handleDrag(block: BlockModel, screenPosition: Vec2f) {
        if (draggingBlock != block) return

        var deltaX = (screenPosition.x - dragStartScreenPos.x - dragStartOffset.x) / editor.scale
        var deltaY = (screenPosition.y - dragStartScreenPos.y - dragStartOffset.y) / editor.scale

        initialBlockPositions.values.forEach { initialPos ->
            val proposedX = initialPos.x + deltaX
            val proposedY = initialPos.y + deltaY
            if (proposedX < 0) deltaX = max(deltaX, -initialPos.x)
            if (proposedY < 0) deltaY = max(deltaY, -initialPos.y)
        }

        initialBlockPositions.forEach { (mover, initialPos) ->
            mover.positionX.set(initialPos.x + deltaX)
            mover.positionY.set(initialPos.y + deltaY)
        }

        if (initialBlockPositions.containsKey(draggingBlock)) {
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
        } else {
            potentialAction = null
        }
    }

    fun handleDragEnd(block: BlockModel, isNewBlock: Boolean = false) {
        val action = potentialAction
        val actionsToPerform = mutableListOf<EditorAction>()

        if (isNewBlock) {
            // Удаляем блок для предпросмотра, чтобы AddBlocksAction добавил его сам и запомнил
            editor.rootBlocks.remove(block)
            actionsToPerform.add(AddBlocksAction(editor, listOf(block)))
        }

        if (action != null) {
            val oldState = if (isNewBlock) {
                ConnectionState(
                    parentBlock = null,
                    parentInputName = null,
                    parentStatement = null,
                    nextStatement = null,
                    indexInRoot = -1 // Не важно для логики Undo при создании
                )
            } else {
                dragStartConnectionState!!
            }

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

            val newState = captureConnectionState(block)
            actionsToPerform.add(ConnectionAction(editor, block, oldState, newState))
        } else {
            val moves = mutableMapOf<BlockModel, Pair<Vec2f, Vec2f>>()
            initialBlockPositions.forEach { (mover, startPos) ->
                val current = Vec2f(mover.positionX.value, mover.positionY.value)
                if (startPos.distance(current) > 1f) {
                    moves[mover] = startPos to current
                }
            }

            if (moves.isNotEmpty()) {
                val oldState = dragStartConnectionState!!
                if (oldState.parentBlock != null || oldState.parentStatement != null) {
                    val newState = captureConnectionState(block)
                    actionsToPerform.add(ConnectionAction(editor, block, oldState, newState))
                } else {
                    actionsToPerform.add(MoveBlockAction(moves))
                }
            }
        }

        if (actionsToPerform.isNotEmpty()) {
            if (actionsToPerform.size == 1) {
                history.perform(actionsToPerform.first())
            } else {
                history.perform(CompoundAction(actionsToPerform))
            }
        }

        draggingBlock = null
        potentialAction = null
        dragStartConnectionState = null
        editor.notifyChanged()
    }

    fun copySelected() {
        clipboard.clear()
        val topLevel = selectedBlocks.filter { !isParentSelected(it) }
        topLevel.forEach { original ->
            val copy = original.deepCopy(editor.provider)
            clipboard.add(copy)
        }
    }

    fun cutSelected() {
        if (selectedBlocks.isEmpty()) return

        copySelected()
        deleteSelected()
    }

    fun paste() {
        if (clipboard.isEmpty()) return

        clearSelection()
        val newBlocks = mutableListOf<BlockModel>()

        val pasteOffset = 20.0f

        clipboard.forEach { originalCopy ->
            val freshCopy = originalCopy.deepCopy(editor.provider)
            freshCopy.positionX.set(freshCopy.positionX.value + pasteOffset)
            freshCopy.positionY.set(freshCopy.positionY.value + pasteOffset)

            newBlocks.add(freshCopy)
            selectedBlocks.add(freshCopy)
        }

        history.perform(AddBlocksAction(editor, newBlocks))
    }

    fun deleteSelected() {
        val topLevel = selectedBlocks.filter { !isParentSelected(it) }
        if (topLevel.isEmpty()) return

        topLevel.forEach { block ->
            detachBlockInternal(block, true)
        }

        history.perform(RemoveBlocksAction(editor, topLevel))
        selectedBlocks.clear()
    }

    // --- Internal Logic ---

    private fun captureConnectionState(block: BlockModel): ConnectionState {
        return ConnectionState(
            parentBlock = block.parentBlock,
            parentInputName = block.parentInputName,
            parentStatement = (block as? StatementBlock)?.parent,
            nextStatement = (block as? StatementBlock)?.next,
            indexInRoot = editor.rootBlocks.indexOf(block)
        )
    }

    fun detachBlockInternal(block: BlockModel, onlyOnce: Boolean = false) {
        if (block.isStatement()) {
            val nextBlock = block.next
            val hasParent = block.parent != null
            block.parent?.let { p ->
                if (p.next == block) {
                    if (onlyOnce && nextBlock != null) {
                        p.next = nextBlock
                        nextBlock.parent = p
                    } else {
                        p.next = null
                    }
                }
                block.parent = null
            }
            if (onlyOnce) {
                if (!hasParent) block.next?.let {
                    it.positionX.set(block.positionX.value)
                    it.positionY.set(block.positionY.value)
                    editor.rootBlocks.add(it)
                }
                block.next = null
            }
        }
        block.parentBlock?.let { p ->
            p.inputs.remove(block.parentInputName)
            block.parentBlock = null
            block.parentInputName = null
        }
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

    fun duplicateBlock(block: BlockModel, localPos: Vec2f) {
        val newBlock = block.deepCopy(editor.provider)
        val zoom = editor.scale
        newBlock.positionX.set(localPos.x / zoom)
        newBlock.positionY.set(localPos.y / zoom)
        history.perform(AddBlocksAction(editor, listOf(newBlock)))
    }

    fun duplicateSelected() {
        if (selectedBlocks.isEmpty()) return

        val topLevel = selectedBlocks.filter { !isParentSelected(it) }

        val newBlocks = mutableListOf<BlockModel>()
        val offset = 20f

        topLevel.forEach { original ->
            val copy = original.deepCopy(editor.provider)

            copy.positionX.set(copy.positionX.value + offset)
            copy.positionY.set(copy.positionY.value + offset)

            newBlocks.add(copy)
        }

        if (newBlocks.isNotEmpty()) {
            clearSelection()
            selectedBlocks.addAll(newBlocks)

            history.perform(AddBlocksAction(editor, newBlocks))
        }
    }

    fun resetCamera() {
        editor.scaleState.set(1.0f)
        scrollState.scrollDpX(0f - scrollState.xScrollDp.value)
        scrollState.scrollDpY(0f - scrollState.yScrollDp.value)
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

    private fun isParentSelected(block: BlockModel): Boolean {
        if (block is StatementBlock) {
            block.parent?.let { if (selectedBlocks.contains(it)) return true }
        }
        block.parentBlock?.let { if (selectedBlocks.contains(it)) return true }
        return false
    }

    fun resetAction() {
        potentialAction = null
    }

    fun selectAll() {
        selectedBlocks.clear()
        selectedBlocks.addAll(editor.rootBlocks.flatMap { it.walk() })
    }
}