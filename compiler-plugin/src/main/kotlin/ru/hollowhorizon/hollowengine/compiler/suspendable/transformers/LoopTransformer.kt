package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer
import ru.hollowhorizon.hollowengine.compiler.suspendable.irIfThenElse

fun SuspendCallTransformer.transformLoop(statement: IrLoop): IrExpression? {
    val isDoWhile = statement is IrDoWhileLoop
    val result = transformStatement(statement.condition, true)
    if (result != null) statement.condition = result

    with(whenContext) {
        switchState()
        returnResume()
        nextBranch()

        // Определяем ветвления
        val elseBranch = with(builder) {
            irCall(suspendSetter).apply {
                dispatchReceiver = suspendContext
            }
        }
        val currentBranch = nextBranch - 1

        // Если цикл do-while, выполняем тело до проверки условия
        if (isDoWhile) {
            statement.body?.let { transformStatement(it, false) }
            append(builder.irCall(suspendSetter).apply {
                dispatchReceiver = suspendContext
                putValueArgument(0, builder.irInt(currentBranch + 1))
            })
            returnSuspend()
            nextBranch()
        }

        // Добавляем ветвление для условия цикла
        with(builder) {
            append(
                irIfThenElse(
                    context.irBuiltIns.unitType, statement.condition,
                    irCall(suspendSetter).apply {
                        dispatchReceiver = suspendContext
                        putValueArgument(0, irInt(if (isDoWhile) currentBranch else nextBranch))
                    }, elseBranch, null
                )
            )
        }
        returnResume()
        nextBranch()

        // Если цикл while, выполняем тело после проверки условия
        if (!isDoWhile) {
            statement.body?.let { transformStatement(it, false) }
            append(builder.irCall(suspendSetter).apply {
                dispatchReceiver = suspendContext
                putValueArgument(0, builder.irInt(currentBranch))
            })
            returnSuspend()
            nextBranch()
        }

        elseBranch.putValueArgument(0, builder.irInt(nextBranch - 1))
    }

    return null
}