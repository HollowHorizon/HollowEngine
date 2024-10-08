package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import ru.hollowhorizon.hollowengine.compiler.identifiers.IntIterator
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx

class PropertyTransformer : IrElementTransformerVoid() {
    val symbols = arrayListOf<IrVariable>()
    val setter = ctx.referenceClass(SuspendContext)!!.functionByName("setProperty")
    val getter = ctx.referenceClass(SuspendContext)!!.functionByName("getProperty")
    lateinit var context: IrValueParameter

    override fun visitVariable(declaration: IrVariable): IrStatement {
        symbols += declaration
        declaration.parents.firstIsInstance<IrFunction>().let { function ->
            context = function.valueParameters.last()
            declaration.initializer?.let { initializer ->
                val builder = ctx.irBuiltIns.createIrBuilder(
                    function.symbol, function.startOffset, function.endOffset
                )

                return builder.irCall(setter).apply {
                    dispatchReceiver = builder.irGet(context)
                    putValueArgument(0, builder.irString(declaration.name.asString()))
                    putValueArgument(1, initializer)

                    putTypeArgument(0, initializer.type)
                }

            }
        }
        return super.visitVariable(declaration)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        symbols.find { it.symbol == expression.symbol }?.let { variable ->
            val builder = ctx.irBuiltIns.createIrBuilder(
                expression.symbol, expression.startOffset, expression.endOffset
            )

            return builder.irCall(getter).apply {
                dispatchReceiver = builder.irGet(context)
                putValueArgument(0, builder.irString(variable.name.asString()))

                putTypeArgument(0, variable.type)
            }
        }
        return super.visitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        symbols.find { it.symbol == expression.symbol }?.let { variable ->
            val builder = ctx.irBuiltIns.createIrBuilder(
                expression.symbol, expression.startOffset, expression.endOffset
            )

            return builder.irCall(setter).apply {
                dispatchReceiver = builder.irGet(context)
                putValueArgument(0, builder.irString(variable.name.asString()))
                putValueArgument(1, expression.value)

                putTypeArgument(0, expression.value.type)
            }
        }
        return super.visitSetValue(expression)
    }

    override fun visitBlock(expression: IrBlock): IrExpression {
        expression.transformChildren(this, null)
        return super.visitBlock(expression)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        expression.transformChildren(this, null)
        return super.visitWhen(expression)
    }
}