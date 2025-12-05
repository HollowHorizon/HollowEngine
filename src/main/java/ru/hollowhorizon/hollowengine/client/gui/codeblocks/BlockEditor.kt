package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.set
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.popup.ItemPopupMenu
import ru.hollowhorizon.hollowengine.common.codeblocks.*

sealed interface DropAction {
    val target: CodeBlock

    data class InsertBefore(override val target: CodeBlock) : DropAction
    data class AttachAfter(override val target: CodeBlock) : DropAction
    data class AttachToInput(override val target: CodeBlock, val inputName: String, val isStatementSlot: Boolean) :
        DropAction
}

class BlockEditor(val provider: BlockProvider, val notifyChanged: () -> Unit) {
    val rootBlocks = mutableStateListOf<CodeBlock>()
    var draggingBlock: CodeBlock? = null
    val dragStartOffset = MutableVec2f()
    var potentialAction: DropAction? = null
    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()
    private val tmpLocal = MutableVec2f()

    private val removalPopup = AutoPopup()

    private val snapAnimations = mutableListOf<SnapAnimation>()

    private val creationPopup = ItemPopupMenu<Vec2f>("BlockCreationMenu")

    companion object {
        const val C_BLOCK_SPINE_WIDTH = 20f
        val DROP_SENSOR_HEIGHT = Dp(20f)
    }

    fun UiScope.EditorLayout(body: ScrollPaneScope.() -> Unit) {
        dropTargets.clear()

        val state = rememberScrollState()

        Box {
            modifier
                .width(Grow.Std)
                .height(Grow.Std)
                .backgroundColor(colors.backgroundVariant)
                .onWheelX {
                    state.scrollDpX(it.pointer.scroll.x * -20f)
                }
                .onWheelY {
                    state.scrollDpY(it.pointer.scroll.y * -50f)
                }
                .onClick { createBlocksMenu(it) }

            modifier.onDrag {
                val delta = it.pointer.delta
                if (delta.x != 0f) {
                    state.scrollDpX(Dp.fromPx(-delta.x).value)
                }
                if (delta.y != 0f) {
                    state.scrollDpY(Dp.fromPx(-delta.y).value)
                }
            }

            ScrollPane(state) {
                modifier.layout(CellLayout)
                    .padding(sizes.largeGap)
                modifier.onClick {
                    createBlocksMenu(it)
                    potentialAction = null
                }

                rootBlocks.use().forEach { block -> renderBlockRecursively(block) }
                body()

                renderSnapAnimations()
            }

            VerticalScrollbar {
                modifier
                    .width(sizes.smallGap).margin(sizes.smallGap)
                    .colors(
                        trackColor = EditorTheme.Scrollbar.trackColor,
                        trackHoverColor = EditorTheme.Scrollbar.trackHover,
                        color = EditorTheme.Scrollbar.color,
                        hoverColor = EditorTheme.Scrollbar.hoverColor,
                    )
                    .relativeBarPos(state.relativeBarPosY)
                    .relativeBarLen(state.relativeBarLenY)
                    .onChange { state.scrollRelativeY(it) }
            }
            HorizontalScrollbar {
                modifier
                    .height(sizes.smallGap).margin(sizes.smallGap)
                    .colors(
                        trackColor = EditorTheme.Scrollbar.trackColor,
                        trackHoverColor = EditorTheme.Scrollbar.trackHover,
                        color = EditorTheme.Scrollbar.color,
                        hoverColor = EditorTheme.Scrollbar.hoverColor,
                    )
                    .relativeBarPos(state.relativeBarPosX)
                    .relativeBarLen(state.relativeBarLenX)
                    .onChange { state.scrollRelativeX(it) }
            }

            removalPopup()
            creationPopup()
        }
    }

    private fun UiScope.createBlocksMenu(event: PointerEvent) {
        if (event.isRightClick) {
            val rootMenu = buildMenuFromProvider(provider, uiNode)
            creationPopup.show(Vec2f(event.screenPosition), rootMenu, Vec2f(event.screenPosition))
        }
    }

