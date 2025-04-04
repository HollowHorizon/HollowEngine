package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.getNameWithAssert
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder

class OuterPropertyTransformer(outer: CoroutineGenerator, val coroutine: IrClass): IrElementTransformerVoid() {
    val declarations = outer.invokeFunction.parameters.associateBy { it.symbol }
    val fields = hashMapOf<IrValueSymbol, IrField>()

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        declarations[expression.symbol]?.let {
            val field = fields.getOrPut(expression.symbol) {
                coroutine.addField {
                    type = it.type
                    name = it.name
                }
            }
            return field.builder().run { irGetField(irGet(coroutine.thisReceiver!!), field) }
        }
        return super.visitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        declarations[expression.symbol]?.let {
            val field = fields.getOrPut(expression.symbol) {
                coroutine.addField {
                    type = it.type
                    name = it.name
                }
            }
            return field.builder().run { irSetField(irGet(coroutine.thisReceiver!!), field, expression.value) }
        }
        return super.visitSetValue(expression)
    }
}