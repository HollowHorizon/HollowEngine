package ru.hollowhorizon.hollowengine.compiler.coroutine.util

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

fun IrFunction.isSuspendable() = annotations.hasAnnotation(Suspendable)

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrCall.isSuspendable() = symbol.owner.isSuspendable()

fun IrBuilderWithScope.throwIllegalStateException(message: String) =
    irThrow(irCall(context.irBuiltIns.illegalArgumentExceptionSymbol).apply {
        putValueArgument(0, irString(message))
    })


inline fun <T : IrDeclaration> T.builder(body: DeclarationIrBuilder.(T) -> Unit = {}) =
    pluginContext.irBuiltIns.createIrBuilder(symbol, startOffset, endOffset).apply { body(this@builder) }