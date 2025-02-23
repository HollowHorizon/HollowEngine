package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext
import ru.hollowhorizon.hollowengine.compiler.pluginContext

fun WhenContext.transformBody(body: IrBody) {
    body.statements.forEach {
        val stmt = transformStatement(it)
        if (stmt is IrNothing) return@forEach
        append(stmt)
    }
}

fun WhenContext.transformContainer(body: IrContainerExpression): IrExpression {
    body.statements.forEach {
        val stmt = transformStatement(it)
        if (stmt is IrNothing) return@forEach
        append(stmt)
    }
    return if(body.type == pluginContext.irBuiltIns.unitType) IrNothing else removeLastStmt() as IrExpression
}

fun WhenContext.transformStatement(statement: IrStatement): IrStatement {
    return when (statement) {
        is IrExpression -> transformExpression(statement)
        is IrDeclaration -> transformDeclaration(statement)
        else -> error("Unknown statement type ${statement.javaClass.name}!")
    }
}

fun WhenContext.transformDeclaration(statement: IrDeclaration): IrDeclaration {
    return when (statement) {
        is IrVariable -> transformVariable(statement)
        else -> error("Unknown declaration type ${statement.javaClass.name}")
    }
}

fun WhenContext.transformExpression(statement: IrExpression): IrExpression {
    return when (statement) {
        is IrContainerExpression -> transformContainer(statement)
        is IrFunctionExpression -> {
            statement
        }
        is IrCall -> transformCall(statement)
        is IrGetValue -> transformGet(statement)
        is IrSetValue -> transformSet(statement)
        is IrLoop -> transformLoop(statement)
        is IrBreak -> {
            val breakIndex = loops[statement.loop]!!.breakIndex
            append(builder.run { irBlock {
                +irSet(stateVar, breakIndex)
                +irReturn(irGetObject(suspendObject))
            } })
            IrNothing
        }
        is IrContinue -> {
            val continueIndex = loops[statement.loop]!!.continueIndex
            append(builder.run { irBlock {
                +irSet(stateVar, continueIndex)
                +irReturn(irGetObject(suspendObject))
            } })
            IrNothing
        }
        is IrWhen -> transformWhen(statement)
        is IrConst -> statement
        is IrTypeOperatorCall -> transformTypeOperator(statement)
        is IrReturn -> transformReturn(statement)
        is IrNothing -> IrNothing
        else -> error("Unknown expression type ${statement.javaClass.name}!")
    }
}

fun WhenContext.transformReturn(statement: IrReturn): IrExpression {
    return builder.irReturn(transformExpression(statement.value))
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun WhenContext.transformGet(statement: IrGetValue): IrExpression {
    if (statement.type != statement.symbol.owner.type) {
        return builder.irGet(statement.symbol.owner)
    }
    return statement
}

fun WhenContext.transformSet(statement: IrSetValue): IrExpression {
    statement.value = transformExpression(statement.value)
    return statement
}

object IrNothing : IrExpression() {
    override val startOffset = UNDEFINED_OFFSET
    override val endOffset = UNDEFINED_OFFSET
    override var attributeOwnerId: IrElement
        get() = IrNothing
        set(_) {}
    override var type = pluginContext.irBuiltIns.unitType

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R {
        return visitor.visitConst(
            IrConstImpl(
                -1, -1, pluginContext.irBuiltIns.nothingType,
                IrConstKind.Null, null
            ), data
        )
    }
}