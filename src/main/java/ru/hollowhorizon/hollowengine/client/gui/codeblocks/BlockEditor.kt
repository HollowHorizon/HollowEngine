package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.IfBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.RepeatBlock

// --- Updated DropAction ---
sealed interface DropAction {
    val target: CodeBlock
    data class InsertBefore(override val target: CodeBlock) : DropAction
    data class AttachAfter(override val target: CodeBlock) : DropAction
    data class AttachToInput(override val target: CodeBlock, val inputName: String, val isStatementSlot: Boolean) : DropAction
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
        const val C_BLOCK_SPINE_WIDTH = 15f // Ширина левой полоски у C-блока
    }

    init {
        // Тестовые блоки
        rootBlocks.add(PrintBlock("Start").apply { setPosition(50f, 50f) })
        rootBlocks.add(IfBlock().apply { setPosition(50f, 150f) })
    }

    fun UiScope.EditorLayout() {
        dropTargets.clear()
        ScrollPane(rememberScrollState()) {
            modifier.layout(CellLayout).width(Grow.Std).height(Grow.Std)
            modifier.onClick { potentialAction = null }

            rootBlocks.use().forEach { block -> renderBlockRecursively(block) }
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

            // Drop Target: Вставить ДО текущего блока
            if (!block.isExpression && draggingBlock != null && !isDragging) {
                // Показываем плейсхолдер, если наведены
                val action = potentialAction
                if (action is DropAction.InsertBefore && action.target == block) {
                    GhostPlaceholder(false)
                    addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
                } else if (potentialAction == null || potentialAction !is DropAction.InsertBefore) {
                    // Невидимка для детекции
                    Box {
                        modifier.height(10.dp).width(Grow.Std) // Зона чувствительности сверху
                        addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
                    }
                }
            }

            // Сам блок
            Column {
                modifier.width(FitContent)
                // Основной фон и хедер
                BlockHeaderVisual(block, isGhost) {
                    modifier
                        .onDragStart { ev -> handleDragStart(block, ev) }
                        .onDrag { ev -> handleDrag(block, ev) }
                        .onDragEnd { handleDragEnd(block) }
                }

                // Тело блока (если есть)
                val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color

                // Рендерим тело через scope
                // Важно: мы передаем управление в block.composeBody, который накидает BodySlot-ов
                Column {
                    modifier.width(FitContent) // Растягиваемся по ширине контента
                    with(InputSlotScope(this, block, isGhost)) {
                        with(block) { composeBody() }
                    }
                }

                // Закрывашка (Footer) - рисуем только если блок что-то рендерил в composeBody
                // Для простоты проверим, не является ли блок обычным statement (у них composeBody пустой)
                // Можно добавить флаг в CodeBlock, но пока проверим по типу или просто всегда рисовать,
                // если composeBody что-то добавил?
                // *Решение*: В данном примере просто рисуем footer для If и Repeat, определяя это косвенно
                // В реальном проекте лучше флаг `isContainer`
                if (block is IfBlock || block is RepeatBlock) {
                    Box {
                        modifier.height(20.dp).width(Grow.Std)
                        modifier.background(ContainerFooterBackground(bgColor))

                        // DropTarget ПОСЛЕ всего контейнера
                        Box {
                            modifier.width(Grow.Std).height(10.dp).alignY(AlignmentY.Bottom)
                            addDropTargetOnce(DropAction.AttachAfter(block), uiNode)
                        }
                    }
                }
            }

            // Drop Target: Вставить ПОСЛЕ текущего блока
            // (Только если это не контейнер, у контейнера AttachAfter в футере)
            if (!block.isExpression && block !is IfBlock && block !is RepeatBlock) {
                val action = potentialAction
                if (draggingBlock != null && !isDragging && action is DropAction.AttachAfter && action.target == block) {
                    GhostPlaceholder(false)
                }
                // Рендерим следующий блок
                block.next?.let { next -> renderBlockRecursively(next, isGhost) }
            } else if(block is IfBlock || block is RepeatBlock) {
                // У контейнеров next рендерится после футера
                block.next?.let { next -> renderBlockRecursively(next, isGhost) }
            }
        }
    }

    private fun UiScope.BlockHeaderVisual(block: CodeBlock, isGhost: Boolean, blockModifier: UiModifier.() -> Unit) {
        Box {
            val marginLeft = if (block.isExpression) Dp.fromPx(PuzzleShapes.TAB_WIDTH) else 0.dp
            modifier.width(FitContent).margin(start = marginLeft).apply(blockModifier)

            val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color

            // Если это контейнер, рисуем специфичный фон с "зубом" вниз внутри
            val isContainer = block is IfBlock || block is RepeatBlock

            modifier.background(ScratchBlockBackground(
                color = bgColor,
                isExpression = block.isExpression,
                hasNext = !block.isExpression,
                isContainerHeader = isContainer
            ))

            Row {
                modifier.padding(horizontal = 10.dp, vertical = 6.dp).alignY(AlignmentY.Center)
                with(InputSlotScope(this, block, isGhost)) {
                    with(block) { composeContent() }
                }
            }

            // Если это обычный блок, добавляем зону drop after прямо сюда (снизу)
            if (!isContainer && !block.isExpression) {
                Box {
                    modifier.width(Grow.Std).height(10.dp).alignY(AlignmentY.Bottom)
                    addDropTargetOnce(DropAction.AttachAfter(block), uiNode)
                }
            }
        }
    }

    private fun UiScope.GhostPlaceholder(isExpression: Boolean) {
        Box {
            if (isExpression) modifier.size(40.dp, 30.dp)
            else modifier.height(40.dp).width(100.dp)
            modifier.background(ScratchBlockBackground(Color.WHITE.withAlpha(0.2f), isExpression, !isExpression))
        }
    }

    inner class InputSlotScope(val uiScope: UiScope, val parentBlock: CodeBlock, val isGhost: Boolean): UiScope by uiScope {

        // Обычный слот для значений (сбоку)
        fun InputSlot(name: String) {
            val attached = parentBlock.inputs[name]
            val action = potentialAction
            val isTargeted = action is DropAction.AttachToInput && action.target == parentBlock && action.inputName == name && !action.isStatementSlot

            uiScope.Box {
                modifier.alignY(AlignmentY.Center).margin(horizontal = 2.dp)
                addDropTargetOnce(DropAction.AttachToInput(parentBlock, name, false), uiNode)

                if (attached != null) {
                    if (draggingBlock == attached) EmptySlotVisual(isTargeted, true)
                    else {
                        renderBlockRecursively(attached)
                        if (isTargeted) modifier.border(RectBorder(Color.WHITE, 2.dp))
                    }
                } else {
                    if (isTargeted && draggingBlock?.isExpression == true) GhostPlaceholder(true)
                    else EmptySlotVisual(false, true)
                }
            }
        }

        // --- МЕСТО ДЛЯ ВЛОЖЕННЫХ ИНСТРУКЦИЙ (BODY) ---
        fun BodySlot(name: String) {
            val attached = parentBlock.inputs[name]
            val action = potentialAction

            // Проверяем, пытаемся ли мы что-то бросить в НАЧАЛО этого списка
            val isTargeted = action is DropAction.AttachToInput && action.target == parentBlock && action.inputName == name && action.isStatementSlot

            uiScope.Row {
                modifier.width(Grow.Std) // На всю ширину

                // 1. Позвоночник (Левая цветная полоса)
                val bgColor = if (isGhost) parentBlock.color.withAlpha(0.5f) else parentBlock.color
                Box {
                    modifier
                        .width(Dp.fromPx(C_BLOCK_SPINE_WIDTH))
                        .height(Grow.Std)
                        .background(RectBackground(bgColor))
                }

                // 2. Контейнер для блоков
                Column {
                    modifier.width(Grow.Std)

                    // -- Зона вставки в НАЧАЛО списка --
                    // Если список пуст -> это большая зона.
                    // Если не пуст -> это узкая полоска сверху.
                    Box {
                        if (attached == null) {
                            modifier.height(30.dp).width(100.dp) // Пустое тело - большая зона
                            if (isTargeted) modifier.background(RectBackground(Color.WHITE.withAlpha(0.2f)))
                        } else {
                            modifier.height(10.dp).width(Grow.Std) // Узкая зона вставки перед первым
                        }
                        // Это действие примагнитит блок как ПЕРВЫЙ в списке inputs[name]
                        addDropTargetOnce(DropAction.AttachToInput(parentBlock, name, true), uiNode)
                    }

                    // -- Рендер существующих блоков --
                    if (attached != null) {
                        if (draggingBlock == attached) {
                            // Если мы тащим первый блок, показываем плейсхолдер
                            Box { modifier.size(50.dp, 20.dp).background(RectBackground(Color.WHITE.withAlpha(0.1f))) }
                        } else {
                            renderBlockRecursively(attached, isGhost)
                        }
                    }

                    // -- Зона вставки в КОНЕЦ списка (Append) --
                    // Если список не пуст, нужно место внизу, куда можно кинуть, чтобы добавить в хвост
                    if (attached != null) {
                        Box {
                            modifier.height(20.dp).width(Grow.Std)
                            // Мы не можем использовать AttachAfter к null.
                            // Мы находим хвост цепочки.
                            var tail: CodeBlock = attached
                            while(tail.next != null) tail = tail.next!!

                            // Дроп сюда эквивалентен AttachAfter(tail)
                            addDropTargetOnce(DropAction.AttachAfter(tail), uiNode)

                            // Визуализация при наведении
                            if (action is DropAction.AttachAfter && action.target == tail) {
                                modifier.background(RectBackground(Color.WHITE.withAlpha(0.2f)))
                            }
                        }
                    }
                }
            }
        }

        // Разделитель секций (для Else и т.п.)
        fun SectionSeparator(label: String) {
            val bgColor = if (isGhost) parentBlock.color.withAlpha(0.5f) else parentBlock.color

            uiScope.Row {
                modifier.width(Grow.Std).height(FitContent)

                // Левая часть - продолжает позвоночник, но с выступом
                // Для простоты рисуем прямоугольник с текстом, имитирующим среднюю планку
                Box {
                    modifier.width(Grow.Std).height(30.dp)
                    // Используем фон "Средней части" (с выемкой сверху и зубом снизу)
                    modifier.background(ContainerMiddleBackground(bgColor))

                    Text(label) {
                        modifier
                            .alignY(AlignmentY.Center)
                            .margin(start = Dp.fromPx(C_BLOCK_SPINE_WIDTH + 10f)) // Отступ от позвоночника
                            .textColor(Color.WHITE)
                    }
                }
            }
        }

        private fun UiScope.EmptySlotVisual(highlight: Boolean, isExpression: Boolean) {
            Box {
                modifier.size(40.dp, 30.dp)
                modifier.background(SlotBackground(parentBlock.color.mix(Color.BLACK, 0.3f), highlight))
                if (highlight) modifier.border(RectBorder(Color.WHITE, 2.dp))
            }
        }
    }

    // --- Drag Logic Updates ---

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
        // Ищем подходящую цель
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
        // Отцепляем от вертикального родителя
        block.parent?.let { p ->
            if (p.next == block) p.next = null
            block.parent = null
        }
        // Отцепляем от инпута (горизонтального или тела)
        block.parentBlock?.let { p ->
            p.inputs.remove(block.parentInputName)
            block.parentBlock = null
            block.parentInputName = null
        }

        if (!rootBlocks.contains(block)) {
            rootBlocks.add(block)
            // Обновляем позицию визуально, чтобы не скакал
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
            if (action is DropAction.AttachToInput && !action.isStatementSlot) return true
            return false
        } else {
            // Statement
            return when(action) {
                is DropAction.InsertBefore -> true
                is DropAction.AttachAfter -> true
                is DropAction.AttachToInput -> action.isStatementSlot
            }
        }
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

        val existingBlock = target.inputs[slotName]

        if (existingBlock != null) {
            // КЛЮЧЕВОЕ ИЗМЕНЕНИЕ: Вставка вместо замены
            // 1. Ставим новый блок в слот
            target.inputs[slotName] = newBlock
            newBlock.parentBlock = target
            newBlock.parentInputName = slotName
            newBlock.parent = null

            // 2. Старый блок цепляем к новому снизу (next)
            // Ищем конец цепочки нового блока (если вдруг перетащили целую змейку)
            var tail = newBlock
            while(tail.next != null) tail = tail.next!!

            tail.next = existingBlock
            existingBlock.parent = tail
            // Очищаем привязки к родителю у старого, так как теперь его родитель - newBlock
            existingBlock.parentBlock = null
            existingBlock.parentInputName = null
        } else {
            // Если пусто - просто добавляем
            target.attachInput(slotName, newBlock)
        }
    }

    // Остальные методы (insertBlockBefore, attachBlockAfter, isAncestorOf, addDropTargetOnce) без изменений
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
        val parentBlock = target.parentBlock // Может быть внутри тела

        if (parent != null) {
            parent.next = newBlock
            newBlock.parent = parent
        } else if (parentBlock != null) {
            // Target был первым в цепочке внутри тела
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