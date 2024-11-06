package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.expressions.*
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer

fun SuspendCallTransformer.transformContainer(statement: IrContainerExpression, shouldReplace: Boolean): IrExpression? {
    statement.statements.forEach { transformStatement(it, shouldReplace) }
    return null
}
