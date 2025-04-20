@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.backend.wasm.ir2wasm.allSuperInterfaces
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.impl.IrVariableImpl
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrFieldSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrVariableSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.renderer.render
import ru.hollowhorizon.hollowengine.compiler.coroutine.NameHelper
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.FakeCoroutine
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.receiver
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.iterators.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.WhenContext
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isAsyncAwait
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.identifiers.*
import ru.hollowhorizon.hollowengine.compiler.irOr
import ru.hollowhorizon.hollowengine.compiler.pluginContext

val suspendObject = pluginContext.referenceClass(SuspendState)!!
val resumeObject = pluginContext.referenceClass(ResumeState)!!
val asyncController = pluginContext.referenceClass(AsyncController)!!

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

    if (call.isAsyncAwait()) {
        nextBranch(resume = true)
        return builder.run {
            irIfThen(irCall(asyncController.getPropertyGetter("isActive")!!).apply {
                dispatchReceiver = call.dispatchReceiver
            }, irReturn(irGetObject(suspendObject)))
        }
    }
    if (call.isSuspendable()) {
        val coroutineId = innerCallId.getOrPut(call.symbol.owner) { 0 }
        innerCallId[call.symbol.owner] = coroutineId + 1
        val owner = call.symbol.owner

        if (owner.parentClassOrNull?.allSuperInterfaces()?.any { it.name.asString().startsWith("SFunction") } == true) {
            return transformSFunctionCall(call, owner, coroutineId)
        }

        return transformSuspendableCall(call, owner, coroutineId)
    }

    return call
}

private fun WhenContext.transformSFunctionCall(
    call: IrFunctionAccessExpression,
    owner: IrFunction,
    coroutineId: Int,
): IrExpression {
    nextBranch(resume = true)
    (whenStatement.branches[nextBranch - 2].result as IrBlock)
        .statements.removeIf {
            if (it !is IrSetField) return@removeIf false
            if (it.receiver != call.dispatchReceiver) return@removeIf false
            append(it)
            true
        }

    val invokeResult = createVariable("invoke", owner, coroutineId, pluginContext.irBuiltIns.anyNType)
    append(builder.irCall(owner.parentAsClass.getSimpleFunction("updateAsyncs")!!).apply {
        setupCall(call)
    })
    append(invokeResult)
    invokeResult.initializer = builder.irCall(owner.parentAsClass.getSimpleFunction("invoke")!!).apply {
        setupCall(call)
    }
    append(builder.run {
        irIfThen(
            irOr(
                irEqeqeq(irGet(invokeResult), irGetObject(suspendObject)),
                irEqeqeq(irGet(invokeResult), irGetObject(resumeObject))
            ),
            irReturn(irGet(invokeResult))
        )
    })

    val coroutineResult = createVariable("result", owner, coroutineId, call.type).apply {
        initializer = builder.irGet(invokeResult)
    }
    append(coroutineResult)
    return builder.irGet(coroutineResult)
}

@OptIn(UnsafeDuringIrConstructionAPI::class, ObsoleteDescriptorBasedAPI::class)
private fun WhenContext.transformSuspendableCall(
    call: IrFunctionAccessExpression,
    owner: IrFunction,
    coroutineId: Int,
): IrExpression {
    val ownerBody = owner.body
    if (owner.isInline && ownerBody != null) {
        val body = ownerBody.deepCopyWithSymbols(builder.parent)
        body.transformChildrenVoid(InlineSuspendableLowering(call, builder.parent as IrFunction))

        return when (body) {
            is IrExpressionBody -> {
                transformExpression(body.expression)
            }

            is IrBlockBody -> {
                transformContainer(body, call.type)
            }

            else -> error("Usupported body expression type")
        }
    }

    val className = ClassId(owner.getPackageFragment().packageFqName, Name.identifier(NameHelper.createName(owner)))
    val coroutine = functionToClass[owner]?.coroutine?.symbol ?: pluginContext.referenceClass(className)
    ?: FakeCoroutine.generate(owner) //error("Class ${className.asSingleFqName().render()} not found!")

    val coroutineVar = createField("coroutine", owner, coroutineId, coroutine.defaultType).apply {
        annotations += builder.irCall(Restorable.constructor())
        annotations += builder.irCall(Ignore.constructor())
        initializer = builder.run {
            irExprBody(irCallConstructor(coroutine.constructors.first(), emptyList()))
        }
    }
    generator.addSerializableField(coroutineVar)
    generator.addRestorableField(coroutineVar, nextBranch - 1, builder.run {
        irCall(coroutineVar.type.classOrFail.functionByName("restoreState")).apply {
            setupCall(call)
            dispatchReceiver = irGetField(irGet(generator.receiver), coroutineVar)
        }
    })
    nextBranch(resume = true)
    generator.coroutine.addChild(coroutineVar)

    val invokeResult = createVariable("invoke", owner, coroutineId, pluginContext.irBuiltIns.anyNType)
    invokeResult.initializer = invokeResult.builder().irCall(coroutine.functionByName("invoke")).apply {
        setupCall(call)
        dispatchReceiver = builder.irGetField(builder.irGet(generator.receiver), coroutineVar)
    }
    append(builder.irCall(coroutine.functionByName("updateAsyncs")).apply {
        setupCall(call)
        dispatchReceiver = builder.irGetField(builder.irGet(generator.receiver), coroutineVar)
    })
    append(invokeResult)
    append(builder.run {
        irIfThen(
            irOr(
                irEqeqeq(irGet(invokeResult), irGetObject(suspendObject)),
                irEqeqeq(irGet(invokeResult), irGetObject(resumeObject))
            ),
            irReturn(irGet(invokeResult))
        )
    })

    val coroutineResult = createVariable("result", owner, coroutineId, call.type).apply {
        initializer = builder.irGet(invokeResult)
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
    IrVariableImpl(
        -1, -1, IrDeclarationOrigin.DEFINED, IrVariableSymbolImpl(),
        Name.identifier("$prefix$${owner.name.asString()}\$$coroutineId"), type, false, false, false
    ).apply { parent = builder.parent }

private fun WhenContext.createField(prefix: String, owner: IrFunction, coroutineId: Int, type: IrType) =
    pluginContext.irFactory.createField(
        -1, -1, IrDeclarationOrigin.DEFINED,
        Name.identifier("$prefix$${NameHelper.createName(owner)}\$$coroutineId"), DescriptorVisibilities.PRIVATE,
        IrFieldSymbolImpl(), type, false, false, false
    ).apply {
        parent = builder.parent
    }

fun WhenContext.transformParameters(statement: IrFunctionAccessExpression) {
    statement.dispatchReceiver = statement.dispatchReceiver?.let(::transformExpression)
    statement.extensionReceiver = statement.extensionReceiver?.let(::transformExpression)
    statement.valueArguments.forEachIndexed { i, arg ->
        statement.putValueArgument(i, arg?.let(::transformExpression))
    }
}