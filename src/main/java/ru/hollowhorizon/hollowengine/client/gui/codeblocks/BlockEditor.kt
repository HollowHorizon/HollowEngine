package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock

sealed class DropAction(val target: CodeBlock) {
    class InsertBefore(target: CodeBlock) : DropAction(target)
    class AttachAfter(target: CodeBlock) : DropAction(target)

    // Для корректного сравнения в списках
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DropAction) return false
        if (this::class != other::class) return false
        return target == other.target
    }
    override fun hashCode(): Int = target.hashCode()
}

class BlockEditor {
    val rootBlocks = mutableStateListOf<CodeBlock>()

    var draggingBlock: CodeBlock? = null
    val dragLocalOffset = MutableVec2f()

    var potentialAction: DropAction? = null

    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()

    // Константы для размеров зон, чтобы они совпадали у Блока и у Призрака
    private val TOP_ZONE_HEIGHT = Dp(40f)
    private val TOP_ZONE_OFFSET = Dp(-30f) // Сильный вынос вверх

    private val BOTTOM_ZONE_HEIGHT = Dp(30f)
    private val BOTTOM_ZONE_OFFSET = Dp(-10f)

    init {
        // Тестовые данные
        val b1 = PrintBlock("Hello").apply { setPosition(100f, 100f) }
        val b2 = PrintBlock("My Dear")
        b1.next = b2
        b2.parent = b1
        rootBlocks.add(b1)

        rootBlocks.add(PrintBlock("World").apply { setPosition(100f, 250f) })
    }

    fun UiScope.EditorLayout() {
        dropTargets.clear()

        ScrollPane(rememberScrollState()) {
            modifier.layout(CellLayout)
            modifier.width(Grow.Std).height(Grow.Std)
            modifier.onClick { potentialAction = null }

            rootBlocks.use().forEach { block ->
                renderBlock(block, isRoot = true)
            }
        }
    }

    private fun UiScope.renderBlock(block: CodeBlock, isRoot: Boolean) {
        Column {
            modifier.zLayer(if (draggingBlock == block) UiSurface.LAYER_FLOATING else UiSurface.LAYER_DEFAULT)

            if (isRoot) {
                modifier.margin(start = Dp.fromPx(block.positionX.use()), top = Dp.fromPx(block.positionY.use()))
            }

            val action = potentialAction
            val isDraggingOther = draggingBlock != null && draggingBlock != block

            // --- 1. ПРЕВЬЮ ВСТАВКИ СВЕРХУ (Insert Before) ---
            if (isDraggingOther && action is DropAction.InsertBefore && action.target == block) {
                Box {
                    modifier.layout(CellLayout) // Чтобы наложить ловушку поверх призрака

                    // А. Сам призрак
                    renderGhostChain(draggingBlock!!)

                    // Б. Страховочная зона (Safety Net)
                    // Она должна торчать вверх ТАК ЖЕ, как зона у реального блока,
                    // чтобы поймать мышь, когда реальный блок уедет вниз.
                    Box {
                        modifier
                            .width(Grow.Std)
                            .height(TOP_ZONE_HEIGHT)
                            .margin(top = TOP_ZONE_OFFSET)
                            .alignY(AlignmentY.Top)

                        dropTargets.add(action to uiNode)
                    }

                    // В. Тело призрака тоже является зоной
                    dropTargets.add(action to uiNode)
                }
            }

            // --- 2. БЛОК И ЕГО ЗОНЫ ---
            Box {
                modifier.layout(CellLayout)

                // Визуал блока
                BlockVisual(block) {
                    modifier
                        .onDragStart { ev -> handleDragStart(block, ev) }
                        .onDrag { ev -> handleDrag(block, ev) }
                        .onDragEnd { handleDragEnd(block) }
                }

                // Зона сверху
                Box {
                    modifier
                        .width(Grow.Std)
                        .height(TOP_ZONE_HEIGHT)
                        .alignY(AlignmentY.Top)
                        .margin(top = TOP_ZONE_OFFSET)
                    //.border(RectBorder(Color.GREEN, 1.dp)) // Debug

                    dropTargets.add(DropAction.InsertBefore(block) to uiNode)
                }

                // Зона снизу
                Box {
                    modifier
                        .width(Grow.Std)
                        .height(BOTTOM_ZONE_HEIGHT)
                        .alignY(AlignmentY.Bottom)
                        .margin(bottom = BOTTOM_ZONE_OFFSET)
                    //.border(RectBorder(Color.BLUE, 1.dp)) // Debug

                    dropTargets.add(DropAction.AttachAfter(block) to uiNode)
                }
            }

            // --- 3. ПРЕВЬЮ ВСТАВКИ СНИЗУ (Attach After) ---
            if (isDraggingOther && action is DropAction.AttachAfter && action.target == block) {
                Box {
                    modifier.layout(CellLayout)

                    renderGhostChain(draggingBlock!!)

                    // Страховочная зона для низа
                    Box {
                        modifier
                            .width(Grow.Std)
                            .height(BOTTOM_ZONE_HEIGHT)
                            .margin(bottom = BOTTOM_ZONE_OFFSET)
                            .alignY(AlignmentY.Bottom)

                        dropTargets.add(action to uiNode)
                    }

                    dropTargets.add(action to uiNode)
                }
            }

            // --- 4. РЕКУРСИЯ ---
            block.next?.let { nextBlock ->
                renderBlock(nextBlock, isRoot = false)
            }
        }
    }

