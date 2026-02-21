package ru.hollowhorizon.hollowengine.common.codeblocks

import kotlinx.serialization.PolymorphicSerializer
import ru.hollowhorizon.hollowengine.common.codeblocks.model.BlockModel
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import java.util.*
import kotlin.contracts.contract

fun <T : BlockModel> T.deepCopy(provider: BlockProvider): T {

    val format = CodeBlockFormat(provider)

    val jsonString = format.json.encodeToString(PolymorphicSerializer(BlockModel::class), this)
    val clone = format.json.decodeFromString(PolymorphicSerializer(BlockModel::class), jsonString)

    clone.uuid = UUID.randomUUID()
    (clone as? StatementBlock)?.parent = null
    clone.parentBlock = null
    clone.parentInputName = null

    this.inputs.forEach { (slotName, inputBlock) ->
        val inputClone = inputBlock.deepCopy(provider)
        clone.attachInput(slotName, inputClone)
    }

    (this as? StatementBlock)?.next?.let { nextBlock ->
        val nextClone = nextBlock.deepCopy(provider) as StatementBlock
        (clone as StatementBlock).next = nextClone
        nextClone.parent = clone
    }

    return clone as T
}

@OptIn(kotlin.contracts.ExperimentalContracts::class)
fun BlockModel.isExpression(): Boolean {
    contract {
        returns(true) implies (this@isExpression is ExpressionBlock)
    }
    return this is ExpressionBlock
}


@OptIn(kotlin.contracts.ExperimentalContracts::class)
fun BlockModel.isStatement(): Boolean {
    contract {
        returns(true) implies (this@isStatement is StatementBlock)
    }
    return this is StatementBlock
}

val BlockModel.isRoot: Boolean
    get() = when (this) {
        is StatementBlock -> parent == null && parentBlock == null
        else -> parentBlock == null
    }

val BlockModel.root: BlockModel
    get() = when {
        isStatement() -> parent?.root ?: parentBlock?.root ?: this
        else -> parentBlock?.root ?: this
    }

val BlockModel.bodyRoot: BlockModel
    get() = when {
        isStatement() -> parent?.bodyRoot ?: this
        else -> this
    }

val BlockModel.parentsWithSelf: Sequence<BlockModel>
    get() = sequence {
        yield(this@parentsWithSelf)
        yieldAll(parents)
    }
val BlockModel.parents: Sequence<BlockModel>
    get() = sequence {
        if (this@parents is StatementBlock) parent?.let {
            yield(it)
            yieldAll(it.parents)
        }
        parentBlock?.let {
            yield(it)
            yieldAll(it.parents)
        }
    }

fun BlockModel.flatten(): Set<BlockModel> = buildSet {
    add(this@flatten)
    (this@flatten as? StatementBlock)?.next?.flatten()?.let { addAll(it) }
    inputs.values.forEach {
        addAll(it.flatten())
    }
}