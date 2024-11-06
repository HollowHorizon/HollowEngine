package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irBlock
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrMutableAnnotationContainer
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.Ignore
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class PropertyTransformer(val function: IrFunction, val context: IrExpression) : IrElementTransformerVoid() {
    class Property(val type: IrType, val name: Name, val symbol: IrValueSymbol)

    val controllers = ArrayList<IrVariable>()
    private val async = pluginContext.referenceFunctions(
        CallableId(
            FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"),
            Name.identifier("async")
        )
    ).single()
    private val symbols = arrayListOf<Property>()
    private val setter = pluginContext.referenceClass(SuspendContext)!!.functionByName("setProperty")
    private val getter = pluginContext.referenceClass(SuspendContext)!!.functionByName("getProperty")

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if (!declaration.isIgnored()) {
            declaration.initializer?.let { initializer ->
                val builder = pluginContext.irBuiltIns.createIrBuilder(
                    function.symbol, function.startOffset, function.endOffset
                )

                if (initializer is IrCall && initializer.symbol == async) {
                    initializer.transformChildrenVoid(this)
                    (initializer.getValueArgument(0) as? IrFunctionExpression)?.function?.apply {
                        transform(SuspendableTransformer(), null)
                        returnType = pluginContext.irBuiltIns.anyType
                    }
                    controllers += declaration
                    return builder.irBlock {}
                }

                symbols += Property(declaration.type, declaration.name, declaration.symbol)

                return super.visitCall(builder.irCall(setter).apply {
                    dispatchReceiver = context
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
            val builder = pluginContext.irBuiltIns.createIrBuilder(function.symbol)

            expr = builder.irCall(getter).apply {
                dispatchReceiver = context
                putValueArgument(0, builder.irString(variable.name.asString()))

                putTypeArgument(0, variable.type)
            }
        }
        return super.visitExpression(expr)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        var expr: IrExpression = expression
        symbols.find { it.symbol == expression.symbol }?.let { variable ->
            val builder = pluginContext.irBuiltIns.createIrBuilder(
                expression.symbol, expression.startOffset, expression.endOffset
            )

            expr = builder.irCall(setter).apply {
                dispatchReceiver = context
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
    val ignore = pluginContext.referenceClass(Ignore)!!.constructors.first()
    return annotations.map { it.symbol }.contains(ignore)
}