    private fun UiScope.renderBlockRecursively(block: CodeBlock, isGhost: Boolean = false) {
        Column {

            val isRoot = rootBlocks.contains(block)
            modifier.width(if (isRoot) FitContent else Grow.Std)
            val isDragging = draggingBlock == block

            if (isRoot) {
                modifier.zLayer(if (isDragging) UiSurface.LAYER_FLOATING else UiSurface.LAYER_DEFAULT)
                modifier.margin(start = Dp.fromPx(block.positionX.use()), top = Dp.fromPx(block.positionY.use()))
            }

            Column {
                modifier.width(Grow.Std)

                if (!block.isExpression && potentialAction is DropAction.InsertBefore && potentialAction?.target == block && !isDragging) {
                    Column(Grow.Std) {
                        GhostPlaceholder(false)
                        addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
                    }
                }

                Box {
                    modifier.width(Grow.Std)

                    Column {
                        modifier.width(Grow.Std)

                        val isHovered = remember { mutableStateOf(false) }

                        BlockHeaderVisual(isHovered, block, isGhost) {
                            modifier
                                .onDragStart { ev -> handleDragStart(block, ev) }
                                .onDrag { ev -> handleDrag(block, ev) }
                                .onDragEnd { handleDragEnd(block) }
                                .onClick {
                                    onBlockRightClick(block, it)
                                }
                                .onEnter {
                                    isHovered.set(true)
                                }
                                .onHover {
                                    PointerInput.cursorShape = CursorShape.HAND
                                }
                                .onExit {
                                    isHovered.set(false)
                                }
                        }

                        with(InputSlotScope(this, block, isHovered.use(), isGhost)) {
                            with(block) { composeBody() }
                        }

                        if (block is ContainerBlock) {
                            Box {
                                modifier.height(20.dp).width(Grow.Std)
                                val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color
                                val color by animateColorAsState(
                                    if (isHovered.value) bgColor else bgColor.mulRgb(0.9f), tween(
                                        0.2f,
                                        Easing.quadRev
                                    )
                                )
                                modifier.background(ContainerFooterBackground(color))

                                if (!isDragging) {
                                    Box {
                                        modifier.width(Grow.Std).alignY(AlignmentY.Bottom)
                                            .height(DROP_SENSOR_HEIGHT)
                                        addDropTargetOnce(DropAction.AttachAfter(block), uiNode)

                                    }
                                }
                            }
                        }
                    }

                    if (!isDragging && !block.isExpression) {
                        Box {
                            modifier
                                .width(Grow.Std).height(DROP_SENSOR_HEIGHT)
                                .alignY(AlignmentY.Top)

                            addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
                        }

                        if (block !is ContainerBlock) {
                            Box {
                                modifier
                                    .width(Grow.Std).height(DROP_SENSOR_HEIGHT)
                                    .alignY(AlignmentY.Bottom)

                                addDropTargetOnce(DropAction.AttachAfter(block), uiNode)
                            }
                        }
                    }
                }

                if (!block.isExpression) {
                    val action = potentialAction
                    if (action is DropAction.AttachAfter && action.target == block && !isDragging) {
                        GhostPlaceholder(false)
                    }

                    block.next?.let { next -> renderBlockRecursively(next, isGhost) }
                }
            }
        }
    }

    private fun onBlockRightClick(
        block: CodeBlock,
        event: PointerEvent,
    ) {
        removalPopup.popupContent = {
            Column {
                Button("Удалить") {
                    modifier.onClick {
                        removalPopup.hide()
                        if (it.isLeftClick) removeBlock(block)
                    }
                }
            }
        }
        if (event.isRightClick) {
            removalPopup.show(Vec2f(event.screenPosition))
        }
    }

    private fun UiScope.BlockHeaderVisual(
        isHovered: MutableStateValue<Boolean>,
        block: CodeBlock,
        isGhost: Boolean,
        blockModifier: UiModifier.() -> Unit,
    ) {
        Box {
            val marginLeft = if (block.isExpression) Dp.fromPx(PuzzleShapes.TAB_WIDTH) else 0.dp
            modifier.width(Grow.Std).margin(start = marginLeft).apply(blockModifier)

            val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color
            val color by animateColorAsState(
                if (isHovered.use()) bgColor else bgColor.mulRgb(0.9f),
                tween(0.2f, Easing.quadRev)
            )
            val isContainer = block is ContainerBlock

            modifier.background(
                ScratchBlockBackground(
                    color = color,
                    isExpression = block.isExpression,
                    hasNext = !block.isExpression,
                    isContainerHeader = isContainer
                )
            )

            with(block) {
                // Передаем модификаторы, которые отвечают за фон и перетаскивание
                composeHeaderLayout(isHovered.use(), isGhost) {
                    modifier.width(Grow.Std).margin(start = marginLeft).apply(blockModifier)


                }
            }
        }
    }

