package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irSet
import org.jetbrains.kotlin.ir.expressions.IrDoWhileLoop
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrLoop
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext


class BreakContinue(val context: WhenContext) {
    val breakIndex = context.builder.irInt(0)
    val continueIndex = context.builder.irInt(0)
}
val loops = hashMapOf<IrLoop, BreakContinue>()

fun WhenContext.transformLoop(loop: IrLoop): IrExpression {
    val isDoWhile = loop is IrDoWhileLoop

    val breakContinue = loops.getOrPut(loop) { BreakContinue(this) }
    loop.condition = transformExpression(loop.condition)

    if(!isBranchEmpty()) nextBranch()
    val elseBranch = builder.run { irSet(stateVar, irInt(nextBranch)) }

    val currentBranch = nextBranch - 1

    // Если цикл do-while, выполняем тело до проверки условия
    if (isDoWhile) {
        loop.body?.let {
            append(transformExpression(it))
        }
        append(builder.run { irSet(stateVar, irInt(currentBranch+1)) })
        nextBranch(true)
    }

    append(
        builder.run {
            irIfThenElse(
                context.irBuiltIns.unitType, loop.condition,
                irSet(stateVar, irInt(if(isDoWhile) currentBranch else nextBranch)),
                elseBranch
            )
        }
    )
    breakContinue.continueIndex.value = if(isDoWhile) currentBranch else nextBranch
    nextBranch(true)

    if (!isDoWhile) {
        loop.body?.let {
            append(transformExpression(it))
        }
        append(builder.run { irSet(stateVar, irInt(currentBranch)) })
        nextBranch(true)
    }

    breakContinue.breakIndex.value = nextBranch-1
    elseBranch.value = builder.irInt(nextBranch-1)

    return IrNothing
}