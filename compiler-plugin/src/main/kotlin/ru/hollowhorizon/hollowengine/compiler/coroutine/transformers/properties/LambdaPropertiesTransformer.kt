@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties

import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.allParametersCount
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.FqName
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.receiver
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.sFunctionN
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext


class LambdaPropertiesTransformer(
    private val functionToClass: Map<IrFunction, CoroutineGenerator>,
) : CoroutineTransformer() {
    private val replaces: MutableMap<IrVariableSymbol, Pair<IrValueParameter, IrField>> = hashMapOf()

    private var currentBranch: Int = 0

    fun visitStateBranch(index: Int, branch: IrBranch): IrBranch {
        currentBranch = index
        return super.visitBranch(branch)
    }

    override fun visitWhen(expression: IrWhen): IrExpression {
        if (coroutine.invokeFunction.body?.statements?.getOrNull(1) == expression) {
            expression.branches.forEachIndexed { index, irBranch ->
                visitStateBranch(index, irBranch)
            }
            return expression
        } else {
            return super.visitWhen(expression)
        }
    }

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

    override fun visitVariable(declaration: IrVariable): IrStatement {
        declaration.initializer?.let {
            if (it is IrFunctionExpression && it.function.isSuspendable()) {
                val field = coroutine.addField(declaration.name,
                    sFunctionN(
                        it.function.allParametersCount
                    ).typeWith(it.function.parameters.map { it.type } + pluginContext.irBuiltIns.anyNType)
                )
                field.initializer = field.builder().irExprBody(visitFunctionExpression(it))
                coroutine.addSerializableField(field)
                coroutine.addRestorableField(field, currentBranch, field.builder().run {
                    irCall(field.type.classOrFail.functionByName("restoreState")).apply {
                        dispatchReceiver = irGetField(irGet(coroutine.receiver), field)
                    }
                })
                replaces[declaration.symbol] = coroutine.receiver to field
                return declaration.builder().irBlock { }
            }
        }
        return super.visitVariable(declaration)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        return super.visitCall(expression).apply {
            val function = expression.symbol.owner
            val type = function.parent as? IrClass ?: return@apply
            if (function.name.asString() == "invoke" &&
                type.packageFqName == FqName("kotlin") &&
                type.name.asString().startsWith("Function")
            ) {
                val dispatchReceiver = expression.dispatchReceiver ?: return@apply
                return coroutine.coroutine.builder().run {
                    irCall(dispatchReceiver.type.classOrFail.functionByName("invoke")).apply {
                        this.dispatchReceiver = dispatchReceiver
                        expression.arguments.forEachIndexed { index, irExpression ->
                            this.arguments[index] = irExpression
                        }
                        expression.typeArguments.forEachIndexed { index, irType ->
                            this.typeArguments[index] = irType
                        }
                    }
                }
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
            field.builder {
                return super.visitSetField(irSetField(irGet(receiver), field, expression.value))
            }
        }
        return super.visitSetValue(expression)
    }
}