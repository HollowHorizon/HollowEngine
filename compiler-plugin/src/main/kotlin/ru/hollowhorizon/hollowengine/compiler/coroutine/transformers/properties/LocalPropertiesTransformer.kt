@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor
import ru.hollowhorizon.hollowengine.compiler.identifiers.Restorable


class LocalPropertiesTransformer(
    val locals: MutableMap<IrVariableSymbol, Int> = hashMapOf(),
) : CoroutineTransformer() {
    private var currentBranch: Int = 0
    private val reverseDependencies: MutableMap<IrVariableSymbol, MutableSet<IrVariableSymbol>> = hashMapOf()

    private fun visitStateBranch(index: Int, branch: IrBranch): IrBranch {
        currentBranch = index
        return super.visitBranch(branch)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        if (coroutine.invokeFunction.body?.statements?.get(1) == expression) {
            expression.branches.forEachIndexed { index, irBranch ->
                visitStateBranch(index, irBranch)
            }
            return expression
        } else {
            return super.visitWhen(expression)
        }
    }

    override fun visitVariable(declaration: IrVariable): IrStatement {
        val symbol = declaration.symbol
        if (declaration.parent == coroutine.invokeFunction &&
            !declaration.annotations.hasAnnotation(Restorable)
        ) {
            // Собираем переменные, использованные в инициализаторе
            val usedSymbols = mutableSetOf<IrVariableSymbol>()
            declaration.initializer?.accept(object : IrElementVisitor<Unit, Unit> {
                override fun visitElement(element: IrElement, data: Unit) {
                    element.acceptChildren(this, data)
                }

                override fun visitGetValue(expression: IrGetValue, data: Unit) {
                    val usedSymbol = expression.symbol
                    val owner = usedSymbol.owner
                    if (owner is IrVariable) {
                        usedSymbols.add(owner.symbol)
                    }
                    super.visitGetValue(expression, data)
                }
            }, Unit)

            // Обновляем reverseDependencies
            reverseDependencies.getOrPut(symbol) { mutableSetOf() }.addAll(usedSymbols)

            // Добавляем текущую переменную в locals
            locals[symbol] = currentBranch
        }
        return super.visitVariable(declaration)
    }

    private fun removeVariable(symbol: IrVariableSymbol) {
        if (locals.remove(symbol) != null) {
            // Рекурсивно удаляем все переменные, зависящие от этой
            reverseDependencies[symbol]?.forEach { dependent ->
                removeVariable(dependent)
            }
        }
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val symbol = expression.symbol
        val isSameClass = (symbol.owner.parent as? IrFunction)?.parent == coroutine.coroutine
        val branch = locals[symbol]

        if (branch != null && (branch != currentBranch || !isSameClass) && symbol is IrVariableSymbol) {
            removeVariable(symbol)
        }
        return super.visitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        val symbol = expression.symbol
        val isSameClass = (symbol.owner.parent as? IrFunction)?.parent == coroutine.coroutine
        val branch = locals[symbol]

        // Собираем переменные, использованные в инициализаторе
        val usedSymbols = mutableSetOf<IrVariableSymbol>()
        expression.value.accept(object : IrElementVisitor<Unit, Unit> {
            override fun visitElement(element: IrElement, data: Unit) {
                element.acceptChildren(this, data)
            }

            override fun visitGetValue(expression: IrGetValue, data: Unit) {
                val usedSymbol = expression.symbol
                val owner = usedSymbol.owner
                if (owner is IrVariable) {
                    usedSymbols.add(owner.symbol)
                }
                super.visitGetValue(expression, data)
            }
        }, Unit)

        (symbol as? IrVariableSymbol)?.let {
            // Обновляем reverseDependencies
            reverseDependencies.getOrPut(it) { mutableSetOf() }.addAll(usedSymbols)
        }

        if (branch != null && (branch != currentBranch || !isSameClass) && symbol is IrVariableSymbol) {
            removeVariable(symbol)
        }

        return super.visitSetValue(expression)
    }
}