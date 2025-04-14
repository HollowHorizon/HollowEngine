package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.receiver
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.IrNothing
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncController
import ru.hollowhorizon.hollowengine.compiler.identifiers.Ignore
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class RestorablePropertyTransformer(
    private val replaces: MutableMap<IrVariableSymbol, Pair<IrValueParameter, IrField>> = hashMapOf(),
) : CoroutineTransformer() {
    private var currentBranch: Int = 0
    var filter: MutableMap<IrVariableSymbol, Int> = mutableMapOf()

    fun visitStateBranch(index: Int, branch: IrBranch): IrBranch {
        currentBranch = index
        return super.visitBranch(branch)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        if (coroutine.invokeFunction.body?.statements?.get(1) == expression) {
            expression.branches.forEachIndexed { index, irBranch ->
                visitStateBranch(index, irBranch)
            }
        }
        return super.visitWhen(expression)
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if(declaration.symbol in filter || declaration.parent != coroutine.invokeFunction) return super.visitVariable(declaration)
        var isIgnored = declaration.annotations.hasAnnotation(Ignore)


        val field = coroutine.addField(declaration.name, declaration.type)
        if(field.type.classOrNull == pluginContext.referenceClass(AsyncController)) {
            isIgnored = true
            coroutine.addAsync(field)
        }
        declaration.initializer?.let {
            if(isIgnored) {
                field.initializer = field.builder().irExprBody(it)
            } else {
                coroutine.addRestorableField(field, currentBranch, it)
            }
        }
        replaces[declaration.symbol] = coroutine.receiver to field
        declaration.builder {
            if(isIgnored) {
                return irBlock {}
            } else {
                val initializer = declaration.initializer ?: return IrNothing
                return super.visitSetField(irSetField(irGet(coroutine.receiver), field, initializer))
            }
        }
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
            coroutine.addRestorableField(field, currentBranch, expression.value)
            field.builder {
                return super.visitSetField(irSetField(irGet(receiver), field, expression.value))
            }
        }
        return super.visitSetValue(expression)
    }
}