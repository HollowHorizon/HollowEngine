@file:OptIn(ObsoleteDescriptorBasedAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class InlineSuspendableLowering(val call: IrFunctionAccessExpression, val parent: IrFunction): IrElementTransformerVoid() {
    override fun visitClassReference(expression: IrClassReference): IrExpression {
        // Обработка reified T::class
        (expression.classType.classifierOrNull as? IrTypeParameterSymbol)?.let { type ->
            val index = type.descriptor.index
            expression.classType = call.getTypeArgument(index)!!
        }
        return super.visitClassReference(expression)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitDeclaration(declaration: IrDeclarationBase): IrStatement {
        declaration.parent = parent
        return super.visitDeclaration(declaration)
    }

    override fun visitGetClass(expression: IrGetClass): IrExpression {
        // Обработка выражений вроде `someVar::class`
        (expression.argument.type.classifierOrNull as? IrTypeParameterSymbol)?.let { type ->
            val index = type.owner.index
            call.getTypeArgument(index)?.let { newType ->
                expression.argument.type = newType
            }
        }
        return super.visitGetClass(expression)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        // Обработка inline параметров (например, лямбд)
        val parameter = expression.symbol.owner
        if (parameter is IrValueParameter) {
            val index = call.symbol.owner.valueParameters.indexOfFirst { it.symbol == expression.symbol }
            if (index != -1 && index < call.valueArgumentsCount) {
                call.getValueArgument(index)?.let { return it }
            }
        }
        return super.visitGetValue(expression)
    }

    override fun visitTypeOperator(expression: IrTypeOperatorCall): IrExpression {
        // Обработка is/as с generic типами
        (expression.typeOperand.classifierOrNull as? IrTypeParameterSymbol)?.let { type ->
            val index = type.owner.index
            call.getTypeArgument(index)?.let { expression.typeOperand = it }
        }
        return super.visitTypeOperator(expression)
    }

    override fun visitReturn(expression: IrReturn): IrExpression {
        val returnExp = super.visitReturn(expression)
        if(returnExp is IrReturn) {
            return returnExp.value
        }
        return returnExp
    }
}