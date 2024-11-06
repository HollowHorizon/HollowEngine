package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer

fun SuspendCallTransformer.transformGet(statement: IrGetValue): IrExpression? {
    if (statement.type != statement.symbol.owner.type) {
        return whenContext.builder.irGet(statement.symbol.owner)
    }
    return null
}