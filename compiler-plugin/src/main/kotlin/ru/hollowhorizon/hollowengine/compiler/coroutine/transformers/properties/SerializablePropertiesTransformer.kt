package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.util.fields
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.receiver
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.isSerializable
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.IrNothing
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder

class SerializablePropertiesTransformer(
    private val replaces: MutableMap<IrVariableSymbol, Pair<IrValueParameter, IrField>> = hashMapOf(),
) : CoroutineTransformer() {

    var filter: MutableMap<IrVariableSymbol, Int> = mutableMapOf()

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if (declaration.symbol in filter) return super.visitVariable(
            declaration
        )

        if (declaration.type.isSerializable(coroutine.generator)) {
            declaration.builder {
                if (declaration.name == Name.special("<stateIndex>")) {
                    val field = coroutine.coroutine.fields.single { it.name == Name.special("<stateIndex>") }
                    val initializer = declaration.initializer ?: return IrNothing
                    field.initializer = irExprBody(initializer)
                    declaration.name = Name.identifier("<stateIndex>")
                    declaration.initializer = irGetField(irGet(coroutine.receiver), field)
                    filter[declaration.symbol] = -1
                    replaces[declaration.symbol] = coroutine.receiver to field
                    coroutine.addSerializableField(field)
                    return declaration
                }

                val field = coroutine.addField(declaration.name, declaration.type)
                replaces[declaration.symbol] = coroutine.receiver to field
                coroutine.addSerializableField(field)

                val initializer = declaration.initializer ?: return IrNothing
                return super.visitSetField(irSetField(irGet(coroutine.receiver), field, initializer))
            }
        }
        return super.visitVariable(declaration)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        if (expression.symbol in filter) return super.visitGetValue(expression)
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

