package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx

class PropertyTransformer(val function: IrFunction) : IrElementTransformerVoid() {
    class Property(val type: IrType, val name: Name, val symbol: IrValueSymbol)

    val symbols = arrayListOf<Property>()
    val getters = arrayListOf<IrSimpleFunction>()
    val setters = arrayListOf<IrSimpleFunction>()

    val setter = ctx.referenceClass(SuspendContext)!!.functionByName("setProperty")
    val getter = ctx.referenceClass(SuspendContext)!!.functionByName("getProperty")
    val context: IrValueParameter = function.valueParameters.last()

    override fun visitProperty(declaration: IrProperty): IrStatement {
        declaration.backingField?.let {
            declaration.getter?.let { getters += it }
            declaration.setter?.let { setters += it }

            it.initializer?.let { initializer ->
                val builder = ctx.irBuiltIns.createIrBuilder(
                    function.symbol, function.startOffset, function.endOffset
                )

                return builder.irCall(setter).apply {
                    dispatchReceiver = builder.irGet(context)
                    putValueArgument(0, builder.irString(declaration.name.asString()))
                    putValueArgument(1, initializer.expression)

                    putTypeArgument(0, initializer.expression.type)
                }
            }
        }
        return super.visitProperty(declaration)
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        symbols += Property(declaration.type, declaration.name, declaration.symbol)

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

    override fun visitCall(expression: IrCall): IrExpression {
        var expr = expression
        getters.find { it.symbol == expression.symbol }?.let { _ ->
            val propertyName = expression.symbol.owner.name.asStringStripSpecialMarkers().substring(4)
            val builder = ctx.irBuiltIns.createIrBuilder(function.symbol)

            expr = builder.irCall(this.getter, expression.type, origin = IrStatementOrigin.EQ).apply {
                dispatchReceiver = builder.irGet(this@PropertyTransformer.context)
                putValueArgument(0, builder.irString(propertyName))
                putTypeArgument(0, expression.type)
            }
        }
        setters.find { it.symbol == expression.symbol }?.let { _ ->
            val propertyName = expression.symbol.owner.name.asStringStripSpecialMarkers().substring(4)
            val builder = ctx.irBuiltIns.createIrBuilder(function.symbol)

            expr = builder.irCall(this.setter).apply {
                dispatchReceiver = builder.irGet(context)
                putValueArgument(0, builder.irString(propertyName))
                putValueArgument(1, expression.valueArguments[0])

                putTypeArgument(0, expression.type)
            }
        }
        return super.visitCall(expr)
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

    override fun visitBlock(expression: IrBlock): IrExpression {
        expression.transformChildren(this, null)
        return super.visitBlock(expression)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        expression.transformChildren(this, null)
        return super.visitWhen(expression)
    }
}