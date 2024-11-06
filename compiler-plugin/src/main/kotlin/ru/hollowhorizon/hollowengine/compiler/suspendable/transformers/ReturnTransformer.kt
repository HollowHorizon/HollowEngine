package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrReturn
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer

fun SuspendCallTransformer.transformReturn(statement: IrReturn): IrExpression? {
    val result = transformStatement(statement.value, true)
    whenContext.append(whenContext.builder.irReturn(result ?: statement.value).apply {
        returnTargetSymbol = statement.returnTargetSymbol
    })
    return null
}
