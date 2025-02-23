package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext

fun WhenContext.transformWhen(statement: IrWhen): IrExpression = builder.run {
    statement.branches.forEach {
        it.condition = transformExpression(it.condition)

        val elseBranch = irSet(stateVar, irInt(nextBranch))
        append(
            irIfThenElse(
                context.irBuiltIns.unitType, it.condition,
                irSet(stateVar, irInt(nextBranch)), elseBranch, null
            )
        )

        nextBranch(true)
        append(transformExpression(it.result))
        nextBranch()
        elseBranch.value = builder.irInt(nextBranch - 1)
    }
    return IrNothing
}