package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrMutableAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.Ignore
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx

class PropertyTransformer(val function: IrFunction) : IrElementTransformerVoid() {
    class Property(val type: IrType, val name: Name, val symbol: IrValueSymbol)

    val symbols = arrayListOf<Property>()
    val setter = ctx.referenceClass(SuspendContext)!!.functionByName("setProperty")
    val getter = ctx.referenceClass(SuspendContext)!!.functionByName("getProperty")
    val context: IrValueParameter = function.valueParameters.last()

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if (!declaration.isIgnored()) {
            symbols += Property(declaration.type, declaration.name, declaration.symbol)

            declaration.initializer?.let { initializer ->
                val builder = ctx.irBuiltIns.createIrBuilder(
                    function.symbol, function.startOffset, function.endOffset
                )

                return super.visitCall(builder.irCall(setter).apply {
                    dispatchReceiver = builder.irGet(context)
                    putValueArgument(0, builder.irString(declaration.name.asString()))
                    putValueArgument(1, initializer)

                    putTypeArgument(0, initializer.type)
                })

            }
        }

        return super.visitVariable(declaration)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        var expr: IrExpression = expression
        symbols.find { it.symbol == expression.symbol }?.let { variable ->
            val builder = ctx.irBuiltIns.createIrBuilder(function.symbol)

            expr = builder.irCall(getter).apply {
                dispatchReceiver = builder.irGet(context)
                putValueArgument(0, builder.irString(variable.name.asString()))

                putTypeArgument(0, variable.type)
            }
        }
        return super.visitExpression(expr)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        var expr: IrExpression = expression
        symbols.find { it.symbol == expression.symbol }?.let { variable ->
            val builder = ctx.irBuiltIns.createIrBuilder(
                expression.symbol, expression.startOffset, expression.endOffset
            )

            expr = builder.irCall(setter).apply {
                dispatchReceiver = builder.irGet(context)
                putValueArgument(0, builder.irString(variable.name.asString()))
                putValueArgument(1, expression.value)

                putTypeArgument(0, expression.value.type)
            }
        }
        return super.visitExpression(expr)
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrMutableAnnotationContainer.isIgnored(): Boolean {
    val ignore = ctx.referenceClass(Ignore)!!.constructors.first()
    return annotations.map { it.symbol }.contains(ignore)
}