    inner class InputSlotScope(
        uiScope: UiScope,
        val parentBlock: CodeBlock,
        val isHovered: Boolean,
        val isGhost: Boolean,
    ) : UiScope by uiScope {
        fun notifyChanged() {
            this@BlockEditor.notifyChanged()
        }

        fun UiScope.InputSlot(name: String, type: ExpressionType) {
            parentBlock.inputTypes[name] = type
            val attached = parentBlock.inputs[name]
            val action = potentialAction
            val isTargeted =
                action is DropAction.AttachToInput && action.target == parentBlock && action.inputName == name && !action.isStatementSlot

            Box {
                modifier.align(AlignmentX.End, AlignmentY.Center).margin(horizontal = sizes.gap)

                if (attached != null) {
                    if (draggingBlock == attached) EmptySlotVisual(isTargeted)
                    else {
                        renderBlockRecursively(attached)
                        if (isTargeted) modifier.border(RectBorder(Color.WHITE, 2.dp))
                    }
                } else {
                    addDropTargetOnce(DropAction.AttachToInput(parentBlock, name, false), uiNode)

                    if (isTargeted && draggingBlock?.isExpression == true) GhostPlaceholder(true)
                    else EmptySlotVisual(false)
                }
            }
        }

        fun UiScope.BodySlot(name: String) {
            val attached = parentBlock.inputs[name]
            val action = potentialAction
            val isTargeted =
                action is DropAction.AttachToInput && action.target == parentBlock && action.inputName == name && action.isStatementSlot

            Row {
                modifier.width(Grow.Std)

                val bgColor = if (isGhost) parentBlock.color.withAlpha(0.5f) else parentBlock.color
                val color by animateColorAsState(
                    if (isHovered) bgColor else bgColor.mulRgb(0.9f),
                    tween(0.2f, Easing.quadRev)
                )
                Box {
                    modifier
                        .width(Dp.fromPx(C_BLOCK_SPINE_WIDTH) - sizes.smallGap * 0.5f)
                        .height(Grow.Std)
                        .background(RectBackground(color))
                        .border(RectBorder(color.mix(Color.BLACK, 0.2f), Dp.fromPx(2f)))
                }

                Box { modifier.size(sizes.smallGap * 0.5f, Grow.Std) }

                Column {
                    modifier.width(Grow.Std)

                    Box {
                        if (attached == null) {
                            modifier.height(30.dp).width(100.dp)
                            if (isTargeted) modifier.background(
                                ScratchBlockBackground(
                                    Color.WHITE.withAlpha(0.2f),
                                    false,
                                    true
                                )
                            )
                        } else {
                            modifier.height(sizes.smallGap).width(Grow.Std)
                        }
                        addDropTargetOnce(DropAction.AttachToInput(parentBlock, name, true), uiNode)
                    }

                    if (attached != null) {
                        if (draggingBlock == attached) {
                            Box { modifier.size(50.dp, 20.dp).background(RectBackground(Color.WHITE.withAlpha(0.1f))) }
                        } else {
                            renderBlockRecursively(attached, isGhost)
                        }
                    }

                    Box {
                        modifier.height(sizes.smallGap).width(Grow.Std)
                    }
                }
            }
        }

        fun UiScope.SectionSeparator(label: String) {
            val bgColor = if (isGhost) parentBlock.color.withAlpha(0.5f) else parentBlock.color
            val color by animateColorAsState(
                if (isHovered) bgColor else bgColor.mulRgb(0.9f),
                tween(0.2f, Easing.quadRev)
            )
            Row {
                modifier.width(Grow.Std).height(FitContent)
                Box {
                    modifier.width(Grow.Std).height(30.dp)
                    modifier.background(ContainerMiddleBackground(color))
                    Text(label) {
                        modifier.alignY(AlignmentY.Center).margin(start = Dp.fromPx(C_BLOCK_SPINE_WIDTH + 10f))
                            .textColor(Color.WHITE)
                    }
                }
            }
        }

        private fun UiScope.EmptySlotVisual(highlight: Boolean) {
            Box {
                modifier.size(40.dp, 30.dp)
                modifier.background(SlotBackground(parentBlock.color.mix(Color.BLACK, 0.3f), highlight))
                if (highlight) modifier.border(RectBorder(Color.WHITE, 2.dp))
            }
        }
    }

    private fun UiScope.GhostPlaceholder(isExpression: Boolean) {
        Box(Grow.Std) {
            if (isExpression) modifier.size(40.dp, 30.dp)
            else modifier.height(40.dp).width(100.dp)
            modifier.background(ScratchBlockBackground(Color.WHITE.withAlpha(0.2f), isExpression, !isExpression))
            if (!isExpression) modifier.margin(vertical = 2.dp)
        }
    }

