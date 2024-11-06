package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer

fun SuspendCallTransformer.transformVariable(statement: IrVariable): IrExpression? {
    statement.initializer?.let {
        val result = transformStatement(it, true)
        result?.let {
            statement.initializer = it
            statement.type = it.type
        }
    }
    whenContext.append(statement)
    return null
}