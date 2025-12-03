package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.StringValueBlock

// Новые действия
sealed interface DropAction {
    val target: CodeBlock
    data class InsertBefore(override val target: CodeBlock) : DropAction
    data class AttachAfter(override val target: CodeBlock) : DropAction
    // Новое действие: вставить в слот inputName целевого блока
    data class AttachToInput(override val target: CodeBlock, val inputName: String) : DropAction
}

class BlockEditor {
    val rootBlocks = mutableStateListOf<CodeBlock>()

    var draggingBlock: CodeBlock? = null
    val dragLocalOffset = MutableVec2f()
    var potentialAction: DropAction? = null

    private val dropTargets = mutableListOf<Pair<DropAction, UiNode>>()
    private val tmpLocal = MutableVec2f()

    init {
        // Тест: Принт и Выражение
        val b1 = PrintBlock("Default Text").apply { setPosition(100f, 100f) }
        val valBlock = StringValueBlock("Hello")

        // Пример прикрепления программно
        // b1.attachInput("msg", valBlock)

        rootBlocks.add(b1)
        rootBlocks.add(StringValueBlock("Free String").apply { setPosition(100f, 200f) })
    }

    // --- UI Layout ---

    fun UiScope.EditorLayout() {
        dropTargets.clear()

        ScrollPane(rememberScrollState()) {
            modifier.layout(CellLayout)
            modifier.width(Grow.Std).height(Grow.Std)
            modifier.onClick { potentialAction = null }

            // Рисуем только корневые блоки. Вложенные отрисуются внутри родителей.
            rootBlocks.use().forEach { block ->
                renderBlockRecursively(block)
            }
        }
    }

    // Универсальный рендер блока (и корневого, и вложенного, и ghost)
    private fun UiScope.renderBlockRecursively(block: CodeBlock, isGhost: Boolean = false) {
        Column {
            // Если это корневой блок, ставим позицию. Если вложенный - позиция автоматическая (в потоке Column/Row).
            // Проверяем: если у блока нет parentBlock (инпута) и нет parent (вертикального), и он в rootBlocks -> позиционируем.
            val isRoot = rootBlocks.contains(block)

            modifier.zLayer(if (draggingBlock == block) UiSurface.LAYER_FLOATING else UiSurface.LAYER_DEFAULT)

            if (isRoot) {
                modifier.margin(start = Dp.fromPx(block.positionX.use()), top = Dp.fromPx(block.positionY.use()))
            }

            val action = potentialAction
            val isDraggingOther = draggingBlock != null && draggingBlock != block

            // 1. Зона вставки СВЕРХУ (только для Statements, не для Expressions)
            if (!block.isExpression) {
                if (isDraggingOther && action is DropAction.InsertBefore && action.target == block) {
                    GhostPlaceholder(draggingBlock!!, true)
                }
                // Зона дропа сверху
                Box {
                    modifier.width(Grow.Std).height(20.dp).margin(top = (-15).dp).alignY(AlignmentY.Top)
                    addDropTargetOnce(DropAction.InsertBefore(block), uiNode)
                }
            }

            // 2. Визуал самого блока
            Box {
                modifier.layout(CellLayout) // Чтобы дети накладывались (фон + контент)

                // Фон и обработчики драга
                BlockVisual(block, isGhost) {
                    modifier
                        .onDragStart { ev -> handleDragStart(block, ev) }
                        .onDrag { ev -> handleDrag(block, ev) }
                        .onDragEnd { handleDragEnd(block) }
                }

                // Для Drop Target "Input" нам нужно, чтобы зоны были внутри composeContent.
                // Но drop targets добавляются глобально. См. реализацию InputSlot ниже.
            }

            // 3. Зона вставки СНИЗУ (только для Statements)
            if (!block.isExpression) {
                // Зона дропа снизу
                Box {
                    modifier.width(Grow.Std).height(20.dp).margin(bottom = (-10).dp).alignY(AlignmentY.Bottom)
                    addDropTargetOnce(DropAction.AttachAfter(block), uiNode)
                }

                if (isDraggingOther && action is DropAction.AttachAfter && action.target == block) {
                    GhostPlaceholder(draggingBlock!!, false)
                }

                // Рекурсия для vertical flow (next)
                block.next?.let { next ->
                    renderBlockRecursively(next, isGhost)
                }
            }
        }
    }

