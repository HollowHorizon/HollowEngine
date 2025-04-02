package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.getPackageFragment
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.iterators.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.identifiers.*
import ru.hollowhorizon.hollowengine.compiler.irOr
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import kotlin.contracts.ExperimentalContracts

val suspendObject = pluginContext.referenceClass(SuspendState)!!
val resumeObject = pluginContext.referenceClass(ResumeState)!!

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun WhenContext.transformCall(call: IrFunctionAccessExpression): IrExpression {
    transformParameters(call)

    call.dispatchReceiver?.type?.let { type ->
        if (call.symbol.owner.name.asString() == "iterator") {
            return when (type) {
                CharRangeType -> builder.irCall(CharProgressionCtor)
                IntRangeType -> builder.irCall(IntProgressionCtor)
                LongRangeType -> builder.irCall(LongProgressionCtor)
                else -> return@let
            }.apply { putValueArgument(0, call.dispatchReceiver) }
        }
    }

    if (call.isSuspendable() && call.type != pluginContext.irBuiltIns.unitType) {
        val coroutineId = innerCallId++
        val owner = call.symbol.owner

        if (owner.parentAsClass.name.asString().startsWith("SFunction")) {
            return transformSFunctionCall(call, owner, coroutineId)
        }

        return transformSuspendableCall(call, owner, coroutineId)
    }

    return call
}

private fun WhenContext.transformSFunctionCall(call: IrFunctionAccessExpression, owner: IrFunction, coroutineId: Int): IrExpression {
    nextBranch(resume = true)
    val invokeResult = createVariable("invoke", owner, coroutineId, pluginContext.irBuiltIns.anyNType)
    append(invokeResult)

    val coroutineResult = createVariable("result", owner, coroutineId, call.type).apply {
        initializer = builder.irBlock {
            invokeResult.initializer = irCall(owner.parentAsClass.getSimpleFunction("invoke")!!).apply {
                setupCall(call)
            }
            +irIfThen(
                irOr(
                    irEqeqeq(irGet(invokeResult), irGetObject(suspendObject)),
                    irEqeqeq(irGet(invokeResult), irGetObject(resumeObject))
                ),
                irReturn(irGet(invokeResult))
            )
            irGet(invokeResult)
        }
    }
    append(coroutineResult)
    return builder.irGet(coroutineResult)
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun WhenContext.transformSuspendableCall(call: IrFunctionAccessExpression, owner: IrFunction, coroutineId: Int): IrExpression {
    val className = ClassId(owner.getPackageFragment().packageFqName, Name.identifier("${owner.name.asString().capitalize()}\$SerializableCoroutine"))
    val coroutine = functionToClass[owner]?.coroutine?.symbol ?: pluginContext.referenceClass(className)
    ?: error("Class ${className.asSingleFqName().render()} not found!")

    val coroutineVar = createVariable("coroutine", owner, coroutineId, coroutine.defaultType).apply {
        annotations += builder.irCall(Restorable.constructor())
        annotations += builder.irCall(Ignore.constructor())
        initializer = builder.irCallConstructor(coroutine.constructors.first(), emptyList())
    }
    nextBranch(resume = true)
    append(coroutineVar)

    val invokeResult = createVariable("invoke", owner, coroutineId, pluginContext.irBuiltIns.anyNType)
    append(invokeResult)

    val coroutineResult = createVariable("result", owner, coroutineId, call.type).apply {
        initializer = builder.irBlock {
            invokeResult.initializer = irCall(coroutine.functionByName("invoke")).apply {
                dispatchReceiver = irGet(coroutineVar)
                setupCall(call)
            }
            +irIfThen(
                irOr(
                    irEqeqeq(irGet(invokeResult), irGetObject(suspendObject)),
                    irEqeqeq(irGet(invokeResult), irGetObject(resumeObject))
                ),
                irReturn(irGet(invokeResult))
            )
            irGet(invokeResult)
        }
    }
    append(coroutineResult)
    return builder.irGet(coroutineResult)
}

private fun IrFunctionAccessExpression.setupCall(call: IrFunctionAccessExpression) {
    dispatchReceiver = call.dispatchReceiver
    call.valueArguments.forEachIndexed(::putValueArgument)
    call.typeArguments.forEachIndexed(::putTypeArgument)
    extensionReceiver = call.extensionReceiver
}

private fun WhenContext.createVariable(prefix: String, owner: IrFunction, coroutineId: Int, type: IrType) =
    IrVariableImpl(-1, -1, IrDeclarationOrigin.DEFINED, IrVariableSymbolImpl(),
        Name.identifier("$prefix$${owner.name.asString()}\$$coroutineId"), type, false, false, false
    ).apply { parent = builder.parent }

fun WhenContext.transformParameters(statement: IrFunctionAccessExpression) {
    statement.dispatchReceiver = statement.dispatchReceiver?.let(::transformExpression)
    statement.extensionReceiver = statement.extensionReceiver?.let(::transformExpression)
    statement.valueArguments.forEachIndexed { i, arg ->
        statement.putValueArgument(i, arg?.let(::transformExpression))
    }
}