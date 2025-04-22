@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.backend.js.utils.valueArguments
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.constructors
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.getSimpleFunction
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.properties.CoroutineTransformer
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.statements.InlineSuspendableLowering
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class InlineTransformer : CoroutineTransformer() {
    var varIndex = 0

    override fun visitCall(expression: IrCall): IrExpression {
        val target = expression.symbol.owner
        if (target.isInline) {
            coroutine.invokeFunction.builder {
                // Эх, ну за что мне это всё... Почему нельзя было в inline хотя бы декомпилированное тело функции составить...
                when (target.name.asString()) {
                    "forEach" -> {
                        return super.visitExpression(transformForLoop(expression))
                    }

                    "map" -> {
                        return super.visitExpression(transformMap(expression))
                    }

                    "filter" -> {
                        return super.visitExpression(transformFilter(expression))
                    }
                }
            }

            val body = target.body?.deepCopyWithSymbols(coroutine.invokeFunction)
            body?.transformChildrenVoid(InlineSuspendableLowering(expression, coroutine.invokeFunction))

            body?.let {
                return coroutine.invokeFunction.builder().irBlock {
                    it.statements.forEach {
                        +it
                    }
                }
            }
        }
        return super.visitCall(expression)
    }

    private fun DeclarationIrBuilder.transformForLoop(expression: IrCall) = irBlock {
        val range = expression.extensionReceiver ?: return@irBlock
        val componentType = (range.type as IrSimpleType).arguments[0].typeOrNull ?: return@irBlock
        val action = expression.getValueArgument(0) as? IrFunctionExpression ?: return@irBlock
        val iterator = range.type.classOrNull?.functionByName("iterator") ?: return@irBlock
        val iteratorClass = iterator.owner.returnType.classOrNull ?: return@irBlock
        val hasNext = iteratorClass.functionByName("hasNext")
        val next = iteratorClass.functionByName("next")

        val iter = irTemporary(irCall(iterator, iterator.owner.returnType.classOrFail.typeWith(componentType)).apply {
            dispatchReceiver = range
        }, varIndex++.toString())
        +irWhile().apply {
            condition = irCall(hasNext).apply {
                dispatchReceiver = irGet(iter)
            }

            body = irBlock {
                val nextVar = irTemporary(irCall(next, componentType).apply {
                    dispatchReceiver = irGet(iter)
                }, varIndex++.toString(), componentType)
                if (action.origin == IrStatementOrigin.LAMBDA) {
                    val newBody =
                        action.function.body?.deepCopyWithSymbols(coroutine.invokeFunction) ?: return@irBlock
                    newBody.transformChildrenVoid(object : IrElementTransformerVoid() {
                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            if (expression.symbol == action.function.valueParameters[0].symbol) {
                                return irGet(nextVar)
                            }
                            return super.visitGetValue(expression)
                        }
                    })
                    newBody.statements.forEach {
                        +it
                    }
                } else {
                    irCall(action.function).apply {
                        putValueArgument(0, irGet(nextVar))
                    }
                }
            }
        }
    }

    private fun DeclarationIrBuilder.transformMap(expression: IrCall) = irBlock {
        val range = expression.extensionReceiver ?: return@irBlock
        val componentType = (range.type as IrSimpleType).arguments[0].typeOrNull ?: return@irBlock
        val transform = expression.getValueArgument(0) as? IrFunctionExpression ?: return@irBlock
        val iterator = range.type.classOrNull?.functionByName("iterator") ?: return@irBlock
        val iteratorClass = iterator.owner.returnType.classOrNull ?: return@irBlock
        val hasNext = iteratorClass.functionByName("hasNext")
        val next = iteratorClass.functionByName("next")

        val listClass = pluginContext.referenceClass(ClassId(FqName("java.util"), Name.identifier("ArrayList")))!!

        val result = irTemporary(irCall(listClass.constructors.first { it.owner.valueParameters.isEmpty() }, listClass.typeWith(componentType)).apply {
            putTypeArgument(0, componentType)
        }, varIndex++.toString())

        val iter = irTemporary(irCall(iterator, iterator.owner.returnType.classOrFail.typeWith(componentType)).apply {
            dispatchReceiver = range
        }, varIndex++.toString())

        +irWhile().apply {
            condition = irCall(hasNext).apply {
                dispatchReceiver = irGet(iter)
            }

            body = irBlock {
                val nextVar = irTemporary(irCall(next, componentType).apply {
                    dispatchReceiver = irGet(iter)
                })

                val resultExpr = if (transform.origin == IrStatementOrigin.LAMBDA) {
                    val newBody = transform.function.body?.deepCopyWithSymbols(coroutine.invokeFunction) ?: return@irBlock
                    val variable = irTemporary(null, varIndex++.toString(), irType = componentType)
                    newBody.transformChildrenVoid(object : IrElementTransformerVoid() {
                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            if (expression.symbol == transform.function.valueParameters[0].symbol) {
                                return irGet(nextVar)
                            }
                            return super.visitGetValue(expression)
                        }

                        override fun visitReturn(expression: IrReturn): IrExpression {
                            if(expression.returnTargetSymbol == transform.function.symbol) {
                                return super.visitExpression(irSet(variable, expression.value))
                            }
                            return super.visitReturn(expression)
                        }
                    })
                    newBody.statements.forEach {
                        +it
                    }
                    irGet(variable)
                } else {
                    irCall(transform.function).apply {
                        putValueArgument(0, irGet(nextVar))
                    }
                }

                +irCall(listClass.getSimpleFunction("add")!!).apply {
                    dispatchReceiver = irGet(result)
                    putValueArgument(0, resultExpr)
                }
            }
        }

        +irGet(result)
    }

    private fun DeclarationIrBuilder.transformFilter(expression: IrCall) = irBlock {
        val range = expression.extensionReceiver ?: return@irBlock
        val componentType = (range.type as IrSimpleType).arguments[0].typeOrNull ?: return@irBlock
        val transform = expression.getValueArgument(0) as? IrFunctionExpression ?: return@irBlock
        val iterator = range.type.classOrNull?.functionByName("iterator") ?: return@irBlock
        val iteratorClass = iterator.owner.returnType.classOrNull ?: return@irBlock
        val hasNext = iteratorClass.functionByName("hasNext")
        val next = iteratorClass.functionByName("next")

        val listClass = pluginContext.referenceClass(ClassId(FqName("java.util"), Name.identifier("ArrayList")))!!

        val result = irTemporary(irCall(listClass.constructors.first { it.owner.valueParameters.isEmpty() }, listClass.typeWith(componentType)).apply {
            putTypeArgument(0, componentType)
        }, varIndex++.toString())

        val iter = irTemporary(irCall(iterator, iterator.owner.returnType.classOrFail.typeWith(componentType)).apply {
            dispatchReceiver = range
        }, varIndex++.toString())

        +irWhile().apply {
            condition = irCall(hasNext).apply {
                dispatchReceiver = irGet(iter)
            }

            body = irBlock {
                val nextVar = irTemporary(irCall(next, componentType).apply {
                    dispatchReceiver = irGet(iter)
                })

                val resultExpr = if (transform.origin == IrStatementOrigin.LAMBDA) {
                    val newBody = transform.function.body?.deepCopyWithSymbols(coroutine.invokeFunction) ?: return@irBlock
                    val variable = irTemporary(null, varIndex++.toString(), irType = pluginContext.irBuiltIns.booleanType)
                    newBody.transformChildrenVoid(object : IrElementTransformerVoid() {
                        override fun visitGetValue(expression: IrGetValue): IrExpression {
                            if (expression.symbol == transform.function.valueParameters[0].symbol) {
                                return irGet(nextVar)
                            }
                            return super.visitGetValue(expression)
                        }

                        override fun visitReturn(expression: IrReturn): IrExpression {
                            if(expression.returnTargetSymbol == transform.function.symbol) {
                                return super.visitExpression(irSet(variable, expression.value))
                            }
                            return super.visitReturn(expression)
                        }
                    })
                    newBody.statements.forEach {
                        +it
                    }
                    irGet(variable)
                } else {
                    irCall(transform.function).apply {
                        putValueArgument(0, irGet(nextVar))
                    }
                }

                +irIfThen(pluginContext.irBuiltIns.unitType, resultExpr, irCall(listClass.getSimpleFunction("add")!!).apply {
                    dispatchReceiver = irGet(result)
                    putValueArgument(0, irGet(nextVar))
                })
            }
        }

        +irGet(result)
    }
    
}
