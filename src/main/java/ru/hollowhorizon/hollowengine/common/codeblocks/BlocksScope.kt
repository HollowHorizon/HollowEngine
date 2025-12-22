package ru.hollowhorizon.hollowengine.common.codeblocks

import de.fabmax.kool.modules.ui2.MutableStateList
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

interface BlocksScope {
    val rootBlocks: MutableStateList<BlockModel>
}

fun BlocksScope.walk() = sequence {
    rootBlocks.forEach {
        yieldAll(it.walk())
    }
}

class ObservableBlockList(private val scope: BlocksScope) : ArrayList<BlockModel>() {
    override fun add(element: BlockModel): Boolean {
        element.setExplicitScope(scope)
        return super.add(element)
    }

    override fun add(index: Int, element: BlockModel) {
        element.setExplicitScope(scope)
        super.add(index, element)
    }

    override fun remove(element: BlockModel): Boolean {
        element.setExplicitScope(null)
        return super.remove(element)
    }

    override fun removeAt(index: Int): BlockModel {
        val removed = super.removeAt(index)
        removed.setExplicitScope(null)
        return removed
    }

    override fun clear() {
        forEach { it.setExplicitScope(null) }
        super.clear()
    }
}

fun BlockModel.walk(): Sequence<BlockModel> = sequence {
    yield(this@walk)
    for (input in inputs.values) {
        yieldAll(input.walk())
    }
    (this@walk as? StatementBlock)?.next?.let {
        yieldAll(it.walk())
    }
}

val BlockModel.parentCount: Int
    get() = (((this as? StatementBlock)?.parent ?: (this as? ExpressionBlock)?.parentBlock)?.parentCount ?: 0) + 1