    private fun UiScope.renderGhostChain(head: CodeBlock) {
        Column {
            modifier.background(null)
            var current: CodeBlock? = head
            while (current != null) {
                BlockVisual(current, isGhost = true)
                current = current.next
            }
        }
    }

    private fun UiScope.BlockVisual(block: CodeBlock, isGhost: Boolean = false, blockModifier: UiModifier.() -> Unit = {}) {
        Column {
            modifier
                .width(FitContent)
                .apply(blockModifier)

            val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color
            modifier.background(ScratchBlockBackground(bgColor, hasTopNotch = true, hasBottomNotch = true))

            Box { modifier.height(5.dp).width(Grow.Std) }
            Row {
                modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                if (isGhost) with(block) { composeContent() } else with(block) { composeContent() }
            }
            Box { modifier.height(5.dp).width(Grow.Std) }
        }
    }

    // --- ОБРАБОТЧИКИ ---

    private fun UiScope.handleDragStart(block: CodeBlock, ev: PointerEvent) {
        draggingBlock = block
        dragLocalOffset.set(ev.position.x, ev.position.y)

        if (!rootBlocks.contains(block)) {
            val screenPos = ev.screenPosition
            detachBlock(block)

            val scrollPane = uiNode.findParentOfType<ScrollPaneNode>()
            if (scrollPane != null) {
                val localInScroll = MutableVec2f()
                scrollPane.toLocal(screenPos, localInScroll)
                block.setPosition(localInScroll.x - dragLocalOffset.x, localInScroll.y - dragLocalOffset.y)
            }
        }
    }

    private fun UiScope.handleDrag(block: CodeBlock, ev: PointerEvent) {
        if (draggingBlock == block) {
            val scrollPane = uiNode.findParentOfType<ScrollPaneNode>() ?: return
            val localPos = MutableVec2f()
            scrollPane.toLocal(ev.screenPosition, localPos)
            block.setPosition(localPos.x - dragLocalOffset.x, localPos.y - dragLocalOffset.y)

            // Hit Test Sticky Logic
            // 1. Проверяем текущее действие (включая зоны у призраков)
            val currentActionTarget = dropTargets.find { (action, node) ->
                potentialAction != null &&
                        action == potentialAction && // Используем equals переопределенный в DropAction
                        node.isInBounds(ev.screenPosition)
            }

            if (currentActionTarget != null) {
                // Остаемся на текущем действии
            } else {
                // 2. Ищем новую цель
                val hitTarget = dropTargets.find { (action, node) ->
                    node.isInBounds(ev.screenPosition) &&
                            block != action.target &&
                            !isChildOf(action.target, block)
                }
                potentialAction = hitTarget?.first
            }

            surface.triggerUpdate()
        }
    }

    private fun handleDragEnd(block: CodeBlock) {
        potentialAction?.let { action ->
            when (action) {
                is DropAction.InsertBefore -> insertBlockBefore(action.target, block)
                is DropAction.AttachAfter -> attachBlockAfter(action.target, block)
            }
        }
        draggingBlock = null
        potentialAction = null
    }

    // --- ЛОГИКА ДЕРЕВА ---

    private fun detachBlock(block: CodeBlock) {
        block.parent?.let { parent ->
            if (parent.next == block) parent.next = null
            block.parent = null
        }
        if (!rootBlocks.contains(block)) rootBlocks.add(block)
    }

    private fun attachBlockAfter(target: CodeBlock, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
        val oldNext = target.next
        target.next = newBlock
        newBlock.parent = target

        var newBlockTail = newBlock
        while (newBlockTail.next != null) newBlockTail = newBlockTail.next!!

        if (oldNext != null) {
            newBlockTail.next = oldNext
            oldNext.parent = newBlockTail
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

        var newBlockTail = newBlock
        while (newBlockTail.next != null) newBlockTail = newBlockTail.next!!

        newBlockTail.next = target
        target.parent = newBlockTail
    }

    private fun isChildOf(parent: CodeBlock, possibleChild: CodeBlock): Boolean {
        var curr = parent.parent
        while (curr != null) {
            if (curr == possibleChild) return true
            curr = curr.parent
        }
        return false
    }
}

private inline fun <reified T> UiNode.findParentOfType(): T? {
    var current = this
    while (current !is T) {
        current = current.parent ?: break
    }
    return current as? T
}