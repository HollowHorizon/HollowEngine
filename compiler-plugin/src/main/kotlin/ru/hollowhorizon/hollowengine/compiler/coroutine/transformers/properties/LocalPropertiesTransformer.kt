@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import ru.hollowhorizon.hollowengine.compiler.identifiers.Restorable


class LocalPropertiesTransformer(
    val locals: MutableMap<IrVariableSymbol, Int> = hashMapOf(),
) : CoroutineTransformer() {
    private var currentBranch: Int = 0

    private fun visitStateBranch(index: Int, branch: IrBranch): IrBranch {
        currentBranch = index
        return super.visitBranch(branch)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        if (coroutine.invokeFunction.body?.statements?.get(1) == expression) {
            expression.branches.forEachIndexed { index, irBranch ->
                visitStateBranch(index, irBranch)
            }
        }
        return expression
    }

    override fun visitConstructorCall(expression: IrConstructorCall): IrExpression {
        return super.visitConstructorCall(expression)
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        if (declaration.parent == coroutine.invokeFunction &&
            !declaration.annotations.hasAnnotation(Restorable)
        ) {
            locals[declaration.symbol] = currentBranch
        }
        return super.visitVariable(declaration)
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val isSameClass = (expression.symbol.owner.parent as? IrFunction)?.parent == coroutine.coroutine
        if (locals[expression.symbol] != currentBranch || !isSameClass) locals.remove(expression.symbol)
        return super.visitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        val isSameClass = (expression.symbol.owner.parent as? IrFunction)?.parent == coroutine.coroutine

        if (locals[expression.symbol] != currentBranch || !isSameClass) locals.remove(expression.symbol)
        return super.visitSetValue(expression)
    }
}