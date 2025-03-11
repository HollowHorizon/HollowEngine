package ru.hollowhorizon.hollowengine.compiler.coroutine.transformers

import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.common.lower.irIfThen
import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addField
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.makeNotNull
import org.jetbrains.kotlin.ir.types.makeNullable
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.SpecialNames
import org.jetbrains.kotlin.utils.findIsInstanceAnd
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.BaseIrGenerator
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext
import ru.hollowhorizon.hollowengine.compiler.coroutine.NonSerializableProperty
import ru.hollowhorizon.hollowengine.compiler.coroutine.generators.CoroutineClassGenerator.CoroutineInfo
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class CoroutinePropertyTransformer(
    val functionToClass: HashMap<IrFunction, CoroutineInfo>,
) : IrElementTransformerVoid() {
    val serializationContext = SerializationPluginContext(pluginContext, null)
    var anonymousIndex = 0

    private val elementType = pluginContext.referenceClass(
        ClassId.topLevel(FqName("kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder"))
    )!!.functionByName("element")
    private val encodeSerializableElement = pluginContext.referenceClass(
        ClassId.topLevel(FqName("kotlinx.serialization.encoding.CompositeEncoder"))
    )!!.functionByName("encodeSerializableElement")

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitFunction(function: IrFunction): IrStatement {
        if (!function.isSuspendable()) return super.visitFunction(function)
        val suspendableCalls = functionToClass.values.associate { it.coroutine.defaultType to it.coroutine }

        functionToClass[function]?.let { serializer ->
            val coroutine = serializer.coroutine
            val generator = object : BaseIrGenerator(coroutine, serializationContext) {}
            val serializableFields = ArrayList<IrField>()
            val nonSerializableFields = ArrayList<IrField>()


            val localsCollector = object : IrElementTransformerVoid() {
                var currentBranch: IrBranch? = null
                val localVariables = HashMap<IrVariable, IrBranch>()
                val lastAccess = HashMap<IrVariable, IrValueAccessExpression>()

                fun visitStateBranch(branch: IrBranch): IrBranch {
                    currentBranch = branch
                    return super.visitBranch(branch)
                }

                override fun visitWhen(expression: IrWhen): IrExpression {
                    if (function.body?.statements?.get(1) == expression) {
                        expression.branches.forEach {
                            visitStateBranch(it)
                        }
                        currentBranch = null
                    }

                    return super.visitWhen(expression)
                }

                // TODO: Какая ирония... Ты ведь сам вырезал из корутин все циклы
                override fun visitLoop(loop: IrLoop): IrExpression {
                    // TODO: Пофиксить определение циклов и сброс переменных в нём
                    val old = currentBranch
                    currentBranch = null // Внутри циклов не нужно сбрасывать переменные
                    val transformed = super.visitLoop(loop)
                    currentBranch = old
                    return transformed
                }

                override fun visitVariable(declaration: IrVariable): IrStatement {
                    currentBranch?.let {
                        if (declaration.type in suspendableCalls) return super.visitVariable(declaration)
                        localVariables[declaration] = it
                    }
                    return super.visitVariable(declaration)
                }

                override fun visitGetValue(expression: IrGetValue): IrExpression {
                    val owner = expression.symbol.owner as? IrVariable ?: return super.visitGetValue(expression)
                    currentBranch?.let {
                        if (currentBranch != localVariables[owner]) {
                            localVariables.remove(owner)
                        }
                        //if (expression.type !in suspendableCalls) lastAccess[owner] = expression
                    }
                    return super.visitGetValue(expression)
                }

                override fun visitSetValue(expression: IrSetValue): IrExpression {
                    val owner = expression.symbol.owner as? IrVariable ?: return super.visitSetValue(expression)
                    currentBranch?.let {
                        if (currentBranch != localVariables[owner]) {
                            localVariables.remove(owner)
                        }
                        //if (expression.type !in suspendableCalls) lastAccess[owner] = expression
                    }
                    return super.visitSetValue(expression)
                }
            }

            function.transformChildrenVoid(localsCollector)

            localsCollector.localVariables.clear() //! Пока отключил, нужно убрать из локальных те, что есть в лямбдах

            val localVariables = HashSet(localsCollector.localVariables.keys)

            function.transformChildrenVoid(object : IrElementTransformerVoid() {
                override fun visitVariable(declaration: IrVariable): IrStatement {
                    if (declaration in localVariables) return super.visitVariable(declaration)


                    val isSuspendCall = declaration.type in suspendableCalls
                    val isSerializable =
                        declaration.type.isSerializable(generator, serializationContext) || isSuspendCall

                    if (isSerializable) {
                        val isStateIndex = declaration.name == Name.special("<stateIndex>")
                        val field = coroutine.addField {
                            type = if (isStateIndex || isSuspendCall) {
                                declaration.type.makeNotNull()
                            } else {
                                declaration.type.makeNullable()
                            }
                            name = declaration.name
                        }
                        if (isStateIndex) field.initializer = field.builder().run { irExprBody(irInt(0)) }
                        serializableFields += field

                        function.transformChildrenVoid(
                            SerializablePropertyRemapper(
                                localsCollector.lastAccess, localVariables,
                                coroutine.thisReceiver!!,
                                declaration,
                                field,
                                isStateIndex
                            )
                        )

                        if (!isStateIndex) {
                            field.builder {
                                declaration.initializer?.let {
                                    return if (isSuspendCall) {
                                        field.initializer = irExprBody(declaration.initializer!!)
                                        irBlock {}
                                    } else irSetField(irGet(coroutine.thisReceiver!!), field, it)
                                }
                            }
                        } else {
                            declaration.initializer =
                                field.builder().run { irGetField(irGet(coroutine.thisReceiver!!), field) }
                            return super.visitVariable(declaration)
                        }
                    } else {
                        val field = coroutine.addField {
                            type = NonSerializableProperty.TYPE.typeWith(declaration.type)
                            name = declaration.name
                        }
                        nonSerializableFields += field

                        val collector = PropertySetterCollector(declaration)
                        function.transformChildrenVoid(collector)
                        declaration.initializer?.let { collector.setters.add(0, it) }

                        function.transformChildrenVoid(
                            NonSerializablePropertyRemapper(
                                localsCollector.lastAccess, localVariables,
                                coroutine.thisReceiver!!,
                                declaration,
                                field
                            )
                        )

                        field.builder {
                            field.initializer = irExprBody(irCallConstructor(
                                NonSerializableProperty.TYPE.constructors.first(),
                                listOf(declaration.type)
                            ).apply {
                                putValueArgument(0, irCall(pluginContext.irBuiltIns.arrayOf).apply {
                                    putTypeArgument(
                                        0,
                                        pluginContext.irBuiltIns.functionN(0).typeWith(declaration.type)
                                    )
                                    putValueArgument(
                                        0,
                                        irVararg(
                                            pluginContext.irBuiltIns.functionN(0).typeWith(declaration.type),
                                            collector.setters.map { coroutine.createLambda(it) })
                                    )
                                })
                            })
                            val setter = NonSerializableProperty.TYPE.functionByName("set")
                            return irCall(setter).apply {
                                dispatchReceiver = irGetField(irGet(coroutine.thisReceiver!!), field)
                                putValueArgument(0, irInt(0)) // Переключаем конструктор
                            }
                        }
                    }

                    return super.visitVariable(declaration)
                }
            })

            transformSerializableFields(
                coroutine.builder().irGet(coroutine.thisReceiver!!),
                serializer,
                serializableFields,
                nonSerializableFields,
                generator
            )

            val name = if(function.origin == IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA) {
                Name.identifier("Anonymous\$${anonymousIndex++}\$tick")
            } else {
                Name.identifier("tick")
            }

            coroutine.declarations.findIsInstanceAnd<IrFunction> { it.name == name }?.apply {
                val oldParams =
                    function.valueParameters.mapIndexed { index, par -> par.symbol to valueParameters[index] }
                        .toMap()


                function.body?.transformChildrenVoid(object : IrElementTransformerVoid() {
                    override fun visitGetValue(expression: IrGetValue): IrExpression {
                        oldParams[expression.symbol]?.let {
                            return builder().irGet(it)
                        }
                        return super.visitGetValue(expression)
                    }

                    override fun visitReturn(expression: IrReturn): IrExpression {
                        if (expression.returnTargetSymbol == function.symbol) {
                            expression.returnTargetSymbol = symbol
                        }
                        return super.visitReturn(expression)
                    }
                })
                body = function.body
            }
        }


        function.builder {
            function.body = irBlockBody {}
        }

        return super.visitFunction(function)
    }

    private fun transformSerializableFields(
        coroutineReceiver: IrExpression,
        coroutineSerializer: CoroutineInfo,
        serializable: List<IrField>, nonSerializable: List<IrField>,
        generator: BaseIrGenerator,
    ) {
        fillEncoders(coroutineReceiver, coroutineSerializer, serializable, nonSerializable, generator)
        fillDecoders(coroutineReceiver, coroutineSerializer, serializable, nonSerializable, generator)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun fillDecoders(
        coroutineReceiver: IrExpression,
        coroutineSerializer: CoroutineInfo,
        serializable: List<IrField>,
        nonSerializable: List<IrField>,
        generator: BaseIrGenerator,
    ) {
        val suspendableCalls = functionToClass.values.associate { it.coroutine.defaultType to it.coroutine }

        coroutineSerializer.decoder.apply {
            body = builder().irBlockBody {
                val decoder = irTemporary(irCall(DBeginStructure).apply {
                    dispatchReceiver = irGet(coroutineSerializer.decoder.valueParameters[0])
                    putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                        dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                    })
                }, "decoder", origin = IrDeclarationOrigin.DEFINED)

                var index = 0

                +irWhile().apply loop@{
                    condition = irBoolean(true)
                    body = irBlock {
                        val indexVariable = irTemporary(irCall(decodeElementIndex).apply {
                            dispatchReceiver = irGet(decoder)
                            putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                                dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                            })
                        })
                        val nonSerializableBranches = nonSerializable.map {
                            val elementIndex = index++
                            irBranch(
                                irEquals(irGet(indexVariable), irInt(elementIndex)),
                                irCall(NonSerializableProperty.TYPE.getPropertySetter("index")!!).apply {
                                    dispatchReceiver = irGetField(coroutineReceiver, it)
                                    putValueArgument(0, irCall(decodeSerializableElement).apply {
                                        dispatchReceiver = irGet(decoder)
                                        putTypeArgument(0, it.type)
                                        putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                                            dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                                        })
                                        putValueArgument(1, irInt(elementIndex))
                                        putValueArgument(2, irGetObject(IntSerializer))
                                    })
                                }
                            )
                        }

                        val serializableBranches = serializable.map {
                            val elementIndex = index++
                            suspendableCalls[it.type]?.let { childCoroutine ->
                                val serializerField =
                                    childCoroutine.fields.single { it.name == Name.identifier("serializer") }

                                return@map irBranch(
                                    irEquals(irGet(indexVariable), irInt(elementIndex)),
                                    irCall(decodeSerializableElement).apply {
                                        dispatchReceiver = irGet(decoder)
                                        putTypeArgument(0, it.type)
                                        putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                                            dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                                        })
                                        putValueArgument(1, irInt(elementIndex))
                                        putValueArgument(
                                            2,
                                            irGetField(irGetField(coroutineReceiver, it), serializerField)
                                        )
                                    }
                                )
                            }
                            val variableSerializer = (it.type as IrSimpleType).makeSerializer(
                                it.builder(),
                                generator,
                                serializationContext
                            )
                            irBranch(
                                irEquals(irGet(indexVariable), irInt(elementIndex)),
                                irSetField(coroutineReceiver, it, irCall(decodeSerializableElement).apply {
                                    dispatchReceiver = irGet(decoder)
                                    putTypeArgument(0, it.type)
                                    putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                                        dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                                    })
                                    putValueArgument(1, irInt(elementIndex))
                                    putValueArgument(2, variableSerializer)
                                })
                            )
                        }

                        val endBranch = irBranch(
                            irEquals(
                                irGet(indexVariable),
                                irInt(kotlinx.serialization.encoding.CompositeDecoder.DECODE_DONE)
                            ), irBreak(this@loop)
                        )

                        +irWhen(
                            pluginContext.irBuiltIns.unitType,
                            nonSerializableBranches + serializableBranches + endBranch
                        )
                    }
                }

                +irCall(DEndStructure).apply {
                    dispatchReceiver = irGet(decoder)
                    putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                        dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                    })
                }

                +irReturn(coroutineReceiver)
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun fillEncoders(
        coroutineReceiver: IrExpression,
        coroutineSerializer: CoroutineInfo,
        serializable: List<IrField>,
        nonSerializable: List<IrField>,
        generator: BaseIrGenerator,
    ) {
        val suspendableCalls = functionToClass.values.associate { it.coroutine.defaultType to it.coroutine }
        coroutineSerializer.encoder.apply {
            body = builder().irBlockBody {
                val encoder = irTemporary(irCall(EBeginStructure).apply {
                    dispatchReceiver = irGet(coroutineSerializer.encoder.valueParameters[0])
                    putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                        dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                    })
                }, "encoder", origin = IrDeclarationOrigin.DEFINED)

                var index = 0

                for (field in nonSerializable) {
                    appendFieldToLambda(coroutineSerializer, field, isOptional = false)

                    /**
                     * Constructor index:
                     * if(field != null) encoder.encodeSerializableElement(0, Int.serializer(), field.index)
                     */

                    +irIfThen(irNot(
                        irEqualsNull(
                            irGetField(
                                irGet(coroutineSerializer.encoder.valueParameters[1]),
                                field
                            )
                        )
                    ),
                        irCall(encodeSerializableElement).apply {
                            dispatchReceiver = irGet(encoder)

                            putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                                dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                            })
                            putValueArgument(1, irInt(index++))
                            putValueArgument(2, irGetObject(IntSerializer))
                            putValueArgument(
                                3,
                                irCall(NonSerializableProperty.TYPE.getPropertyGetter("index")!!).apply {
                                    dispatchReceiver =
                                        irGetField(irGet(coroutineSerializer.encoder.valueParameters[1]), field)
                                }
                            )
                        })
                }

                serializable.forEach { field ->
                    suspendableCalls[field.type]?.let {
                        val serializerField = it.fields.single { it.name == Name.identifier("serializer") }

                        appendFieldToLambda(coroutineSerializer, field, field.builder().run {
                            irGetField(irGetField(coroutineReceiver, field), serializerField)
                        }, true)

                        val stateIndexField = it.fields.single { it.name == Name.special("<stateIndex>") }

                        +irIfThen(
                            irNot(irEquals(irGetField(irGetField(coroutineReceiver, field), stateIndexField), irInt(0))),
                            irCall(encodeSerializableElement).apply {
                                dispatchReceiver = irGet(encoder)

                                putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                                    dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                                })
                                putValueArgument(1, irInt(index++))
                                putValueArgument(2, irGetField(irGetField(coroutineReceiver, field), serializerField))
                                putValueArgument(
                                    3,
                                    irGetField(coroutineReceiver, field)
                                )
                            }
                        )

                        return@forEach
                    }

                    val variableSerializer = (field.type as IrSimpleType).makeSerializer(
                        field.builder(),
                        generator,
                        serializationContext
                    )
                    appendFieldToLambda(coroutineSerializer, field, variableSerializer, field.type.isNullable())

                    /**
                     * if(field != null) encoder.encodeSerializableElement(0, serializer, field)
                     */
                    val serializer = {
                        irCall(encodeSerializableElement).apply {
                            dispatchReceiver = irGet(encoder)

                            putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                                dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                            })
                            putValueArgument(1, irInt(index++))
                            putValueArgument(2, variableSerializer)
                            putValueArgument(
                                3,
                                irGetField(irGet(coroutineSerializer.encoder.valueParameters[1]), field)
                            )
                        }
                    }

                    if (field.type.isNullable()) {
                        +irIfThen(
                            irNot(
                                irEqualsNull(
                                    irGetField(
                                        irGet(coroutineSerializer.encoder.valueParameters[1]),
                                        field
                                    )
                                )
                            ),
                            serializer()
                        )
                    } else {
                        +serializer()
                    }

                }

                +irCall(EEndStructure).apply {
                    dispatchReceiver = irGet(encoder)
                    putValueArgument(0, irCall(coroutineSerializer.descriptor.getter!!).apply {
                        dispatchReceiver = irGet(coroutineSerializer.serializer.thisReceiver!!)
                    })
                }
            }
        }
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    private fun appendFieldToLambda(
        coroutineSerializer: CoroutineInfo,
        field: IrField,
        variableSerializer: IrExpression? = null,
        isOptional: Boolean
    ) {
        coroutineSerializer.lambda.apply {
            builder {
                (body as IrBlockBody).statements.add(
                    irCall(elementType).apply {
                        dispatchReceiver = irGet(extensionReceiverParameter!!)
                        putValueArgument(0, irString(field.name.asString()))
                        putValueArgument(
                            1,
                            irCall(KSerializer.owner.properties.single { it.name.identifier == "descriptor" }.getter!!).apply {
                                dispatchReceiver = variableSerializer ?: irGetObject(IntSerializer)
                            }
                        )
                        putValueArgument(3, irBoolean(isOptional))
                    }
                )
            }
        }
    }
}

