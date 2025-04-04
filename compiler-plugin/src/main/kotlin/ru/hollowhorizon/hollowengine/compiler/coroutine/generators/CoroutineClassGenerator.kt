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
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionReference
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionReferenceImplWithShape
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
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.sFunctionN
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.emptyList
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set

class CoroutineClassGenerator : IrElementTransformerVoid() {
    val functionToClass = HashMap<IrFunction, CoroutineGenerator>()
    private val anonymousIndexes = HashMap<IrFunction, Int>()

    private val stack = ArrayList<CoroutineGenerator>()

    override fun visitFunction(declaration: IrFunction): IrStatement {
        if (declaration.isSuspendable()) {
            val generator = generate(declaration)
            functionToClass[declaration] = generator

            stack.push(generator)
            generator.invokeFunction.transformChildrenVoid()
            stack.pop()

            declaration.body = declaration.builder().irBlockBody {}
        }
        return super.visitFunction(declaration)
    }

    private fun IrFunction.createName(): String = if (name.isAnonymous) {
        val function = parent as IrFunction
        val index = anonymousIndexes.getOrPut(function) { 0 }
        anonymousIndexes[function] = index + 1
        "Lambda\$$index"
    } else {
        name.identifier + "\$SerializableCoroutine"
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
                dispatchReceiverParameter = coroutine.thisReceiver
                returnType = pluginContext.irBuiltIns.anyNType
                (this as IrSimpleFunction).overriddenSymbols += coroutineLambda.classOrFail.getSimpleFunction("invoke")!!
            }
            coroutine.addChild(invokeFunction)

            stack.peek()?.let { outer ->
                coroutine.isInner = true
                constructor.addValueParameter(
                    Name.identifier("this$0"),
                    coroutine.defaultType,
                    IrDeclarationOrigin.FIELD_FOR_OUTER_THIS
                ).kind = IrParameterKind.DispatchReceiver
                outer.coroutine.addChild(coroutine)
                coroutine.parent = outer.coroutine
                coroutine.transformChildrenVoid(OuterPropertyTransformer(outer, coroutine))
            } ?: run {
                coroutine.parent = file
                file.addChild(coroutine)
            }
            coroutine.addDefaultConstructorBodyIfAbsent(pluginContext)


            val restoreFunction = coroutine.addFunction {
                updateFrom(function)
                returnType = pluginContext.irBuiltIns.unitType
                name = Name.identifier("restoreState")
            }.apply {
                invokeFunction.parameters.forEach { v ->
                    parameters += v
                }
                invokeFunction.typeParameters.forEach { value ->
                    typeParameters += value
                }
                dispatchReceiverParameter = coroutine.thisReceiver
                this.overriddenSymbols += coroutineLambda.classOrFail.getSimpleFunction("restoreState")!!
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
                    body = builder().run {
                        irExprBody(irCall(serializer.primaryConstructor!!.symbol).apply {
                            dispatchReceiver = irGet(coroutine.thisReceiver!!)
                        })
                    }
                }
                val prop = coroutineLambda.classOrFail.owner.properties
                    .first { it.name == Name.identifier("serializer") }
                this.overriddenSymbols += prop.symbol
            }
            val (property, descriptor) = serializer.createDescriptor(coroutine)

            val coroutineGenerator =
                CoroutineGenerator(coroutine, serializer, property, descriptor, invokeFunction, restoreFunction)
            coroutine.addChild(serializer)
            return coroutineGenerator
        }
    }

    private fun fillFunction(coroutine: IrFunction, function: IrFunction) {
        //coroutine.body = function.deepCopyWithSymbols(coroutine.parent)
    }

    // Создание описания структуры при сериализации
    private fun IrClass.createDescriptor(coroutine: IrClass): Pair<IrProperty, IrFunctionReference> {
        val irLambda = createLambda(coroutine)
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
private fun IrClass.createLambda(coroutine: IrClass): IrFunctionReference {
    val irBuiltIns = pluginContext.irBuiltIns
    val irFactory = pluginContext.irFactory
    val receiverType = csdbClass.defaultType
    val lambdaType = irBuiltIns.functionN(1)
        .typeWith(receiverType, irBuiltIns.unitType) // ClassSerialDescriptorBuilder.() -> Unit

    builder {
        // Создаём анонимную функцию
        val lambdaFunction = addFunction {
            name = Name.identifier("makeDescriptor")
            returnType = irBuiltIns.unitType
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply {
            dispatchReceiverParameter = this@createLambda.thisReceiver!!
            extensionReceiverParameter = createExtensionReceiver(receiverType)
            this.body = irBlockBody {}
        }

        // Заворачиваем функцию в IrFunctionExpression (лямбду)
        return IrFunctionReferenceImplWithShape(
            startOffset, endOffset,
            lambdaType,
            lambdaFunction.symbol,
            0, 2,
            0, true, true
        ).apply {
            dispatchReceiver = irGet(this@createLambda.thisReceiver!!)
        }
    }
}