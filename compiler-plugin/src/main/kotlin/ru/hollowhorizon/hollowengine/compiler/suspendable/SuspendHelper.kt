package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.hasAnnotation
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Dialogue
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

fun IrFunction.isSuspendable(): Boolean {
    val asyncContext = pluginContext.referenceClass(AsyncContext)?.defaultType
    val dialogueContext = pluginContext.referenceClass(Dialogue)?.defaultType

    return when {
        annotations.hasAnnotation(Suspendable.asSingleFqName()) -> true
        origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA -> {
            extensionReceiverParameter?.type?.let { it == asyncContext || it == dialogueContext } == true
        }

        else -> false
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrFunction.suspendableContext: IrExpression
    get() {
        val builder = pluginContext.irBuiltIns.createIrBuilder(symbol, startOffset, endOffset)
        val asyncContext = pluginContext.referenceClass(AsyncContext)
        val asyncGetter = asyncContext?.getPropertyGetter("context")
        val dialogueContext = pluginContext.referenceClass(Dialogue)
        val dialogueGetter = dialogueContext?.getPropertyGetter("context")
        val receiver = extensionReceiverParameter

        return when {
            annotations.hasAnnotation(Suspendable.asSingleFqName()) -> builder.irGet(valueParameters.last())
            origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA -> {
                val context = receiver?.type?.let {
                    when (it) {
                        asyncContext?.defaultType -> builder.irCall(asyncGetter!!).apply { dispatchReceiver = builder.irGet(receiver) }
                        dialogueContext?.defaultType -> builder.irCall(dialogueGetter!!).apply { dispatchReceiver = builder.irGet(receiver) }
                        else -> error("Unsupported suspend context type")
                    }
                }

                context ?: error("Context not found")
            }

            else -> error("Unsupported suspend function")
        }
    }