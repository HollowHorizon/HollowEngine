@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.backend.common.ir.createExtensionReceiver
import org.jetbrains.kotlin.backend.common.peek
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.backend.common.push
import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.isAnonymous
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.addDefaultConstructorBodyIfAbsent
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.KSerializer
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.SerialDescriptor
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.serialBuilder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class CoroutineClassGenerator : IrElementTransformerVoid() {
    val functionToClass = HashMap<IrFunction, CoroutineGenerator>()
    private val anonymousIndexes = HashMap<IrFunction, Int>()

    private val stack = ArrayList<CoroutineGenerator>()

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration.isSuspendable()) {
            val generator = generate(declaration)
            functionToClass[declaration] = generator

            stack.push(generator)
            generator.updateFunction.transformChildrenVoid()
            stack.pop()

            declaration.body = declaration.builder().irBlockBody {}
        }
        return super.visitFunction(declaration)
    }

    private fun IrFunction.createName(postfix: String = "\$SerializableCoroutine"): String = if (name.isAnonymous) {
        val function = parent as IrFunction
        val index = anonymousIndexes.getOrPut(function) { 0 }
        anonymousIndexes[function] = index + 1
        function.createName("") + "\$Anonymous$index$postfix"
    } else {
        name.identifier + postfix
    }

    private fun generate(function: IrFunction): CoroutineGenerator {
        function.apply {
            // Создаём новую корутину
            val coroutine = pluginContext.irFactory.createClass(
                startOffset, endOffset, IrDeclarationOrigin.DEFINED,
                Name.identifier(function.createName()),
                DescriptorVisibilities.PUBLIC, IrClassSymbolImpl(), ClassKind.CLASS, Modality.FINAL
            )


            coroutine.createThisReceiverParameter()
            val constructor = coroutine.addConstructor {
                isPrimary = true
            }
            stack.peek()?.let { outer ->
                coroutine.isInner = true
                constructor.addValueParameter(
                    Name.identifier("this$0"),
                    coroutine.defaultType,
                    IrDeclarationOrigin.FIELD_FOR_OUTER_THIS
                ).kind = IrParameterKind.DispatchReceiver
                outer.coroutine.addChild(coroutine)
                coroutine.parent = outer.coroutine
            } ?: run {
                coroutine.parent = file
                file.addChild(coroutine)
            }
            coroutine.addDefaultConstructorBodyIfAbsent(pluginContext)

            // Реализуем интерфейс для лямбд
            val coroutineLambda = pluginContext.irBuiltIns.functionN(
                function.allParametersCount
            ).typeWith(function.parameters.map { it.type } + pluginContext.irBuiltIns.anyNType)
            coroutine.superTypes += coroutineLambda

            // Создаём функцию действия корутины с параметрами исходной функции
            val updateFunction = coroutine.addFunction {
                updateFrom(function)
                returnType = pluginContext.irBuiltIns.anyNType
                name = Name.identifier("invoke")
            }.apply {
                function.valueParameters.forEach { value ->
                    addValueParameter(value.name, value.type)
                }
                function.typeParameters.forEach { value ->
                    addTypeParameter(value.name.asString(), value.defaultType, value.variance)
                }
                extensionReceiverParameter = function.extensionReceiverParameter
                dispatchReceiverParameter = coroutine.thisReceiver
                fillFunction(this, function)
                overriddenSymbols += coroutineLambda.classOrFail.getSimpleFunction("invoke")!!
            }

            // Создаём вложенный класс-сериализатор корутины
            val serializer = pluginContext.irFactory.buildClass {
                name = Name.identifier("Serializer")
                kind = ClassKind.CLASS
            }.apply {
                parent = coroutine
                isInner = true
                createThisReceiverParameter()
                val constructor = addConstructor {
                    isPrimary = true
                }
                constructor.addValueParameter(
                    Name.identifier("this$0"),
                    coroutine.defaultType,
                    IrDeclarationOrigin.FIELD_FOR_OUTER_THIS
                ).kind = IrParameterKind.DispatchReceiver
                addDefaultConstructorBodyIfAbsent(pluginContext)
                superTypes += pluginContext.referenceClass(
                    ClassId(
                        FqName("kotlinx.serialization"),
                        Name.identifier("KSerializer")
                    )
                )!!.typeWith(coroutine.defaultType)
            }

            // Добавляем экземпляр вложенного класса в корутину
            coroutine.addField {
                name = Name.special("<serializer>")
                type = serializer.defaultType
                isFinal = true
            }.apply {
                initializer = builder().run {
                    irExprBody(irCall(serializer.primaryConstructor!!.symbol).apply {
                        dispatchReceiver = irGet(coroutine.thisReceiver!!)
                    })
                }
            }
            val (property, descriptor) = serializer.createDescriptor()

            val coroutineGenerator = CoroutineGenerator(coroutine, serializer, property, descriptor, updateFunction)
            coroutine.addChild(serializer)
            return coroutineGenerator
        }
    }

    private fun fillFunction(coroutine: IrFunction, function: IrFunction) {
        val oldParams =
            function.valueParameters.mapIndexed { index, par -> par.symbol to coroutine.valueParameters[index] }
                .toMap()

        function.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitGetValue(expression: IrGetValue): IrExpression {
                oldParams[expression.symbol]?.let {
                    return coroutine.builder().irGet(it)
                }
                return super.visitGetValue(expression)
            }

            override fun visitReturn(expression: IrReturn): IrExpression {
                if (expression.returnTargetSymbol == function.symbol) {
                    expression.returnTargetSymbol = coroutine.symbol
                }
                return super.visitReturn(expression)
            }
        })
        coroutine.body = function.body
    }

    // Создание описания структуры при сериализации
    private fun IrClass.createDescriptor(): Pair<IrProperty, IrFunctionExpression> {
        val irLambda = createLambda()
        val property = addProperty {
            name = Name.identifier("descriptor")
        }.apply {
            backingField = pluginContext.irFactory.buildField {
                type = SerialDescriptor.defaultType
                name = Name.identifier("descriptorField")
                visibility = DescriptorVisibilities.PRIVATE
            }.apply {
                parent = this@createDescriptor
                initializer = builder().run {
                    irExprBody(irCall(serialBuilder).apply {
                        val pkg = this@createDescriptor.packageFqName ?: FqName("")
                        putValueArgument(0, irString(pkg.child(Name.identifier("Serializer")).render()))
                        putValueArgument(1, irVararg(SerialDescriptor.defaultType, emptyList()))
                        putValueArgument(2, irLambda)
                    })
                }
            }
            addGetter {
                returnType = SerialDescriptor.defaultType
            }.apply {
                dispatchReceiverParameter = thisReceiver
                body = builder().run { irExprBody(irGetField(irGet(thisReceiver!!), backingField!!)) }
            }

            overriddenSymbols += KSerializer.owner.properties.single { it.name.identifier == "descriptor" }.symbol
        }
        return property to irLambda
    }
}

