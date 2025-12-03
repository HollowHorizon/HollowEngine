package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.MutableVec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.PrintBlock

// TODO: Явно указывать surface не имеет смысла, он уже есть в UiScope
class BlockEditor {
    val rootBlocks = mutableStateListOf<CodeBlock>()

    var draggingBlock: CodeBlock? = null
    val dragOffset = MutableVec2f()

    var potentialParent: CodeBlock? = null

    init {
        rootBlocks.add(PrintBlock("Hello").apply { position.set(100f, 100f) })
        rootBlocks.add(PrintBlock("My Dear").apply { position.set(100f, 100f) })
        rootBlocks.add(PrintBlock("World").apply { position.set(100f, 200f) })
    }

    private val dropTargets = mutableListOf<Pair<CodeBlock, UiNode>>()

    fun UiScope.EditorLayout() {
        dropTargets.clear()

        ScrollPane(rememberScrollState()) {
            modifier.layout(CellLayout) // Позволяет свободное позиционирование через margin
            modifier.width(Grow.Std).height(Grow.Std)
            //TODO: Думаю potentialParent тут сбрасывать не нужно, он сам сбрасывается в onDragEnd

            // Рендерим только корневые блоки.
            // Вложенные (next) будут отрендерены рекурсивно внутри renderBlock.
            rootBlocks.use().forEach { block ->
                renderBlock(block, isRoot = true)
            }
        }
    }

    private fun UiScope.renderBlock(block: CodeBlock, isRoot: Boolean) {
        Column {
            if (isRoot) {
                modifier.margin(start = Dp.fromPx(block.position.x), top = Dp.fromPx(block.position.y))
            }
            modifier.zLayer(if (draggingBlock == block) UiSurface.LAYER_FLOATING else UiSurface.LAYER_DEFAULT)

            Column {
                modifier
                    .width(FitContent)
                    .background(ScratchBlockBackground(block.color, hasTopNotch = true, hasBottomNotch = true))
                    .onDragStart { ev ->
                        draggingBlock = block
                        if (!rootBlocks.contains(block)) {
                            detachBlock(block)
                        }

                        dragOffset.set(ev.position.x, ev.position.y)
                    }
                    .onDrag { ev ->
                        if (draggingBlock == block) {
                            // TODO: Возможно лучше это передать через аргументы метода или сделать поле в классе
                            //  Иначе возможно через несколько слоёв рекурсии этот код упадёт
                            val scrollPane = uiNode.parent!!.parent!!
                            val localPos = MutableVec2f()

                            // TODO: Не уверен что это будет работать, хотя мой вариант отлично срабатывает
                            scrollPane.toLocal(ev.screenPosition, localPos)

                            block.position.set(localPos.x - dragOffset.x, localPos.y - dragOffset.y)

                            val hitTarget = dropTargets.find { (codeBlock, node) ->
                                // Проверка: курсор внутри границ ноды?
                                // TODO: Может стоит проверять по ссылке?
                                node.isInBounds(ev.screenPosition) && block != codeBlock // Не прицеплять к самому себе
                            }

                            potentialParent = hitTarget?.first

                            // TODO: Может быть поменять тогда position на mutableStateOf?
                            surface.triggerUpdate()
                        }
                    }
                    .onDragEnd {
                        potentialParent?.let { parent ->
                            attachBlock(parent, block)
                        }
                        draggingBlock = null
                        potentialParent = null
                    }

                // 1. Верхний отступ (визуально)
                Box { modifier.height(5.dp).width(Grow.Std) }

                // 2. Контент блока
                Row {
                    modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    with(block) { composeContent() }
                }

                // 3. Нижняя зона стыковки (Drop Zone)
                Box {
                    modifier
                        .height(15.dp) // Высота зоны чувствительности
                        .width(Grow.Std)

                    if (potentialParent == block && draggingBlock != block) {
                        modifier.border(RectBorder(Color.RED.withAlpha(1f), sizes.borderWidth))
                    }

                    // *** ВАЖНО: Регистрируем эту ноду как цель для drop ***
                    // Мы делаем это прямо здесь, так как UiNode доступна внутри блока compose
                    dropTargets.add(block to uiNode)
                }
            }

            // Рекурсия для следующего блока
            block.next?.let { nextBlock ->
                // Важно: nextBlock уже не root, поэтому isRoot = false
                renderBlock(nextBlock, isRoot = false)
            }
        }
    }

    private fun detachBlock(block: CodeBlock) {
        // 1. Найти кто ссылается на этот блок (родителя)
        // TODO: Это неэффективно полным перебором, лучше хранить parent в CodeBlock,
        //  но для примера пробежимся по корням и их детям
        val parent = findParent(block, rootBlocks)

        if (parent != null) {
            parent.next = null
            // Пересчитать координаты block в глобальные, чтобы он не прыгнул визуально
            // block.position.set(...)
            rootBlocks.add(block)
        }
    }

    // Логика данных: Присоединить child к parent
    private fun attachBlock(parent: CodeBlock, child: CodeBlock) {
        // Если у родителя уже был next, его надо куда-то деть (например, вставить между)
        val oldNext = parent.next

        parent.next = child
        child.next = oldNext // Вставка в середину цепочки

        rootBlocks.remove(child) // Убираем из корня, теперь он внутри
    }

    // Вспомогательная функция поиска родителя
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