private class PropertySetterCollector(val original: IrVariable) : IrElementTransformerVoid() {
    val setters = ArrayList<IrExpression>()

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        if (expression.symbol == original.symbol) setters += expression.value
        return super.visitSetValue(expression)
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class SerializablePropertyRemapper(
    val lastAccess: HashMap<IrVariable, IrValueAccessExpression>,
    val localVariables: MutableSet<IrVariable>,
    val receiver: IrValueParameter,
    val old: IrVariable,
    val new: IrField,
    var settersOnly: Boolean,
) : IrElementTransformerVoid() {
    override fun visitGetValue(expression: IrGetValue): IrExpression {
        if (expression.symbol == old.symbol && !settersOnly) {
            new.builder {
                return super.visitExpression(if (lastAccess[expression.symbol.owner] == expression) {
                    irBlock {
                        val temp = irTemporary(irGetField(irGet(receiver), new))
                        localVariables.add(temp)
                        +irSetField(irGet(receiver), new, irNull())
                        +irGet(temp)
                    }
                } else {
                    irGetField(irGet(receiver), new)
                })
            }
        }
        return super.visitGetValue(expression)
    }

    @OptIn(UnsafeDuringIrConstructionAPI::class)
    override fun visitSetValue(expression: IrSetValue): IrExpression {
        if (expression.symbol == old.symbol) {
            new.builder {
                return super.visitExpression(if (lastAccess[expression.symbol.owner] == expression) {
                    irBlock {
                        +expression.value
                        +irSetField(irGet(receiver), new, irNull())
                    }
                } else {
                    irSetField(irGet(receiver), new, expression.value)
                })
            }
        }
        return super.visitSetValue(expression)
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class NonSerializablePropertyRemapper(
    val lastAccess: HashMap<IrVariable, IrValueAccessExpression>,
    val localVariables: MutableSet<IrVariable>,
    val receiver: IrValueParameter, val old: IrVariable, val new: IrField,
) :
    IrElementTransformerVoid() {
    val getter = NonSerializableProperty.TYPE.functionByName("get")
    val setter = NonSerializableProperty.TYPE.functionByName("set")
    var index = 1

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        if (expression.symbol == old.symbol) {
            new.builder {
                return super.visitExpression(if (lastAccess[expression.symbol.owner] == expression) {
                    irBlock {
                        val temp = irTemporary(irCall(getter).apply {
                            dispatchReceiver = irGetField(irGet(receiver), new)
                        })
                        localVariables.add(temp)
                        +irSetField(irGet(receiver), new, irNull())
                        +irGet(temp)
                    }
                } else {
                    irCall(getter).apply {
                        dispatchReceiver = irGetField(irGet(receiver), new)
                    }
                })
            }
        }
        return super.visitGetValue(expression)
    }

    override fun visitSetValue(expression: IrSetValue): IrExpression {
        if (expression.symbol == old.symbol) {
            new.builder {
                return super.visitExpression(if (lastAccess[expression.symbol.owner] == expression) {
                    irBlock {
                        +expression.value
                        +irSetField(irGet(receiver), new, irNull())
                    }
                } else {
                    irCall(setter).apply {
                        dispatchReceiver = irGetField(irGet(receiver), new)
                        putValueArgument(0, irInt(index++)) // Переключаем конструктор
                    }
                })
            }
            if (lastAccess[expression.symbol.owner] == expression) {
                new.builder {
                    return irBlock {
                        val field = irSetField(irGet(receiver), new, expression.value)
                        +field
                        super.visitExpression(field)
                        +irSetField(irGet(receiver), new, irNull())
                    }
                }
            } else {
                new.builder {
                    return irCall(setter).apply {
                        dispatchReceiver = irGetField(irGet(receiver), new)
                        putValueArgument(0, irInt(index++)) // Переключаем конструктор
                    }
                }
            }

        }
        return super.visitSetValue(expression)
    }
}

private fun IrClass.createLambda(expression: IrExpression): IrExpression {
    val irBuiltIns = pluginContext.irBuiltIns
    val irFactory = pluginContext.irFactory
    val lambdaType = irBuiltIns.functionN(0).typeWith(expression.type) // () -> T

    builder {
        // Создаём анонимную функцию
        val lambdaFunction = irFactory.buildFun {
            name = SpecialNames.ANONYMOUS
            returnType = expression.type
            visibility = DescriptorVisibilities.LOCAL
            origin = IrDeclarationOrigin.LOCAL_FUNCTION_FOR_LAMBDA
        }.apply {
            parent = this@createLambda

            body = irExprBody(expression)
        }

        // Заворачиваем функцию в IrFunctionExpression (лямбду)
        return IrFunctionExpressionImpl(
            startOffset, endOffset,
            lambdaType,
            lambdaFunction,
            IrStatementOrigin.LAMBDA
        )
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T : IrDeclaration> T.builder(body: DeclarationIrBuilder.(T) -> Unit = {}): DeclarationIrBuilder {
    contract {
        callsInPlace(body, InvocationKind.EXACTLY_ONCE)
    }
    return pluginContext.irBuiltIns.createIrBuilder(symbol, startOffset, endOffset).apply { body(this@builder) }
}