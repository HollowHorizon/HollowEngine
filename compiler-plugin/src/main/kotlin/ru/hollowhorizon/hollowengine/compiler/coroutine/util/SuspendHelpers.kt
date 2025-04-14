@file:OptIn(ExperimentalContracts::class, UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.util

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrFunctionAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.superTypes
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.checker.SimpleClassicTypeSystemContext.hasAnnotation
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.isClassWithNamePrefix
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFUNCTION_PACKAGE
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncController
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

fun IrFunction.isSuspendable() = annotations.hasAnnotation(Suspendable)
fun IrType.isSuspendable(): Boolean {
    val type = classOrNull ?: return false

    return type.isClassWithNamePrefix("SFunction", SFUNCTION_PACKAGE) || this.type.hasAnnotation(Suspendable) || type.superTypes().any { it.isSuspendable() }
}
fun IrType.isAsyncController() = classOrNull == pluginContext.referenceClass(AsyncController)

fun IrFunctionAccessExpression.isSuspendable() = symbol.owner.isSuspendable()

fun IrFunctionAccessExpression.isAsyncAwait() =
    pluginContext.referenceClass(AsyncController)!!.functionByName("await") == symbol

fun IrBuilderWithScope.throwIllegalStateException(message: String) =
    irThrow(irCall(context.irBuiltIns.illegalArgumentExceptionSymbol).apply {
        putValueArgument(0, irString(message))
    })


@OptIn(ExperimentalContracts::class)
inline fun <T : IrDeclaration> T.builder(body: DeclarationIrBuilder.(T) -> Unit = {}): DeclarationIrBuilder {
    contract {
        callsInPlace(body, InvocationKind.EXACTLY_ONCE)
    }

    return pluginContext.irBuiltIns.createIrBuilder(symbol, startOffset, endOffset).apply { body(this@builder) }
}