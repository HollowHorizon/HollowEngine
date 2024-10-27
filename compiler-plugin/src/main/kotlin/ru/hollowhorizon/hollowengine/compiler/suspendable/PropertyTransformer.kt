package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
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
    val setter = ctx.referenceClass(SuspendContext)!!.functionByName("setProperty")
    val getter = ctx.referenceClass(SuspendContext)!!.functionByName("getProperty")
    val context: IrValueParameter = function.valueParameters.last()

    override fun visitProperty(declaration: IrProperty): IrStatement {
        declaration.backingField?.let {
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
        symbols.find { it.symbol == expression.symbol }?.let { variable ->
            val builder = ctx.irBuiltIns.createIrBuilder(function.symbol)

            return builder.irCall(getter).apply {
                dispatchReceiver = builder.irGet(context)
                putValueArgument(0, builder.irString(variable.name.asString()))

                putTypeArgument(0, variable.type)
            }
        }
        return super.visitGetValue(expression)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        if (expression.origin == IrStatementOrigin.GET_PROPERTY) {
            val propertyName = expression.symbol.owner.name.asStringStripSpecialMarkers().substring(4)
            val builder = ctx.irBuiltIns.createIrBuilder(function.symbol)

            return builder.irCall(getter).apply {
                dispatchReceiver = builder.irGet(this@PropertyTransformer.context)
                putValueArgument(0, builder.irString(propertyName))

                putTypeArgument(0, expression.type)
            }
        }
        if(expression.origin == IrStatementOrigin.EQ) {
            val propertyName = expression.symbol.owner.name.asStringStripSpecialMarkers().substring(4)
            val builder = ctx.irBuiltIns.createIrBuilder(function.symbol)

            return builder.irCall(setter).apply {
                dispatchReceiver = builder.irGet(context)
                putValueArgument(0, builder.irString(propertyName))
                putValueArgument(1, expression.valueArguments[0])

                putTypeArgument(0, expression.type)
            }
        }
        return super.visitCall(expression)
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