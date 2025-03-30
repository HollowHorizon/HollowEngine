package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irGetField
import org.jetbrains.kotlin.ir.builders.irSetField
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.receiver
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.isSerializable
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.IrNothing
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder

class SerializablePropertiesTransformer(val replaces: MutableMap<IrVariableSymbol, Pair<IrValueParameter, IrField>> = hashMapOf()) :
    IrElementTransformerVoid() {
    lateinit var coroutine: CoroutineGenerator

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if (declaration.type.isSerializable(coroutine.generator)) {
            val field = coroutine.addField(declaration.name, declaration.type)
            coroutine.addSerializableField(field)
            replaces[declaration.symbol] = coroutine.receiver to field
            declaration.builder {
                val initializer = declaration.initializer ?: return IrNothing
                return super.visitSetField(irSetField(irGet(coroutine.receiver), field, initializer))
            }
        }
        return super.visitVariable(declaration)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        replaces[expression.symbol]?.let { (receiver, field) ->
            field.builder {
                return super.visitGetField(irGetField(irGet(receiver), field))
            }
        }
        return super.visitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        replaces[expression.symbol]?.let { (receiver, field) ->
            field.builder {
                return super.visitSetField(irSetField(irGet(receiver), field, expression.value))
            }
        }
        return super.visitSetValue(expression)
    }
}