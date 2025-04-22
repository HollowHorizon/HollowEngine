package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.util.transformInPlace
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext
import ru.hollowhorizon.hollowengine.compiler.pluginContext

fun WhenContext.transformWhen(statement: IrWhen): IrExpression = builder.run {
    val result = if (statement.type != pluginContext.irBuiltIns.unitType) {
        IrVariableImpl(
            startOffset,
            endOffset,
            IrDeclarationOrigin.DEFINED,
            IrVariableSymbolImpl(),
            name = Name.identifier("whenResult$${whenResultId++}"),
            statement.type,
            true,
            isConst = false,
            isLateinit = false
        ).apply {
            append(this)
            parent = generator.invokeFunction
        }
    } else null

    val lastIndex = statement.branches.lastIndex
    val elseSkips = mutableListOf<IrSetValue>()
    statement.branches.sortedBy { it is IrElseBranch }.forEachIndexed { index, branch ->
        branch.condition = transformExpression(branch.condition)

        if(index == lastIndex && branch is IrElseBranch) {
            nextBranch(true, resume = true)
            result?.let {
                append(irSet(it, transformExpression(branch.result)))
            } ?: run {
                append(transformExpression(branch.result))
            }
            nextBranch(false, resume = true)
            elseSkips.forEach { it.value = builder.irInt(nextBranch - 1) }
        } else {

            val elseBranch = irSet(stateVar, irInt(nextBranch))
            append(
                irIfThenElse(
                    context.irBuiltIns.unitType, branch.condition,
                    irSet(stateVar, irInt(nextBranch)), elseBranch, null
                )
            )

            nextBranch(true, resume = true)
            result?.let {
                append(irSet(it, transformExpression(branch.result)))
            } ?: run {
                append(transformExpression(branch.result))
            }

            elseSkips += irSet(stateVar, irInt(nextBranch))
            append(elseSkips.last())
            nextBranch(true, resume = true)
            elseBranch.value = builder.irInt(nextBranch - 1)
        }
    }
    return result?.let { builder.irGet(it) } ?: IrNothing
}