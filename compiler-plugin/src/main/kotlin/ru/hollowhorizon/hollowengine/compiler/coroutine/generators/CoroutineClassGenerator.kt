package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.backend.common.ir.createExtensionReceiver
import org.jetbrains.kotlin.backend.common.serialization.mangle.ir.isAnonymous
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.impl.IrClassSymbolImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.addDefaultConstructorBodyIfAbsent
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.transformers.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class CoroutineClassGenerator : IrElementTransformerVoid() {
    val functionToClass = HashMap<IrFunction, CoroutineInfo>()
    var anonymousIndex = 0

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration.isSuspendable()) {
            if(declaration.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) {
                val baseInfo = functionToClass[declaration.parent as IrFunction]?.copy(isLambda = true) ?: error("Function not found!")
                baseInfo.coroutine.addFunction {
                    updateFrom(declaration)
                    returnType = pluginContext.irBuiltIns.anyNType
                    name = Name.identifier("Anonymous\$${anonymousIndex++}\$tick")
                }.apply {
                    declaration.valueParameters.forEach { value ->
                        addValueParameter(value.name, value.type)
                    }
                    declaration.typeParameters.forEach { value ->
                        addTypeParameter(value.name.asString(), value.defaultType, value.variance)
                    }
                    extensionReceiverParameter = declaration.extensionReceiverParameter
                    dispatchReceiverParameter = baseInfo.coroutine.thisReceiver
                }
                functionToClass[declaration] = baseInfo
            } else {
                functionToClass[declaration] = generate(declaration)
            }
        }
        return super.visitFunction(declaration)
    }

    fun IrFunction.createName(): String = if (name.isAnonymous) {
        (parent as IrFunction).createName() + "\$Anonymous${anonymousIndex++}\$SerializableCoroutine"
    } else {
        name.identifier + "\$SerializableCoroutine"
    }


    fun generate(function: IrFunction): CoroutineInfo {
        function.apply {
            val coroutine = pluginContext.irFactory.createClass(
                startOffset, endOffset, IrDeclarationOrigin.DEFINED,
                Name.identifier(function.createName()),
                DescriptorVisibilities.PUBLIC, IrClassSymbolImpl(), ClassKind.CLASS, Modality.FINAL
            )
            coroutine.parent = file
            coroutine.createThisReceiverParameter()
            coroutine.addConstructor { isPrimary = true }
            coroutine.addDefaultConstructorBodyIfAbsent(pluginContext)
            coroutine.addFunction {
                updateFrom(function)
                returnType = pluginContext.irBuiltIns.anyNType
                name = Name.identifier("tick")
            }.apply {
                function.valueParameters.forEach { value ->
                    addValueParameter(value.name, value.type)
                }
                function.typeParameters.forEach { value ->
                    addTypeParameter(value.name.asString(), value.defaultType, value.variance)
                }
                extensionReceiverParameter = function.extensionReceiverParameter
                dispatchReceiverParameter = coroutine.thisReceiver
            }
            file.addChild(coroutine)
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
            coroutine.addField {
                name = Name.identifier("serializer")
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
            val encoder = serializer.createSerializer(coroutine.defaultType)
            val decoder = serializer.createDeserializer(coroutine.defaultType)
            coroutine.addChild(serializer)
            return CoroutineInfo(coroutine, serializer, property, descriptor.function, encoder, decoder, false)
        }
    }

    data class CoroutineInfo(
        val coroutine: IrClass,
        val serializer: IrClass,
        val descriptor: IrProperty,
        val lambda: IrFunction,
        val encoder: IrFunction,
        val decoder: IrFunction,
        val isLambda : Boolean
    )

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

    private fun IrClass.createSerializer(valueType: IrType): IrFunction {
        return addFunction {
            name = Name.identifier("serialize")
        }.apply {
            addValueParameter(Name.identifier("encoder"), Encoder.defaultType)
            addValueParameter(Name.identifier("value"), valueType)
            returnType = pluginContext.irBuiltIns.unitType
            overriddenSymbols += SerializationStrategy
            dispatchReceiverParameter = thisReceiver
            body = builder().irBlockBody { }
        }
    }

    private fun IrClass.createDeserializer(valueType: IrType): IrFunction {
        return addFunction {
            name = Name.identifier("deserialize")
        }.apply {
            addValueParameter(Name.identifier("decoder"), Decoder.defaultType)
            returnType = valueType
            overriddenSymbols += DeserializationStrategy
            dispatchReceiverParameter = thisReceiver
            body = builder().irBlockBody { }
        }
    }
}

val csdbClass = pluginContext.referenceClass(
    ClassId(
        FqName("kotlinx.serialization.descriptors"),
        Name.identifier("ClassSerialDescriptorBuilder")
    )
) ?: error("ClassSerialDescriptorBuilder not found")

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