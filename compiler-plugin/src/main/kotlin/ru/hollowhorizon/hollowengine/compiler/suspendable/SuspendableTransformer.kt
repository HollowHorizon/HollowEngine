package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irWhen
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.getPropertySetter
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable

class SuspendableTransformer(val context: IrPluginContext) : IrElementTransformerVoid() {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val suspendGetter = context.referenceClass(SuspendContext)!!.getPropertyGetter("index")!!

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (!declaration.annotations.hasAnnotation(Suspendable.asSingleFqName())) return super.visitFunction(declaration)

        declaration.transformChildrenVoid(PropertyTransformer(declaration))
        val builder = context.irBuiltIns.createIrBuilder(
            declaration.symbol,
            declaration.startOffset,
            declaration.endOffset,
        )

        val suspendContext = declaration.valueParameters.last()

        val newBody = builder.irBlockBody {
            val stateVar = irCall(suspendGetter).apply {
                dispatchReceiver = irGet(suspendContext)
            }

            val whenStatement = irWhen(context.irBuiltIns.unitType, listOf())
            +whenStatement

            declaration.body?.transform(
                SuspendCallTransformer(
                    WhenContext(
                        builder,
                        whenStatement,
                        stateVar,
                        suspendContext,
                        this@SuspendableTransformer.context
                    )
                ), null
            )
        }
        declaration.body = newBody

        return declaration
    }

}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrCall.isSuspendable() = symbol.owner.annotations.hasAnnotation(Suspendable.asSingleFqName())