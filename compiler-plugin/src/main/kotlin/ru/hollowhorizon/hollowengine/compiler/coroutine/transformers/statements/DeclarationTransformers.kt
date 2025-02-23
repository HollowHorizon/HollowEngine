package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.ir.declarations.IrVariable
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext

fun WhenContext.transformVariable(statement: IrVariable): IrVariable {
    statement.initializer?.let {
        val new = transformExpression(it)
        statement.initializer = new
        statement.type = new.type
    }
    return statement
}