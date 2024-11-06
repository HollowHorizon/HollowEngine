package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrWhen
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer
import ru.hollowhorizon.hollowengine.compiler.suspendable.irIfThenElse

fun SuspendCallTransformer.transformWhen(statement: IrWhen): IrExpression? {
    statement.branches.forEach {
        val result = transformStatement(it.condition, true)
        if (result != null) it.condition = result

        with(whenContext) {
            val elseBranch = with(builder) {
                irCall(suspendSetter).apply {
                    dispatchReceiver = suspendContext
                }
            }
            with(builder) {
                append(
                    irIfThenElse(
                        context.irBuiltIns.unitType, it.condition,
                        irCall(suspendSetter).apply {
                            dispatchReceiver = suspendContext
                            putValueArgument(0, irInt(nextBranch))
                        }, elseBranch, null
                    )
                )
            }
            returnResume()
            nextBranch()
            transformStatement(it.result, false)
            switchState()
            returnResume()
            nextBranch()
            elseBranch.putValueArgument(0, builder.irInt(nextBranch - 1))
        }
    }
    return null
}