    private fun attachBlockToInput(target: CodeBlock, slotName: String, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
        val existingBlock = target.inputs[slotName]
        if (existingBlock != null) {
            target.inputs[slotName] = newBlock
            newBlock.parentBlock = target
            newBlock.parentInputName = slotName
            newBlock.parent = null

            var tail = newBlock
            while (tail.next != null) tail = tail.next!!
            tail.next = existingBlock
            existingBlock.parent = tail
            existingBlock.parentBlock = null
            existingBlock.parentInputName = null
        } else {
            target.attachInput(slotName, newBlock)
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

    private fun removeBlock(block: CodeBlock) {
        val nextBlock = block.next

        block.parent?.let { parent ->
            parent.next = nextBlock
            nextBlock?.parent = parent
        } ?: block.parentBlock?.let { parentContainer ->
            val slotName = block.parentInputName ?: return@let

            if (nextBlock != null) {
                parentContainer.inputs[slotName] = nextBlock
                nextBlock.parentBlock = parentContainer
                nextBlock.parentInputName = slotName
                nextBlock.parent = null
            } else {
                parentContainer.inputs.remove(slotName)
            }
        } ?: run {
            rootBlocks.remove(block)
            // Если у удаленного блока был хвост, он становится новым корневым блоком
            if (nextBlock != null) {
                rootBlocks.add(nextBlock)
                // Сохраняем позицию, чтобы хвост не прыгнул в (0,0)
                nextBlock.setPosition(block.positionX.value, block.positionY.value)
                nextBlock.parent = null
            }
        }

        block.parent = null
        block.parentBlock = null
        block.parentInputName = null
        block.next = null

        notifyChanged()
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

        return when(action) {
            is DropAction.InsertBefore, is DropAction.AttachAfter -> !source.isExpression
            is DropAction.AttachToInput -> {
                if(source is ExpressionBlock) {
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

    private fun handleDragEnd(block: CodeBlock) {
        potentialAction?.let { action ->
            triggerSnapEffect(action)
            when (action) {
                is DropAction.InsertBefore -> insertBlockBefore(action.target, block)
                is DropAction.AttachAfter -> attachBlockAfter(action.target, block)
                is DropAction.AttachToInput -> attachBlockToInput(action.target, action.inputName, block)
            }
        }
        draggingBlock = null
        potentialAction = null
        notifyChanged()
    }

    private fun triggerSnapEffect(action: DropAction) {
        if (action is DropAction.InsertBefore) return
        val targetNode = dropTargets.find { it.first == action }?.second ?: return

        val centerX = targetNode.leftPx
        val centerY = targetNode.topPx

        val scrollPane = targetNode.findParentOfType<ScrollPaneNode>() ?: return

        scrollPane.toLocal(Vec2f(centerX, centerY), tmpLocal)

        val offsetX = if (action !is DropAction.AttachToInput) -7.5f else 0f
        val offsetY = if (action is DropAction.AttachToInput) -10f else 0f

        snapAnimations.add(SnapAnimation(tmpLocal.x + offsetX, tmpLocal.y + offsetY))
    }

    private fun attachBlockAfter(target: CodeBlock, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
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

    private fun insertBlockBefore(target: CodeBlock, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
        val parent = target.parent
        val parentBlock = target.parentBlock
        if (parent != null) {
            parent.next = newBlock
            newBlock.parent = parent
        } else if (parentBlock != null) {
            val slotName = target.parentInputName!!
            parentBlock.inputs[slotName] = newBlock
            newBlock.parentBlock = parentBlock
            newBlock.parentInputName = slotName
            target.parentBlock = null
            target.parentInputName = null
        } else {
            rootBlocks.remove(target)
            rootBlocks.add(newBlock)
            newBlock.parent = null
        }
        var tail = newBlock
        while (tail.next != null) tail = tail.next!!
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

    private fun UiScope.renderSnapAnimations() {
        snapAnimations.removeIf { it.isFinished }



        Box {
            modifier.width(Grow.Std).height(Grow.Std)
            modifier.background(UiRenderer { node ->
                node.apply {
                    val drawList = getPlainBuilder(UiSurface.LAYER_FLOATING)

                    snapAnimations.forEach { anim ->
                        val p = anim.animator.progressAndUse()

                        val scale = 10f + p * 30f

                        val alpha = Easing.quad(1f - p).coerceIn(0f, 1f)

                        drawList.configured(Color.WHITE.withAlpha(alpha)) {
                            translate(anim.x, anim.y, 0f)
                            scale(scale, scale, 1f)

                            // Используем нашу "ручную" геометрию
                            val i0 = geometry.numVertices
                            for (v in RingGeometry.vertices) {
                                vertex { it.position.set(v) }
                            }
                            for (i in RingGeometry.indices) {
                                geometry.addIndex(i0 + i)
                            }
                        }
                    }
                }
            })
        }
    }
}

inline fun <reified T> UiNode.findParentOfType(): T? {
    var current: UiNode? = this
    while (current != null && current !is T) current = current.parent
    return current as? T
}