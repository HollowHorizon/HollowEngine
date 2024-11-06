package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrThrow
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer

fun SuspendCallTransformer.transformThrow(statement: IrThrow): IrExpression? {
    whenContext.append(statement)
    return null
}