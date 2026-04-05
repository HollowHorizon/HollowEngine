package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
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
    private var dragStartConnectionState: Map<BlockModel, ConnectionState>? = null

    private val dragStartScreenPos = MutableVec2f()

    val filter = mutableStateOf("")
    val isBlockPanelMinimized = mutableStateOf(false)

    data class BlockRect(val x: Float, val y: Float, val w: Float, val h: Float)

    fun update() {
        dropTargets.clear()
        blockBounds.clear()
    }

    fun toLocal(screenPosition: Vec2f): Vec2f {
        val scrollX = scrollState.xScrollDp.value * UiScale.measuredScale
        val scrollY = scrollState.yScrollDp.value * UiScale.measuredScale

        val relX = screenPosition.x - scrollPaneBounds.x + scrollX
        val relY = screenPosition.y - scrollPaneBounds.y + scrollY

        return Vec2f(relX / editor.scale, relY / editor.scale)
    }

    fun toLocal(screenX: Float, screenY: Float): Vec2f = toLocal(Vec2f(screenX, screenY))
    operator fun contains(screenPosition: Vec2f): Boolean =
        screenPosition.x in scrollPaneBounds.x + Dimensions.PaddingMedium.px..scrollPaneBounds.x + scrollPaneBounds.z - Dimensions.PaddingMedium.px &&
                screenPosition.y in scrollPaneBounds.y + Dimensions.PaddingMedium.px..scrollPaneBounds.y + scrollPaneBounds.w - Dimensions.PaddingMedium.px

    fun startSelection(screenPos: Vec2f) {
        selectedBlocks.clear()
        isSelecting.set(true)
        val local = toLocal(screenPos)
        selectionStart.set(local.x, local.y)
        selectionCurr.set(selectionStart)
    }

    fun updateSelection(screenPos: Vec2f) {
        val local = toLocal(screenPos)
        selectionCurr.set(local.x, local.y)
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
        val xMin = min(selectionStart.x, selectionCurr.x)
        val xMax = max(selectionStart.x, selectionCurr.x)
        val yMin = min(selectionStart.y, selectionCurr.y)
        val yMax = max(selectionStart.y, selectionCurr.y)

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

    fun isDragging(block: BlockModel): Boolean {
        val previewRoot = editor.dragState.entry?.previewItem
        return draggingBlock in block.parentsWithSelf || previewRoot in block.parentsWithSelf
    }

    fun canAttachBefore(block: BlockModel): Boolean {
        val target = (potentialAction as? DropAction.InsertBefore)?.target
        // Нельзя вставить перед StartBlock (он триггер)
        if (draggingBlock is EndBlock || target is StartBlock) return false
        return target == block
    }

    fun canAttachAfter(block: BlockModel): Boolean {
        val target = (potentialAction as? DropAction.AttachAfter)?.target
        // Нельзя прицепить StartBlock (он должен быть началом)
        if (draggingBlock is StartBlock || target is EndBlock) return false
        return target == block
    }

    fun canAttachToInput(block: BlockModel, inputName: String) =
        (potentialAction as? DropAction.AttachToInput)?.let { it.target == block && it.inputName == inputName } == true

    fun canAttachToOutput(block: BlockModel, outputName: String) =
        (potentialAction as? DropAction.AttachToOutput)?.let { it.target == block && it.outputName == outputName } == true

    val isStatementSlot: Boolean
        get() = (potentialAction as? DropAction.AttachToInput)?.isStatementSlot == true


    fun handleDragStart(block: BlockModel, screenPosition: Vec2f, localOffset: Vec2f) {
        if (!selectedBlocks.contains(block)) {
            selectSingle(block)
        }

        draggingBlock = block
        dragStartOffset.set(localOffset)
        dragStartScreenPos.set(screenPosition + localOffset)

        initialBlockPositions.clear()
        val relevantBlocks = mutableListOf(block)
        block.parentBlock?.let { relevantBlocks.add(it) }
        (block as? StatementBlock)?.parent?.let { relevantBlocks.add(it) }

        dragStartConnectionState = relevantBlocks.associateWith { captureConnectionState(it) }

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

        val startWorldPos = toLocal(dragStartScreenPos)
        val currentWorldPos = toLocal(screenPosition)

        var deltaX = currentWorldPos.x - startWorldPos.x
        var deltaY = currentWorldPos.y - startWorldPos.y

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
            editor.rootBlocks.remove(block)
            actionsToPerform.add(AddBlocksAction(editor, listOf(block)))
        }

        if (action != null) {
            val affectedBlocks = mutableSetOf<BlockModel>()
            affectedBlocks.add(block)

            when (action) {
                is DropAction.InsertBefore -> {
                    affectedBlocks.add(action.target) // Тот, перед кем встаем
                    action.target.parentBlock?.let { affectedBlocks.add(it) } // Родитель-контейнер (если был)
                    (action.target as? StatementBlock)?.parent?.let { affectedBlocks.add(it) } // Родитель-стейтмент (если был)
                }

                is DropAction.AttachAfter -> {
                    affectedBlocks.add(action.target) // Тот, после кого встаем
                    action.target.next?.let { affectedBlocks.add(it) } // Тот, кто был после (если мы вклиниваемся)
                }

                is DropAction.AttachToInput -> {
                    affectedBlocks.add(action.target) // Тот, к кому цепляемся
                    action.target.inputs[action.inputName]?.let { affectedBlocks.add(it) } // Тот, кто там уже был
                }

                is DropAction.AttachToOutput -> {
                    affectedBlocks.add(action.target)
                    action.target.outputs[action.outputName]?.let { affectedBlocks.add(it) }
                }
            }

            if (block is StatementBlock) {
                var tail: StatementBlock = block
                while (tail.next != null) tail = tail.next!!
                if (tail != block) affectedBlocks.add(tail)
            }

            if (!isNewBlock) {
                dragStartConnectionState?.keys?.forEach { affectedBlocks.add(it) }
            }

            val oldStates = affectedBlocks.associateWith {
                dragStartConnectionState?.get(it) ?: captureConnectionState(it)
            }
            when (action) {
                is DropAction.InsertBefore -> insertBlockBeforeLogic(action.target, block as StatementBlock)
                is DropAction.AttachAfter -> attachBlockAfterLogic(action.target, block as StatementBlock)
                is DropAction.AttachToInput -> attachBlockToInputLogic(action.target, action.inputName, block)
                is DropAction.AttachToOutput -> attachBlockToOutputLogic(action.target, action.outputName, block)
            }

            editor.playConnectSound()
            triggerSnapEffect(action)

            val newStates = affectedBlocks.associateWith { captureConnectionState(it) }
            affectedBlocks.forEach { affected ->
                val old = oldStates[affected]!!
                val new = newStates[affected]!!
                if (old != new) {
                    actionsToPerform.add(ConnectionAction(editor, affected, old, new))
                }
            }
        } else {
            val moves = mutableMapOf<BlockModel, Pair<Vec2f, Vec2f>>()
            initialBlockPositions.forEach { (mover, startPos) ->
                val current = Vec2f(mover.positionX.value, mover.positionY.value)
                if (startPos.distance(current) > 1f) {
                    moves[mover] = startPos to current
                }
            }

            if (moves.isNotEmpty()) {
                val oldStates = dragStartConnectionState

                if (oldStates != null && oldStates.values.any { it.parentBlock != null || it.parentStatement != null }) {
                    val affectedBlocks = oldStates.keys.toMutableSet()
                    affectedBlocks.add(block)
                    val finalStates = affectedBlocks.associateWith { captureConnectionState(it) }
                    affectedBlocks.forEach { affected ->
                        val old = oldStates[affected] ?: captureConnectionState(affected)
                        val new = finalStates[affected]!!
                        if (old != new) {
                            actionsToPerform.add(ConnectionAction(editor, affected, old, new))
                        } else if (affected == block) {
                            val move = moves[block] ?: return@forEach
                            actionsToPerform.add(MoveBlockAction(mapOf(block to move)))
                        }
                    }
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

    private fun attachBlockToInputLogic(target: BlockModel, slotName: String, newBlock: BlockModel) {
        editor.rootBlocks.remove(newBlock)
        detachBlockInternal(newBlock)

        val existingBlock = target.inputs[slotName]
        if (existingBlock != null) {
            if (newBlock.isStatement() && existingBlock.isStatement()) {
                val stmtNew = newBlock
                val stmtExist: StatementBlock = existingBlock

                target.inputs[slotName] = stmtNew
                stmtNew.parentBlock = target
                stmtNew.parentInputName = slotName
                stmtNew.parentOutputName = null

                var tail: StatementBlock = stmtNew
                while (tail.next != null) tail = tail.next!!

                tail.next = stmtExist
                stmtExist.parent = tail
                stmtExist.parentBlock = null
                stmtExist.parentInputName = null
                stmtExist.parentOutputName = null
            } else {
                existingBlock.parentBlock = null
                existingBlock.parentInputName = null
                existingBlock.parentOutputName = null
                editor.rootBlocks.add(existingBlock)

                target.inputs[slotName] = newBlock
                newBlock.parentBlock = target
                newBlock.parentInputName = slotName
                newBlock.parentOutputName = null
            }
        } else {
            target.attachInput(slotName, newBlock)
        }
    }

    private fun attachBlockToOutputLogic(target: BlockModel, slotName: String, newBlock: BlockModel) {
        editor.rootBlocks.remove(newBlock)
        detachBlockInternal(newBlock)

        val existingBlock = target.outputs[slotName]
        if (existingBlock != null) {
            existingBlock.parentBlock = null
            existingBlock.parentInputName = null
            existingBlock.parentOutputName = null
            editor.rootBlocks.add(existingBlock)
        }

        target.attachOutput(slotName, newBlock)
    }

    private fun attachBlockAfterLogic(target: StatementBlock, newBlock: StatementBlock) {
        editor.rootBlocks.remove(newBlock)
        detachBlockInternal(newBlock)

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

    private fun insertBlockBeforeLogic(target: BlockModel, newBlock: StatementBlock) {
        editor.rootBlocks.remove(newBlock)
        detachBlockInternal(newBlock)

        val parentStatement = (target as? StatementBlock)?.parent
        val parentBlock = target.parentBlock

        if (parentStatement != null) {
            // Вставка между стейтментами
            parentStatement.next = newBlock
            newBlock.parent = parentStatement

            var tail = newBlock
            while (tail.next != null) tail = tail.next!!

            tail.next = target
            target.parent = tail
        } else if (parentBlock != null) {
            // Вставка первым элементом в инпут
            val slotName = target.parentInputName!!

            parentBlock.inputs[slotName] = newBlock
            newBlock.parentBlock = parentBlock
            newBlock.parentInputName = slotName

            target.parentBlock = null
            target.parentInputName = null

            var tail = newBlock
            while (tail.next != null) tail = tail.next!!

            tail.next = target as? StatementBlock
            if (target.isStatement()) target.parent = tail
        } else {
            editor.rootBlocks.remove(target)
            editor.rootBlocks.add(newBlock)

            var tail = newBlock
            while (tail.next != null) tail = tail.next!!

            tail.next = target as? StatementBlock
            if (target.isStatement()) target.parent = tail
        }
    }

    fun copySelected() {
        clipboard.clear()
        val topLevel = selectedBlocks.filter { !isParentSelected(it) }
        topLevel.forEach { original ->
            val copy = original.deepCopy(editor.provider)
            copyDisplayNames(original, copy)
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
            copyDisplayNames(originalCopy, freshCopy)
            freshCopy.positionX.set(freshCopy.positionX.value + pasteOffset)
            freshCopy.positionY.set(freshCopy.positionY.value + pasteOffset)

            newBlocks.add(freshCopy)
            selectedBlocks.add(freshCopy)
        }

        history.perform(AddBlocksAction(editor, newBlocks))
    }

    fun deleteSelected() {
        if (selectedBlocks.isEmpty()) return

        val blocksToDelete = selectedBlocks.toSet()
        val actions = mutableListOf<EditorAction>()

        blocksToDelete.forEach { block ->
            val oldState = captureConnectionState(block)
            val detachedState = ConnectionState(null, null, null, null, null, -1, block.positionX.value, block.positionY.value)
            actions.add(ConnectionAction(editor, block, oldState, detachedState))

            if (block is StatementBlock) {
                val next = block.next
                if (next != null && next !in blocksToDelete) {
                    val survivor = next
                    val survivorOldState = captureConnectionState(survivor)

                    val parentStmt = block.parent
                    val parentBlock = block.parentBlock
                    val parentInput = block.parentInputName
                    val parentOutput = block.parentOutputName

                    val survivorNewState = if (parentStmt != null && parentStmt !in blocksToDelete) {
                        ConnectionState(null, null, null, parentStmt, survivor.next, -1, 0f, 0f)
                    } else if (parentBlock != null && parentInput != null && parentBlock !in blocksToDelete) {
                        ConnectionState(parentBlock, parentInput, null, null, survivor.next, -1, 0f, 0f)
                    } else if (parentBlock != null && parentOutput != null && parentBlock !in blocksToDelete) {
                        ConnectionState(parentBlock, null, parentOutput, null, survivor.next, -1, 0f, 0f)
                    } else {
                        ConnectionState(null, null, null, null, survivor.next, editor.rootBlocks.indexOf(block), block.positionX.value, block.positionY.value)
                    }

                    actions.add(ConnectionAction(editor, survivor, survivorOldState, survivorNewState))
                }
            }
        }

        actions.add(RemoveBlocksAction(editor, blocksToDelete.toList()))

        if (actions.isNotEmpty()) {
            history.perform(CompoundAction(actions))
        }
        selectedBlocks.clear()
    }

    // --- Internal Logic ---

    fun captureConnectionState(block: BlockModel): ConnectionState {
        return ConnectionState(
            parentBlock = block.parentBlock,
            parentInputName = block.parentInputName,
            parentOutputName = block.parentOutputName,
            parentStatement = (block as? StatementBlock)?.parent,
            nextStatement = (block as? StatementBlock)?.next,
            indexInRoot = editor.rootBlocks.indexOf(block),
            positionX = block.positionX.value,
            positionY = block.positionY.value
        )
    }

    fun detachBlockInternal(block: BlockModel) {
        if (block.isStatement()) {
            block.parent?.let {
                if (it.next == block) it.next = null
            }
            block.parent = null
        }
        block.parentBlock?.inputs?.remove(block.parentInputName)
        block.parentBlock?.outputs?.remove(block.parentOutputName)
        block.parentBlock = null
        block.parentInputName = null
        block.parentOutputName = null
    }

    fun duplicateBlock(block: BlockModel, localPos: Vec2f) {
        val newBlock = cloneForDuplication(block)
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
            val copy = cloneForDuplication(original)

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

        val isSlotAction = action is DropAction.AttachToInput || action is DropAction.AttachToOutput
        val offsetX = if (!isSlotAction) -7.5f else 0f
        val offsetY = if (isSlotAction) -10f else 0f

        editor.triggerSnapEffect(SnapAnimation(logicalX + offsetX, logicalY + offsetY))
    }

    fun isValidDrop(source: BlockModel, action: DropAction): Boolean {
        if (source == action.target) return false
        if (isAncestorOf(source, action.target)) return false

        if (source is StartBlock) return false

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
            is DropAction.AttachToOutput -> {
                if (source is StatementBlock) return false
                val requiredType = action.target.outputTypes[action.outputName] ?: return false
                val acceptedType = (source as? ru.hollowhorizon.hollowengine.common.codeblocks.execution.OutputConsumer)?.acceptedType
                    ?: return false
                requiredType.accepts(acceptedType) || acceptedType == AnyType
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

    private fun cloneForDuplication(root: BlockModel): BlockModel {
        val clone = root.deepCopy(editor.provider)
        copyDisplayNames(root, clone)
        stripExternalStatementContinuation(clone)
        return clone
    }

    private fun stripExternalStatementContinuation(copy: BlockModel) {
        val statementCopy = copy as? StatementBlock ?: return
        statementCopy.next?.parent = null
        statementCopy.next = null
    }

    private fun copyDisplayNames(original: BlockModel, copy: BlockModel) {
        copy.displayName = original.displayName

        original.inputs.forEach { (slotName, originalChild) ->
            val copiedChild = copy.inputs[slotName] ?: return@forEach
            copyDisplayNames(originalChild, copiedChild)
        }
        original.outputs.forEach { (slotName, originalChild) ->
            val copiedChild = copy.outputs[slotName] ?: return@forEach
            copyDisplayNames(originalChild, copiedChild)
        }

        val originalStatement = original as? StatementBlock
        val copiedStatement = copy as? StatementBlock
        if (originalStatement != null && copiedStatement != null) {
            val originalNext = originalStatement.next
            val copiedNext = copiedStatement.next
            if (originalNext != null && copiedNext != null) {
                copyDisplayNames(originalNext, copiedNext)
            }
        }
    }

    fun resetAction() {
        potentialAction = null
    }

    fun selectAll() {
        selectedBlocks.clear()
        selectedBlocks.addAll(editor.rootBlocks.flatMap { it.walk() })
    }
}