// Builder описания для сериализации
val csdbClass = pluginContext.referenceClass(
    ClassId(
        FqName("kotlinx.serialization.descriptors"),
        Name.identifier("ClassSerialDescriptorBuilder")
    )
) ?: error("ClassSerialDescriptorBuilder not found")

// Создание лямбды для ClassSerialDescriptorBuilder
private fun IrClass.createLambda(): IrFunctionExpression {
    val irBuiltIns = pluginContext.irBuiltIns
    val irFactory = pluginContext.irFactory
    val receiverType = csdbClass.defaultType
    val lambdaType = irBuiltIns.functionN(1)
        .typeWith(receiverType, irBuiltIns.unitType) // ClassSerialDescriptorBuilder.() -> Unit

    builder {
        // Создаём анонимную функцию
        val lambdaFunction = irFactory.buildFun {
            name = SpecialNames.ANONYMOUS
            returnType = irBuiltIns.unitType
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply {
            parent = this@createLambda

            extensionReceiverParameter = createExtensionReceiver(receiverType)
            this.body = irBlockBody {}
        }

        // Заворачиваем функцию в IrFunctionExpression (лямбду)
        return IrFunctionExpressionImpl(
            startOffset, endOffset,
            lambdaType,
            lambdaFunction,
            IrStatementOrigin.LAMBDA
        )
    }
    error("Lambda not created!")
}