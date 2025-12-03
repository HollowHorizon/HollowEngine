package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock

class BlockEditor {
    val rootBlocks = mutableStateListOf<CodeBlock>()

    var draggingBlock: CodeBlock? = null

    // Смещение курсора внутри блока
    val dragLocalOffset = MutableVec2f()

    var potentialParent: CodeBlock? = null

    // Список зон для Drop
    private val dropTargets = mutableListOf<Pair<CodeBlock, UiNode>>()

    init {
        // Инициализация тестовых блоков
        rootBlocks.add(PrintBlock("Hello").apply { setPosition(100f, 100f) })
        rootBlocks.add(PrintBlock("My Dear").apply { setPosition(100f, 160f) })
        rootBlocks.add(PrintBlock("World").apply { setPosition(100f, 220f) })
    }

    fun UiScope.EditorLayout() {
        dropTargets.clear()

        ScrollPane(rememberScrollState()) {
            modifier.layout(CellLayout)
            modifier.width(Grow.Std).height(Grow.Std)
            modifier.onClick { potentialParent = null }

            rootBlocks.use().forEach { block ->
                renderBlock(block, isRoot = true)
            }
        }
    }

    private fun UiScope.renderBlock(block: CodeBlock, isRoot: Boolean) {
        Column {
            // Z-Layer: Поднимаем блок и всю его цепочку, если тащим этот блок
            modifier.zLayer(if (draggingBlock == block) UiSurface.LAYER_FLOATING else UiSurface.LAYER_DEFAULT)

            if (isRoot) {
                // Применяем координаты только для рутового блока.
                // Остальные выстраиваются в Column автоматически.
                modifier.margin(start = Dp.fromPx(block.positionX.use()), top = Dp.fromPx(block.positionY.use()))
            }

            Box {
                // --- 1. ВИЗУАЛ БЛОКА ---
                BlockVisual(block) {
                    modifier
                        .onDragStart { ev -> handleDragStart(block, ev) }
                        .onDrag { ev -> handleDrag(block, ev) }
                        .onDragEnd { handleDragEnd(block) }
                }

                // TODO: Чтобы не было пустой зоны под блоком, добавляем её поверх блока (мб вообще стоит вместо отступа его использовать)
                Box {
                    modifier
                        .width(Grow.Std)
                        .height(20.dp) // Высота зоны чувствительности
                        .margin(top = (-15).dp) // !ВАЖНО! Поднимаем зону вверх, чтобы она накрыла "шип" блока
                        .zLayer(UiSurface.LAYER_DEFAULT + 1) // Чуть выше фона, чтобы ловить события
                        .alignY(AlignmentY.Bottom)
                        .border(RectBorder(Color.RED, sizes.borderWidth))

                    // Регистрируем эту зону как цель
                    dropTargets.add(block to uiNode)


                }
            }

            // Если это активная зона для дропа - рисуем ПРЕВЬЮ
            if (potentialParent == block && draggingBlock != block && draggingBlock != null) {
                // Смещаем превью вниз, чтобы оно выглядело как присоединенный блок.
                // Отступ 15dp компенсирует margin top=-15dp у родительского Box
                Column {
                    // Рисуем полупрозрачную копию перетаскиваемого блока
                    BlockVisual(draggingBlock!!, isGhost = true)
                }
            }

            // --- 3. РЕКУРСИЯ (Следующий блок) ---
            block.next?.let { nextBlock ->
                // Следующий блок рисуется сразу после Box зоны стыковки.
                // Так как у Box margin отрицательный, следующий блок "подтянется" вверх к шипу.
                renderBlock(nextBlock, isRoot = false)
            }
        }
    }

    // Вынесенная визуальная часть для переиспользования в Ghost
    private fun UiScope.BlockVisual(block: CodeBlock, isGhost: Boolean = false, blockModifier: UiModifier.() -> Unit = {}) {
        Column {
            modifier
                .width(FitContent)
                .apply(blockModifier)

            val bgColor = if (isGhost) block.color.withAlpha(0.5f) else block.color

            modifier.background(ScratchBlockBackground(bgColor, hasTopNotch = true, hasBottomNotch = true))

            // Верхний отступ (визуальный пэддинг внутри блока)
            Box { modifier.height(5.dp).width(Grow.Std) }

            // Контент
            Row {
                modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                if (isGhost) {
                    // Для призрака можно упростить контент, но full render тоже ок
                    with(block) { composeContent() }
                } else {
                    with(block) { composeContent() }
                }
            }

            // Нижний отступ (визуальный пэддинг до начала шипа)
            Box { modifier.height(5.dp).width(Grow.Std) }
        }
    }

    private fun UiScope.handleDragStart(block: CodeBlock, ev: PointerEvent) {
        draggingBlock = block
        dragLocalOffset.set(ev.position.x, ev.position.y)

        if (!rootBlocks.contains(block)) {
            val screenPos = ev.screenPosition
            detachBlock(block)

            // Пересчитываем координаты, чтобы блок остался под мышкой
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

            // Hit Test
            val hitTarget = dropTargets.find { (targetBlock, node) ->
                node.isInBounds(ev.screenPosition) && block != targetBlock
            }
            potentialParent = hitTarget?.first

            surface.triggerUpdate()
        }
    }

    private fun handleDragEnd(block: CodeBlock) {
        potentialParent?.let { parent ->
            attachBlock(parent, block)
        }
        draggingBlock = null
        potentialParent = null
    }

    // --- ЛОГИКА ДАННЫХ ---

    private fun detachBlock(block: CodeBlock) {
        val parent = findParent(block, rootBlocks)
        if (parent != null) {
            parent.next = null
            rootBlocks.add(block)
        }
    }

    private fun attachBlock(parent: CodeBlock, child: CodeBlock) {
        // 1. Сохраняем то, что было после родителя (если мы вставляем в середину)
        val oldNext = parent.next

        // 2. Родитель теперь ссылается на присоединенный блок
        parent.next = child

        // 3. !ИСПРАВЛЕНИЕ! Ищем КОНЕЦ цепочки присоединенного блока
        var childTail = child
        while (childTail.next != null) {
            childTail = childTail.next!!
        }

        // 4. Конец новой цепочки теперь ссылается на старый хвост родителя
        childTail.next = oldNext

        // 5. Убираем child из корней, так как он теперь вложен
        rootBlocks.remove(child)
    }

    private fun findParent(target: CodeBlock, roots: List<CodeBlock>): CodeBlock? {
        for (root in roots) {
            if (root.next == target) return root
            val foundInChild = findParentInChain(root, target)
            if (foundInChild != null) return foundInChild
        }
        return null
    }

    private fun findParentInChain(current: CodeBlock, target: CodeBlock): CodeBlock? {
        var node = current
        while (node.next != null) {
            if (node.next == target) return node
            node = node.next!!
        }
        return null
    }
}

private inline fun <reified T> UiNode.findParentOfType(): T? {
    var current = this
    while (current !is T) {
        current = current.parent ?: break
    }
    return current as? T
}