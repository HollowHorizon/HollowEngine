package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendState
import ru.hollowhorizon.hollowengine.compiler.pluginContext

val suspendObject = pluginContext.referenceClass(SuspendState)!!

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun WhenContext.transformCall(call: IrCall): IrExpression {
    transformParameters(call)

    if (call.isSuspendable() && call.type != pluginContext.irBuiltIns.unitType) {
        val owner = call.symbol.owner
        val coroutineInfo = functionToClass[owner] ?: return call
        val coroutine = coroutineInfo.coroutine.symbol

        val coroutineId = innerCallId++

        val coroutineVar = IrVariableImpl(
            call.startOffset, call.endOffset, IrDeclarationOrigin.DEFINED,
            IrVariableSymbolImpl(), Name.identifier(owner.name.asString() + "Coroutine$coroutineId"), coroutine.defaultType,
            isVar = true, isConst = false, isLateinit = false
        ).apply { parent = builder.parent }
        coroutineVar.initializer = builder.irCallConstructor(coroutine.constructors.first(), emptyList())
        val suspendVar = IrVariableImpl(
            call.startOffset, call.endOffset, IrDeclarationOrigin.DEFINED,
            IrVariableSymbolImpl(), Name.identifier(owner.name.asString() + "Result$coroutineId"), call.type,
            isVar = true, isConst = false, isLateinit = false
        ).apply { parent = builder.parent }

        append(coroutineVar)
        append(builder.irBlock {
            val temp = irTemporary(irCall(coroutine.functionByName("tick")).apply {
                dispatchReceiver = irGet(coroutineVar)
                call.valueArguments.forEachIndexed(::putValueArgument)
                call.typeArguments.forEachIndexed(::putTypeArgument)
                extensionReceiver = call.extensionReceiver
            })
            +irIfThen(irEqeqeq(irGet(temp), irGetObject(suspendObject)), irReturn(irGetObject(suspendObject)))
            suspendVar.initializer = irGet(temp)
            +suspendVar
        })
        nextBranch()
        return builder.irGet(suspendVar)
    }

    return call
}

fun WhenContext.transformParameters(statement: IrFunctionAccessExpression) {
    statement.dispatchReceiver?.let {
        statement.dispatchReceiver = transformExpression(it)
    }
    statement.extensionReceiver?.let {
        statement.extensionReceiver = transformExpression(it)
    }
    statement.valueArguments.filterNotNull().forEachIndexed { i, arg ->
        statement.putValueArgument(i, transformExpression(arg))
    }
}