    private fun UiScope.BlockVisual(block: CodeBlock, isGhost: Boolean, blockModifier: UiModifier.() -> Unit = {}) {
        Column {
            modifier.width(FitContent).apply(blockModifier)

            val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color

            // Определяем форму: если Expression - просто скругленный, если Statement - с зубчиками
            modifier.background(ScratchBlockBackground(
                color = bgColor,
                hasTopNotch = !block.isExpression,
                hasBottomNotch = !block.isExpression && block.next != null // или всегда true для statement
            ))

            Row {
                modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                modifier.alignY(AlignmentY.Center)

                // Здесь магия: мы подменяем composeContent так, чтобы можно было вызывать InputSlot
                with(InputSlotScope(this, block)) {
                    with(block) { composeContent() }
                }
            }
        }
    }

    // Хелпер для рендера Ghost (призрака при перетаскивании)
    private fun UiScope.GhostPlaceholder(block: CodeBlock, topMargin: Boolean) {
        Box {
            modifier.height(40.dp).width(100.dp) // Условно
            // Можно отрендерить упрощенную копию
        }
    }

    // --- Input Slots Logic ---

    // Специальный скоуп, который мы передаем в composeContent блока,
    // чтобы он мог вызывать функцию InputSlot
    inner class InputSlotScope(val uiScope: UiScope, val parentBlock: CodeBlock): UiScope by uiScope {
        // Главная фича: Слот для вставки
        fun InputSlot(name: String) {
            val attachedBlock = parentBlock.inputs[name]
            val action = potentialAction
            val isTargeted = action is DropAction.AttachToInput &&
                    action.target == parentBlock &&
                    action.inputName == name

            uiScope.Box {
                modifier
                    .margin(horizontal = 5.dp)
                    .alignY(AlignmentY.Center)
                    .border(RectBorder(Color.WHITE, sizes.borderWidth))

                // Логика Drop Target для слота
                // Важно: зона должна быть активна, даже если там уже есть блок (для замены) или пусто
                addDropTargetOnce(DropAction.AttachToInput(parentBlock, name), uiNode)

                if (attachedBlock != null) {
                    // Рендерим прикрепленный блок
                    // Важно: если мы тащим ЭТОТ блок, рисуем его полупрозрачным или вообще скрываем (если хотим логику "вырывания")
                    if (draggingBlock == attachedBlock) {
                        // Место свободно визуально, пока тащим
                        EmptySlotVisual(isTargeted)
                    } else {
                        renderBlockRecursively(attachedBlock)
                        // Если наводим поверх существующего - можно подсветить, что он будет заменен
                        if (isTargeted) {
                            modifier.border(RectBorder(Color.WHITE, 2.dp))
                        }
                    }
                } else {
                    // Слот пуст
                    if (isTargeted && draggingBlock != null) {
                        // Показываем превью того, что вставляем
                        // Упрощенно: просто бокс цвета блока
                        Box {
                            modifier
                                .size(40.dp, 20.dp)
                                .background(RectBackground(draggingBlock!!.color.withAlpha(0.5f)))
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
                    .size(30.dp, 20.dp)
                    .background(RectBackground(Color.BLACK.withAlpha(0.2f)))
                    .border(RectBorder(if(highlight) Color.WHITE else Color.WHITE.withAlpha(0f), 2.dp))
            }
        }
    }

    // --- Drag Handlers ---

    private fun UiScope.handleDragStart(block: CodeBlock, ev: PointerEvent) {
        draggingBlock = block
        dragLocalOffset.set(ev.position.x, ev.position.y)

        // Логика отсоединения
        detachBlock(block, ev.screenPosition)
    }

    private fun UiScope.handleDrag(block: CodeBlock, ev: PointerEvent) {
        if (draggingBlock != block) return

        // Обновляем позицию (визуально блок теперь летает)
        val scrollPane = uiNode.findParentOfType<ScrollPaneNode>() ?: return
        scrollPane.toLocal(ev.screenPosition, tmpLocal)
        block.setPosition(tmpLocal.x - dragLocalOffset.x, tmpLocal.y - dragLocalOffset.y)

        // Поиск цели Drop
        // 1. Проверяем слоты (Input) - они имеют приоритет, так как они меньше и внутри
        // 2. Проверяем вертикальные зоны

        var bestAction: DropAction? = null

        // Ищем среди всех зон
        for ((action, node) in dropTargets) {
            if (node.isInBounds(ev.screenPosition)) {
                // Фильтрация: нельзя вставить в себя или в своих детей
                if (isValidDrop(block, action)) {
                    bestAction = action
                    break // Нашли (можно добавить логику выбора ближайшего по Z)
                }
            }
        }
        potentialAction = bestAction
        surface.triggerUpdate()
    }

    private fun isValidDrop(source: CodeBlock, action: DropAction): Boolean {
        if (source == action.target) return false
        // Нельзя вставить родителя в ребенка
        if (isAncestorOf(source, action.target)) return false

        // Для AttachToInput: принимаем только isExpression = true (опционально, но логично)
        if (action is DropAction.AttachToInput) {
            if (!source.isExpression) return false // Если разрешаем вставлять statement внутрь, убрать это
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

    // --- Logic Utils ---

    private fun UiScope.detachBlock(block: CodeBlock, screenPos: Vec2f) {
        // 1. Отцепляем от вертикального родителя
        block.parent?.let { p ->
            if (p.next == block) p.next = null
            block.parent = null
        }

        // 2. Отцепляем от функционального родителя (Input)
        block.parentBlock?.let { p ->
            p.inputs.remove(block.parentInputName)
            block.parentBlock = null
            block.parentInputName = null
        }

        // 3. Добавляем в root, чтобы он рисовался поверх всего во время драга
        if (!rootBlocks.contains(block)) {
            rootBlocks.add(block)
            // Конвертируем координаты, чтобы блок не прыгнул
            val scrollPane = uiNode.findParentOfType<ScrollPaneNode>()
            if (scrollPane != null) {
                scrollPane.toLocal(screenPos, tmpLocal)
                block.setPosition(tmpLocal.x - dragLocalOffset.x, tmpLocal.y - dragLocalOffset.y)
            }
        }
    }

    private fun attachBlockToInput(target: CodeBlock, slotName: String, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock) // Убираем из корня

        // Если там уже кто-то был - выкидываем его (или можно сделать swap)
        target.inputs[slotName]?.let { oldBlock ->
            oldBlock.parentBlock = null
            oldBlock.parentInputName = null
            rootBlocks.add(oldBlock)
            oldBlock.setPosition(newBlock.positionX.value + 20, newBlock.positionY.value + 20)
        }

        target.attachInput(slotName, newBlock)
    }

    // insertBlockBefore / attachBlockAfter остаются почти такими же,
    // но нужно учесть, что draggingBlock мог прийти из Input'а.
    // detachBlock уже это решает.

    private fun attachBlockAfter(target: CodeBlock, newBlock: CodeBlock) {
        rootBlocks.remove(newBlock)
        val oldNext = target.next
        target.next = newBlock
        newBlock.parent = target

        // Хвост
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
            // Target был корнем
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
        // Проверяем и вертикаль (parent), и инпуты (parentBlock)
        var curr: CodeBlock? = child.parent ?: child.parentBlock
        while (curr != null) {
            if (curr == possibleParent) return true
            curr = curr.parent ?: curr.parentBlock
        }
        return false
    }

    private fun addDropTargetOnce(action: DropAction, node: UiNode) {
        // Простая проверка на дубликаты
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