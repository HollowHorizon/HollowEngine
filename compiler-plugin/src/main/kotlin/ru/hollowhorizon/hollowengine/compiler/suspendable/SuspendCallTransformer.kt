package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.transformStatement
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import ru.hollowhorizon.hollowengine.compiler.identifiers.ResumeState
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendState
import ru.hollowhorizon.hollowengine.compiler.suspendable.FunctionTransformer.ctx

class SuspendCallTransformer(
    private val whenContext: WhenContext,
) : IrElementTransformerVoid() {

    override fun visitBody(body: IrBody): IrBody {
        body.statements.forEach(::transformStatement)
        with(whenContext) {
            with(builder) {
                whenStatement.branches += irElseBranch(throwIllegalStateException("Invalid index: ${whenStatement.branches.size}"))
            }
        }
        return body
    }

    fun transformStatement(statement: IrStatement, parent: IrExpression? = null): IrExpression? {
        return when (statement) {
            is IrCall -> transformCall(statement, parent)
            is IrBlock -> {
                statement.statements.forEach { transformStatement(it, parent) }
                return null
            }

            is IrWhileLoop -> {
                val result = transformStatement(statement.condition, statement)
                if(result != null) statement.condition = result
                with(whenContext) {
                    switchState()
                    returnResume()
                    nextBranch()
                    val elseBranch = with(builder) {
                        irCall(suspendSetter).apply {
                            dispatchReceiver = irGet(suspendContext)
                        }
                    }
                    val currentBranch = nextBranch - 1
                    with(builder) {
                        append(
                            irIfThenElse(
                                context.irBuiltIns.unitType, statement.condition,
                                irCall(suspendSetter).apply {
                                    dispatchReceiver = irGet(suspendContext)
                                    putValueArgument(0, irInt(nextBranch))
                                }, elseBranch
                            )
                        )
                    }
                    returnResume()
                    nextBranch()
                    statement.body?.let { transformStatement(it, null) }
                    append(builder.irCall(suspendSetter).apply {
                        dispatchReceiver = builder.irGet(suspendContext)
                        putValueArgument(0, builder.irInt(currentBranch))
                    })
                    returnSuspend()
                    nextBranch()
                    elseBranch.putValueArgument(0, builder.irInt(nextBranch - 1))
                }
                return null
            }

            is IrDoWhileLoop -> {
                val result = transformStatement(statement.condition, statement)
                if(result != null) statement.condition = result
                with(whenContext) {
                    switchState()
                    returnResume()
                    nextBranch()
                    val currentBranch = nextBranch - 1
                    statement.body?.let { transformStatement(it, null) }
                    append(builder.irCall(suspendSetter).apply {
                        dispatchReceiver = builder.irGet(suspendContext)
                        putValueArgument(0, builder.irInt(currentBranch+1))
                    })
                    returnSuspend()
                    nextBranch()
                    //eff
                    val elseBranch = with(builder) {
                        irCall(suspendSetter).apply {
                            dispatchReceiver = irGet(suspendContext)
                        }
                    }
                    with(builder) {
                        append(
                            irIfThenElse(
                                context.irBuiltIns.unitType, statement.condition,
                                irCall(suspendSetter).apply {
                                    dispatchReceiver = irGet(suspendContext)
                                    putValueArgument(0, irInt(currentBranch))
                                }, elseBranch
                            )
                        )
                    }
                    returnResume()
                    nextBranch()

                    elseBranch.putValueArgument(0, builder.irInt(nextBranch - 1))
                }
                return null
            }

            is IrReturn -> {
                val result = transformStatement(statement.value, statement)
                whenContext.append(whenContext.builder.irReturn(result ?: statement.value))
                return null
            }

            is IrTypeOperatorCall -> transformStatement(statement.argument, parent)
            is IrGetValue, is IrConst<*> -> return null
            is IrSetValue -> transformStatement(statement.value, parent)
            is IrWhen -> {
                statement.branches.forEach {
                    val result = transformStatement(it.condition, statement)
                    if (result != null) {
                        it.condition = result
                    }

                    with(whenContext) {
                        val elseBranch = with(builder) {
                            irCall(suspendSetter).apply {
                                dispatchReceiver = irGet(suspendContext)
                            }
                        }
                        with(builder) {
                            append(
                                irIfThenElse(
                                    context.irBuiltIns.unitType, it.condition,
                                    irCall(suspendSetter).apply {
                                        dispatchReceiver = irGet(suspendContext)
                                        putValueArgument(0, irInt(nextBranch))
                                    }, elseBranch
                                )
                            )
                        }
                        returnResume()
                        nextBranch()
                        transformStatement(it.result, null)
                        switchState()
                        returnResume()
                        nextBranch()
                        elseBranch.putValueArgument(0, builder.irInt(nextBranch - 1))
                    }
                }
                return null
            }
            is IrComposite -> {
                statement.statements.forEach { transformStatement(it, parent) }
                return null
            }
            else -> error("Unexpected statement $statement")
        }
    }

    private fun transformCall(call: IrCall, parent: IrExpression? = null): IrExpression? {
        call.valueArguments.filterNotNull().forEachIndexed { i, arg ->
            val result = transformStatement(arg, call)
            result?.let { call.putValueArgument(i, result) }
        }

        val name = call.symbol.owner.name.identifier
        val argId = (parent as? IrCall)?.valueArguments?.indexOf(call) ?: -1

        with(whenContext) {
            if (call.isSuspendable()) {
                switchState()
                returnResume()
                append(builder.irCall(setter).apply {
                    dispatchReceiver = builder.irGet(whenContext.suspendContext)
                    putValueArgument(0, builder.irString("<inner-context>"))
                    putValueArgument(1, builder.irCall(suspendContextSymbol.constructors.first()))
                })
                nextBranch()
                append(builder.irBlock {
                    val temp = irTemporary(irCall(call.symbol).apply {
                        call.valueArguments.forEachIndexed(::putValueArgument)
                        call.typeArguments.forEachIndexed(::putTypeArgument)
                        val owner = call.symbol.owner
                        if (owner.valueParameters.last().type != ctx.referenceClass(SuspendContext)?.defaultType) {
                            owner.returnType = ctx.irBuiltIns.anyNType
                            owner.addValueParameter("suspendContext", ctx.referenceClass(SuspendContext)!!.defaultType)
                        }
                        putValueArgument(owner.valueParameters.size - 1, irCall(getter).apply {
                            dispatchReceiver = irGet(whenContext.suspendContext)
                            putValueArgument(0, irString("<inner-context>"))
                        })
                    }, nameHint = "result")
                    +irIfThenElse(ctx.irBuiltIns.unitType, irCall(ctx.irBuiltIns.ororSymbol).apply {
                        putValueArgument(
                            0, irEquals(
                                irGet(temp), irGetObject(ctx.referenceClass(ResumeState)!!), IrStatementOrigin.EQEQEQ
                            )
                        )
                        putValueArgument(
                            1, irEquals(
                                irGet(temp), irGetObject(ctx.referenceClass(SuspendState)!!), IrStatementOrigin.EQEQEQ
                            )
                        )
                    }, irReturn(irGet(temp)), irBlock {
                        +irCall(remover).apply {
                            dispatchReceiver = irGet(suspendContext)
                            putValueArgument(0, irString("<inner-context>"))
                        }
                        if (parent != null) {
                            +irCall(setter).apply {
                                dispatchReceiver = irGet(suspendContext)
                                putValueArgument(0, irString("<suspend-arg-$name-$argId>"))
                                putValueArgument(1, irGet(temp))
                                putTypeArgument(0, temp.type)
                            }
                        }
                    })
                })
                return builder.irCall(getter).apply {
                    dispatchReceiver = builder.irGet(suspendContext)
                    putValueArgument(0, builder.irString("<suspend-arg-$name-$argId>"))
                }
            } else if (parent == null) {
                append(call)
            }
        }
        return null
    }
}