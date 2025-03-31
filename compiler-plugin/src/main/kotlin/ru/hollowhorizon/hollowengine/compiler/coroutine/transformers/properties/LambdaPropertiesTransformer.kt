@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.primaryConstructor
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable


class LambdaPropertiesTransformer(
    private val functionToClass: Map<IrFunction, CoroutineGenerator>,
) : CoroutineTransformer() {

    override fun visitFunctionExpression(expression: IrFunctionExpression): IrExpression {
        if (expression.function.isSuspendable()) {
            functionToClass[expression.function]?.let { info ->
                expression.function.builder {
                    return irCall(info.coroutine.primaryConstructor!!.symbol, expression.type).apply {
                        dispatchReceiver = irGet(coroutine.coroutine.thisReceiver!!)
                    }
                }
            }
        }
        return super.visitFunctionExpression(expression)
    }
}