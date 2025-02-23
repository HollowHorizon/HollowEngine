package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrTypeOperatorCall
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext

fun WhenContext.transformTypeOperator(call: IrTypeOperatorCall): IrExpression {
    call.argument = transformExpression(call.argument)
    return call
}