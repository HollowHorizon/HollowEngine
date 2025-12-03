package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock

// DropAction как sealed interface + data classes => equals/hashCode корректны автоматически
sealed interface DropAction {
    val target: CodeBlock
    data class InsertBefore(override val target: CodeBlock) : DropAction
    data class AttachAfter(override val target: CodeBlock) : DropAction
}

class BlockEditor {
    val rootBlocks = mutableStateListOf<CodeBlock>()

    var draggingBlock: CodeBlock? = null
    val dragLocalOffset = MutableVec2f()

    var potentialAction: DropAction? = null

    // Храним уникальные (action, node), чтобы избежать дубликатов
    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()

    // Временный вектор для перерасчётов в onDrag — один объект, переиспользуется
    private val tmpLocal = MutableVec2f()

    companion object {
        // Константы размеров зон (camelCase)
        private val topZoneHeight = Dp(40f)
        private val topZoneOffset = Dp(-30f)

        private val bottomZoneHeight = Dp(30f)
        private val bottomZoneOffset = Dp(-10f)
    }

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
        // каждый кадр/рендер заново собираем цели
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

            // --- PREVIEW INSERT BEFORE ---
            if (isDraggingOther && action is DropAction.InsertBefore && action.target == block) {
                Box {
                    modifier.layout(CellLayout)

                    // ghost
                    renderGhostChain(draggingBlock!!)

                    // safety zone (top)
                    Box {
                        modifier
                            .width(Grow.Std)
                            .height(topZoneHeight)
                            .margin(top = topZoneOffset)
                            .alignY(AlignmentY.Top)

                        addDropTargetOnce(action, uiNode)
                    }

                    // ghost body area as target too
                    addDropTargetOnce(action, uiNode)
                }
            }

            // --- REAL BLOCK + ZONES ---
            Box {
                modifier.layout(CellLayout)

                // visual + drag handlers
                BlockVisual(block) {
                    modifier
                        .onDragStart { ev -> handleDragStart(block, ev) }
                        .onDrag { ev -> handleDrag(block, ev) }
                        .onDragEnd { handleDragEnd(block) }
                }

                // top insertion zone
                Box {
                    modifier
                        .width(Grow.Std)
                        .height(topZoneHeight)
                        .alignY(AlignmentY.Top)
                        .margin(top = topZoneOffset)

                    addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
                }

                // bottom attach zone
                Box {
                    modifier
                        .width(Grow.Std)
                        .height(bottomZoneHeight)
                        .alignY(AlignmentY.Bottom)
                        .margin(bottom = bottomZoneOffset)

                    addDropTargetOnce(DropAction.AttachAfter(block), uiNode)
                }
            }

            // --- PREVIEW ATTACH AFTER ---
            if (isDraggingOther && action is DropAction.AttachAfter && action.target == block) {
                Box {
                    modifier.layout(CellLayout)

                    renderGhostChain(draggingBlock!!)

                    Box {
                        modifier
                            .width(Grow.Std)
                            .height(bottomZoneHeight)
                            .margin(bottom = bottomZoneOffset)
                            .alignY(AlignmentY.Bottom)

                        addDropTargetOnce(action, uiNode)
                    }

                    addDropTargetOnce(action, uiNode)
                }
            }

            // recurse
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
                // composeContent() вызываем напрямую; if-else не нужен (в обоих случаях одно и то же)
                with(block) { composeContent() }
            }
            Box { modifier.height(5.dp).width(Grow.Std) }
        }
    }

    // --- Drag handlers ---

    private fun UiScope.handleDragStart(block: CodeBlock, ev: PointerEvent) {
        draggingBlock = block
        dragLocalOffset.set(ev.position.x, ev.position.y)

        if (!rootBlocks.contains(block)) {
            val screenPos = ev.screenPosition
            detachBlock(block)

            val scrollPane = uiNode.findParentOfType<ScrollPaneNode>()
            if (scrollPane != null) {
                // переиспользуем tmpLocal
                scrollPane.toLocal(screenPos, tmpLocal)
                block.setPosition(tmpLocal.x - dragLocalOffset.x, tmpLocal.y - dragLocalOffset.y)
            }
        }
    }

    private fun UiScope.handleDrag(block: CodeBlock, ev: PointerEvent) {
        if (draggingBlock != block) return

        val scrollPane = uiNode.findParentOfType<ScrollPaneNode>() ?: return
        scrollPane.toLocal(ev.screenPosition, tmpLocal)
        block.setPosition(tmpLocal.x - dragLocalOffset.x, tmpLocal.y - dragLocalOffset.y)

        // Sticky hit-test: сначала проверяем остаёмся ли мы в той же цели (быстрее)
        val stayingOnSameAction = potentialAction?.let { currentAction ->
            dropTargets.any { (action, node) ->
                action == currentAction && node.isInBounds(ev.screenPosition)
            }
        } ?: false

        if (!stayingOnSameAction) {
            // ищем новую цель (и фильтруем некорректные действия)
            val hit = dropTargets.find { (action, node) ->
                node.isInBounds(ev.screenPosition) &&
                        block != action.target && // не на себя
                        !isAncestorOf(action.target, block) // не на своего ребёнка/потомка
            }
            potentialAction = hit?.first
        }

        surface.triggerUpdate()
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

    // --- Tree manipulation ---

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

        // найти хвост newBlock
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

        if (parent != null) {
            parent.next = newBlock
            newBlock.parent = parent
        } else {
            // target был корнем — заменяем в списке rootBlocks
            rootBlocks.remove(target)
            rootBlocks.add(newBlock)
            newBlock.parent = null
        }

        // присоединить хвост newBlock к target
        var tail = newBlock
        while (tail.next != null) tail = tail.next!!
        tail.next = target
        target.parent = tail
    }

    private fun isAncestorOf(parent: CodeBlock, possibleChild: CodeBlock): Boolean {
        // Возвращает true, если possibleChild — один из предков parent (поднимаемся вверх от parent)
        var curr = parent.parent
        while (curr != null) {
            if (curr == possibleChild) return true
            curr = curr.parent
        }
        return false
    }

    // --- Утилиты для dropTargets ---

    private fun addDropTargetOnce(action: DropAction, node: UiNode) {
        // избегаем дубликатов — обычно node identity + action identity
        val pair = action to node
        if (!dropTargets.contains(pair)) dropTargets.add(pair)
    }
}

/** Полезное расширение: безопасное и компактное нахождение родителя нужного типа */
private inline fun <reified T> UiNode.findParentOfType(): T? {
    var current: UiNode? = this
    while (current != null && current !is T) {
        current = current.parent
    }
    return current as? T
}
