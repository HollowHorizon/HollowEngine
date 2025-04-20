package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addConstructor
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addGetter
import org.jetbrains.kotlin.ir.builders.declarations.addProperty
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrValueParameterSymbolImpl
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.util.irCall
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.addDefaultConstructorBodyIfAbsent
import ru.hollowhorizon.hollowengine.compiler.coroutine.NameHelper
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.sFunctionN
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.identifiers.Suspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

object FakeCoroutine {
    fun generate(owner: IrFunction): IrClassSymbol {
        val function = owner.deepCopyWithSymbols()

        function.parameters = function.parameters.map {
            if (!it.hasAnnotation(Suspendable) && !it.type.hasAnnotation(Suspendable)) return@map it

            val type = it.type.classOrFail.owner
            if (type.packageFqName != FqName("kotlin") || !type.name.asString().startsWith("Function")) return@map it

            pluginContext.irFactory.createValueParameter(
                it.startOffset, it.endOffset,
                it.origin, it.kind, it.name,
                sFunctionN(type.typeParameters.size - 1).typeWith(
                    (it.type as IrSimpleType).arguments.dropLast(1) // Args without return type
                        .map { it.typeOrFail } + pluginContext.irBuiltIns.anyNType // All return types are Any?
                ),
                it.isAssignable, IrValueParameterSymbolImpl(), it.varargElementType,
                it.isCrossinline, it.isNoinline, it.isHidden
            ).apply {
                parent = function
                val parameter = this
                function.transformChildrenVoid(object : IrElementTransformerVoid() {
                    override fun visitGetValue(expression: IrGetValue): IrExpression {
                        if (expression.symbol == it.symbol) {
                            return builder().irGet(parameter)
                        }
                        return super.visitGetValue(expression)
                    }

                    override fun visitSetValue(expression: IrSetValue): IrExpression {
                        if (expression.symbol == it.symbol) {
                            return builder().irSet(parameter, expression.value)
                        }
                        return super.visitSetValue(expression)
                    }
                })
            }
        }

        val coroutine = pluginContext.irFactory.createClass(
            owner.startOffset, owner.endOffset, IrDeclarationOrigin.DEFINED,
            Name.identifier(NameHelper.createName(function)),
            DescriptorVisibilities.PUBLIC, IrClassSymbolImpl(), ClassKind.CLASS, Modality.FINAL
        )

        coroutine.createThisReceiverParameter()
        val constructor = coroutine.addConstructor {
            isPrimary = true
        }

        // Реализуем интерфейс для лямбд
        val coroutineLambda = sFunctionN(
            function.allParametersCount
        ).typeWith(function.parameters.map { it.type } + pluginContext.irBuiltIns.anyNType)
        coroutine.superTypes += coroutineLambda

        // Создаём функцию действия корутины с параметрами исходной функции
        val invokeFunction = function.deepCopyWithSymbols(coroutine).apply {
            this.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitFunction(declaration: IrFunction): IrStatement {
                    declaration.attributeOwnerId = declaration
                    return super.visitFunction(declaration)
                }
            })
            name = Name.identifier("invoke")
            dispatchReceiverParameter = coroutine.thisReceiver?.copy()
            dispatchReceiverParameter?.parent = this
            returnType = pluginContext.irBuiltIns.anyNType
            (this as IrSimpleFunction).overriddenSymbols += coroutineLambda.classOrFail.getSimpleFunction("invoke")!!
        }
        coroutine.addChild(invokeFunction)
        coroutine.addDefaultConstructorBodyIfAbsent(pluginContext)
        val restoreFunction = coroutine.addFunction {
            updateFrom(function)
            returnType = pluginContext.irBuiltIns.unitType
            name = Name.identifier("restoreState")
        }.apply {
            invokeFunction.parameters.forEach { v ->
                val p = v.copy()
                p.parent = this
                parameters += p
            }
            invokeFunction.typeParameters.forEach { value ->
                typeParameters += value
            }
            dispatchReceiverParameter = coroutine.thisReceiver?.copy()
            dispatchReceiverParameter?.parent = this
            this.overriddenSymbols += coroutineLambda.classOrFail.getSimpleFunction("restoreState")!!
        }
        val updateAsyncFunction = coroutine.addFunction {
            updateFrom(function)
            returnType = pluginContext.irBuiltIns.unitType
            name = Name.identifier("updateAsyncs")
        }.apply {
            invokeFunction.parameters.forEach { v ->
                val p = v.copy()
                p.parent = this
                parameters += p
            }
            invokeFunction.typeParameters.forEach { value ->
                typeParameters += value
            }
            dispatchReceiverParameter = coroutine.thisReceiver?.copy()
            dispatchReceiverParameter?.parent = this
            this.overriddenSymbols += coroutineLambda.classOrFail.getSimpleFunction("updateAsyncs")!!
        }
        updateAsyncFunction.body = coroutine.builder().irBlockBody { }
        coroutine.addProperty {
            name = Name.identifier("serializer")
        }.apply {
            addGetter {
                returnType = pluginContext.referenceClass(
                    ClassId(
                        FqName("kotlinx.serialization"),
                        Name.identifier("KSerializer")
                    )
                )!!.typeWith()
            }.apply {
                this.dispatchReceiverParameter = coroutine.thisReceiver
            }
            val prop = coroutineLambda.classOrFail.owner.properties
                .first { it.name == Name.identifier("serializer") }
            this.overriddenSymbols += prop.symbol
        }
        coroutine.parent = owner.parent
        return coroutine.symbol
    }

    fun IrValueParameter.copy() = pluginContext.irFactory.createValueParameter(
        startOffset, endOffset, origin, kind, name, type, isAssignable, IrValueParameterSymbolImpl(), varargElementType, isCrossinline, isNoinline, isHidden
    )
}