package ru.hollowhorizon.hollowengine.compiler.suspendable

import org.jetbrains.kotlin.backend.common.lower.irThrow
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.backend.js.utils.typeArguments
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrBranchImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrWhenImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.AsyncController
import ru.hollowhorizon.hollowengine.compiler.identifiers.ResumeState
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
import ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendState
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class SuspendCallTransformer(
    private val whenContext: WhenContext,
    private val controllers: ArrayList<IrVariable>,
) : IrElementTransformerVoid() {
    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val asyncControllers = pluginContext.referenceClass(SuspendContext)!!.getPropertyGetter("asyncControllers")!!
    val asyncContext = pluginContext.referenceClass(AsyncContext)!!
    val asyncController = pluginContext.referenceClass(AsyncController)!!
    val asyncStart = asyncController.functionByName("start")
    val asyncResume = asyncController.functionByName("start")
    val asyncStop = asyncController.functionByName("stop")
    val asyncPause = asyncController.functionByName("pause")
    val asyncJoin = asyncController.functionByName("join")

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    val asyncIsEnd = asyncController.getPropertyGetter("isEnd")!!
    val HashSetAdd = pluginContext.referenceClass(ClassId(FqName("java.util"), Name.identifier("HashSet")))!!
        .functions.single { it.owner.valueParameters.size == 1 && it.owner.name.identifier == "add" }
    val HashSetRemove = pluginContext.referenceClass(ClassId(FqName("java.util"), Name.identifier("HashSet")))!!
        .functions.single { it.owner.valueParameters.size == 1 && it.owner.name.identifier == "remove" }
    val await = pluginContext.referenceFunctions(
        CallableId(
            FqName("ru.hollowhorizon.hollowengine.compiler.suspendable"),
            Name.identifier("await")
        )
    ).first()

    override fun visitBody(body: IrBody): IrBody {
        body.statements.forEach(::transformStatement)
        with(whenContext) {
            with(builder) {
                whenStatement.branches += irElseBranch(throwIllegalStateException("Invalid index: ${whenStatement.branches.size}"))
            }
        }
        return body
    }

    fun transformStatement(statement: IrStatement, shouldReplace: Boolean = false): IrExpression? {
        return when (statement) {
            is IrCall -> transformCall(statement, shouldReplace)
            is IrBlock -> {
                statement.statements.forEach { transformStatement(it, shouldReplace) }
                return null
            }

            is IrWhileLoop -> {
                val result = transformStatement(statement.condition, true)
                if (result != null) statement.condition = result
                with(whenContext) {
                    switchState()
                    returnResume()
                    nextBranch()
                    val elseBranch = with(builder) {
                        irCall(suspendSetter).apply {
                            dispatchReceiver = suspendContext
                        }
                    }
                    val currentBranch = nextBranch - 1
                    with(builder) {
                        append(
                            irIfThenElse(
                                context.irBuiltIns.unitType, statement.condition,
                                irCall(suspendSetter).apply {
                                    dispatchReceiver = suspendContext
                                    putValueArgument(0, irInt(nextBranch))
                                }, elseBranch, null
                            )
                        )
                    }
                    returnResume()
                    nextBranch()
                    statement.body?.let { transformStatement(it, false) }
                    append(builder.irCall(suspendSetter).apply {
                        dispatchReceiver = suspendContext
                        putValueArgument(0, builder.irInt(currentBranch))
                    })
                    returnSuspend()
                    nextBranch()
                    elseBranch.putValueArgument(0, builder.irInt(nextBranch - 1))
                }
                return null
            }

            is IrDoWhileLoop -> {
                val result = transformStatement(statement.condition, true)
                if (result != null) statement.condition = result
                with(whenContext) {
                    switchState()
                    returnResume()
                    nextBranch()
                    val currentBranch = nextBranch - 1
                    statement.body?.let { transformStatement(it, false) }
                    append(builder.irCall(suspendSetter).apply {
                        dispatchReceiver = suspendContext
                        putValueArgument(0, builder.irInt(currentBranch + 1))
                    })
                    returnSuspend()
                    nextBranch()
                    //eff
                    val elseBranch = with(builder) {
                        irCall(suspendSetter).apply {
                            dispatchReceiver = suspendContext
                        }
                    }
                    with(builder) {
                        append(
                            irIfThenElse(
                                context.irBuiltIns.unitType, statement.condition,
                                irCall(suspendSetter).apply {
                                    dispatchReceiver = suspendContext
                                    putValueArgument(0, irInt(currentBranch))
                                }, elseBranch, null
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
                val result = transformStatement(statement.value, true)
                whenContext.append(whenContext.builder.irReturn(result ?: statement.value))
                return null
            }

            is IrTypeOperatorCall -> transformStatement(statement.argument, shouldReplace)
            is IrGetValue -> {
                if (statement.type != statement.symbol.owner.type) {
                    return whenContext.builder.irGet(statement.symbol.owner)
                }
                return null
            }

            is IrConst, is IrGetField, is IrGetSingletonValue -> return null
            is IrFunctionExpression -> {
                val function = statement.function

                return null
            }

            is IrSetValue -> transformStatement(statement.value, shouldReplace)
            is IrWhen -> {
                statement.branches.forEach {
                    val result = transformStatement(it.condition, true)
                    if (result != null) {
                        it.condition = result
                    }

                    with(whenContext) {
                        val elseBranch = with(builder) {
                            irCall(suspendSetter).apply {
                                dispatchReceiver = suspendContext
                            }
                        }
                        with(builder) {
                            append(
                                irIfThenElse(
                                    context.irBuiltIns.unitType, it.condition,
                                    irCall(suspendSetter).apply {
                                        dispatchReceiver = suspendContext
                                        putValueArgument(0, irInt(nextBranch))
                                    }, elseBranch, null
                                )
                            )
                        }
                        returnResume()
                        nextBranch()
                        transformStatement(it.result, false)
                        switchState()
                        returnResume()
                        nextBranch()
                        elseBranch.putValueArgument(0, builder.irInt(nextBranch - 1))
                    }
                }
                return null
            }

            is IrComposite -> {
                statement.statements.forEach { transformStatement(it, shouldReplace) }
                return null
            }

            is IrVariable -> {
                statement.initializer?.let {
                    val result = transformStatement(it, true)
                    result?.let {
                        statement.initializer = it
                        statement.type = it.type
                    }
                }
                whenContext.append(statement)
                return null
            }

            is IrThrow -> {
                whenContext.append(statement)
                return null
            }

            is IrConstructorCall -> {
                statement.valueArguments.filterNotNull().forEachIndexed { i, arg ->
                    val result = transformStatement(arg, true)
                    result?.let { statement.putValueArgument(i, result) }
                }
                return null
            }

            else -> error("Unexpected statement $statement")
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun transformCall(call: IrCall, shouldReplace: Boolean = false): IrExpression? {
        call.dispatchReceiver?.let {
            val result = transformStatement(it, true)
            result?.let { call.dispatchReceiver = it }

        }
        call.extensionReceiver?.let {
            val result = transformStatement(it, true)
            result?.let { call.extensionReceiver = it }
        }
        call.valueArguments.filterNotNull().forEachIndexed { i, arg ->
            val result = transformStatement(arg, true)
            result?.let { call.putValueArgument(i, result) }
        }

        val name = call.symbol.owner.name.asString()
        val argId = 0

        with(whenContext) {
            when {
                call.isSuspendable() -> {
                    switchState()
                    append(builder.irCall(setter).apply {
                        dispatchReceiver = whenContext.suspendContext
                        putValueArgument(0, builder.irString("<inner-context>"))
                        putValueArgument(1, builder.irCall(suspendContextSymbol.constructors.first()))
                        putTypeArgument(0, suspendContextSymbol.defaultType)
                    })
                    returnResume()
                    nextBranch()
                    append(builder.irBlock {
                        val owner = call.symbol.owner
                        if (owner.valueParameters.last().type != pluginContext.referenceClass(SuspendContext)?.defaultType) {
                            owner.returnType = pluginContext.irBuiltIns.anyNType
                            owner.addValueParameter(
                                "suspendContext",
                                pluginContext.referenceClass(SuspendContext)!!.defaultType
                            )
                        }
                        val temp = irTemporary(irCall(call.symbol).apply {
                            call.valueArguments.forEachIndexed(::putValueArgument)
                            call.typeArguments.forEachIndexed(::putTypeArgument)
                            dispatchReceiver = call.dispatchReceiver
                            extensionReceiver = call.extensionReceiver
                            putValueArgument(owner.valueParameters.size - 1, irCall(getter).apply {
                                dispatchReceiver = whenContext.suspendContext
                                putValueArgument(0, irString("<inner-context>"))
                                putTypeArgument(0, suspendContextSymbol.defaultType)
                            })
                        }, nameHint = "result")
                        +irIfThenElse(
                            pluginContext.irBuiltIns.unitType,
                            irCall(pluginContext.irBuiltIns.ororSymbol).apply {
                                putValueArgument(
                                    0, irEquals(
                                        irGet(temp),
                                        irGetObject(pluginContext.referenceClass(ResumeState)!!),
                                        IrStatementOrigin.EQEQEQ
                                    )
                                )
                                putValueArgument(
                                    1, irEquals(
                                        irGet(temp),
                                        irGetObject(pluginContext.referenceClass(SuspendState)!!),
                                        IrStatementOrigin.EQEQEQ
                                    )
                                )
                            },
                            irReturn(irGet(temp)),
                            irBlock {
                                +irCall(remover).apply {
                                    dispatchReceiver = suspendContext
                                    putValueArgument(0, irString("<inner-context>"))
                                }
                                if (parent != null) {
                                    +irCall(setter).apply {
                                        dispatchReceiver = suspendContext
                                        putValueArgument(0, irString("<suspend-arg-$name-$argId>"))
                                        putValueArgument(1, irGet(temp))
                                        putTypeArgument(0, temp.type)
                                    }
                                }
                            }, null
                        )
                    })
                    return builder.irCall(getter).apply {
                        dispatchReceiver = suspendContext
                        putValueArgument(0, builder.irString("<suspend-arg-$name-$argId>"))
                        putTypeArgument(0, context.irBuiltIns.anyNType)
                    }
                }

                call.symbol == await -> {
                    switchState()
                    returnResume()
                    nextBranch()
                    append(builder.irBlock {
                        +irIfThenElse(
                            context.irBuiltIns.unitType,
                            call.valueArguments.first()!!,
                            irCall(suspendSetter).apply {
                                dispatchReceiver = suspendContext
                                putValueArgument(0, irInt(nextBranch))
                            },
                            irReturn(irGetObject(pluginContext.referenceClass(SuspendState)!!)),
                            null
                        )
                        +irReturn(irGetObject(pluginContext.referenceClass(ResumeState)!!))
                    })
                    nextBranch()
                }

                call.symbol == asyncStart || call.symbol == asyncResume -> {
                    builder.apply {
                        whenContext.append(irCall(HashSetAdd).apply {
                            dispatchReceiver = irCall(asyncControllers).apply {
                                dispatchReceiver = suspendContext
                            }

                            putValueArgument(
                                0,
                                irInt(controllers.map { it.symbol }
                                    .indexOf((call.dispatchReceiver as IrGetValue).symbol))
                            )
                        })
                        if (call.symbol == asyncStart) {
                            whenContext.append(irCall(setter).apply {
                                dispatchReceiver = suspendContext

                                putValueArgument(
                                    0, irString(
                                        "<async_${
                                            controllers.map { it.symbol }
                                                .indexOf((call.dispatchReceiver as IrGetValue).symbol)
                                        }>"))
                                putValueArgument(1, irCall(asyncContext.constructors.first()).apply {
                                    putValueArgument(0, irCall(suspendContextSymbol.constructors.first()))
                                })
                                putTypeArgument(0, asyncContext.defaultType)
                            })
                        }
                    }
                }

                call.symbol == asyncStop || call.symbol == asyncPause -> {
                    builder.apply {
                        whenContext.append(irCall(HashSetRemove).apply {
                            dispatchReceiver = irCall(asyncControllers).apply {
                                dispatchReceiver = suspendContext
                            }

                            putValueArgument(
                                0,
                                irInt(controllers.map { it.symbol }
                                    .indexOf((call.dispatchReceiver as IrGetValue).symbol))
                            )
                        })
                        if (call.symbol == asyncStop) {
                            whenContext.append(irCall(remover).apply {
                                dispatchReceiver = suspendContext

                                putValueArgument(
                                    0, irString(
                                        "<async_${
                                            controllers.map { it.symbol }
                                                .indexOf((call.dispatchReceiver as IrGetValue).symbol)
                                        }>"))
                            })
                        }
                    }
                }

                call.symbol == asyncJoin -> {
                    builder.apply {
                        transformCall(irCall(await).apply {
                            putValueArgument(0, irCall(asyncIsEnd).apply {
                                dispatchReceiver = call.dispatchReceiver
                            })
                        }, true)
                        transformCall(irCall(asyncStop).apply {
                            dispatchReceiver = call.dispatchReceiver
                        }, true)
                    }
                }

                !shouldReplace -> {
                    append(call)
                }

                else -> {}
            }
        }
        return null
    }
}

fun IrBuilderWithScope.throwIllegalStateException(message: String): IrExpression {
    // Находим конструктор IllegalStateException(String)
    val exceptionClass = context.irBuiltIns.illegalArgumentExceptionSymbol

    // Создаем выражение для вызова конструктора
    val exceptionConstructorCall = irCall(exceptionClass).apply {
        putValueArgument(0, irString(message))
    }

    // Создаем выражение для выброса исключения
    return irThrow(exceptionConstructorCall)
}

fun IrBuilderWithScope.irIfThenElse(
    type: IrType,
    condition: IrExpression,
    thenPart: IrExpression,
    elsePart: IrExpression,
    origin: IrStatementOrigin? = null,
) = IrWhenImpl(startOffset, endOffset, type, origin).apply {
        branches.add(IrBranchImpl(startOffset, endOffset, condition, thenPart))
        branches.add(irElseBranch(elsePart))
    }