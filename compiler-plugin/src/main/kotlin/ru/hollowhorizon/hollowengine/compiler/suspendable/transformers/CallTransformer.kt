package ru.hollowhorizon.hollowengine.compiler.suspendable.transformers

import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.constructors
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendCallTransformer
import ru.hollowhorizon.hollowengine.compiler.suspendable.irIfThenElse
import ru.hollowhorizon.hollowengine.compiler.suspendable.isSuspendable

// На этой версии как-то странно работает typeArguments, пишет что его нет.
val IrFunctionAccessExpression.typeArgumentsFix: List<IrType?>
    get() = List(typeArgumentsCount) { getTypeArgument(it) }

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun SuspendCallTransformer.transformCall(call: IrCall, shouldReplace: Boolean = false): IrExpression? {
    transformParameters(call)

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
                    if (owner.valueParameters.isEmpty() || owner.valueParameters.last().type != pluginContext.referenceClass(
                            ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext
                        )?.defaultType
                    ) {
                        owner.returnType = pluginContext.irBuiltIns.anyNType
                        owner.addValueParameter(
                            "suspendContext",
                            pluginContext.referenceClass(ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendContext)!!.defaultType
                        )
                    }
                    val temp = irTemporary(irCall(call.symbol).apply {
                        call.valueArguments.forEachIndexed(::putValueArgument)
                        call.typeArgumentsFix.forEachIndexed(::putTypeArgument)
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
                                    irGetObject(pluginContext.referenceClass(ru.hollowhorizon.hollowengine.compiler.identifiers.ResumeState)!!),
                                    org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.EQEQEQ
                                )
                            )
                            putValueArgument(
                                1, irEquals(
                                    irGet(temp),
                                    irGetObject(pluginContext.referenceClass(ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendState)!!),
                                    org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.EQEQEQ
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
                        irReturn(irGetObject(pluginContext.referenceClass(ru.hollowhorizon.hollowengine.compiler.identifiers.SuspendState)!!)),
                        null
                    )
                    +irReturn(irGetObject(pluginContext.referenceClass(ru.hollowhorizon.hollowengine.compiler.identifiers.ResumeState)!!))
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

fun SuspendCallTransformer.transformConstructor(statement: IrConstructorCall): IrExpression? {
    transformParameters(statement)
    return null
}

fun SuspendCallTransformer.transformString(statement: IrStringConcatenation): IrExpression? {
    ArrayList(statement.arguments).forEachIndexed { i, arg ->
        val result = transformStatement(arg, true)
        result?.let { statement.arguments[i] = it }
    }

    return null
}

fun SuspendCallTransformer.transformParameters(statement: IrFunctionAccessExpression) {
    statement.dispatchReceiver?.let {
        val result = transformStatement(it, true)
        result?.let { statement.dispatchReceiver = it }
    }
    statement.extensionReceiver?.let {
        val result = transformStatement(it, true)
        result?.let { statement.extensionReceiver = it }
    }
    statement.valueArguments.filterNotNull().forEachIndexed { i, arg ->
        val result = transformStatement(arg, true)
        result?.let { statement.putValueArgument(i, result) }
    }
}