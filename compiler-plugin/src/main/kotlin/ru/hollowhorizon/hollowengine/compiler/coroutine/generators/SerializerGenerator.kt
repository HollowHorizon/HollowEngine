@file:OptIn(UnsafeDuringIrConstructionAPI::class)

package ru.hollowhorizon.hollowengine.compiler.coroutine.generators

import org.jetbrains.kotlin.backend.common.lower.irNot
import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addFunction
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrField
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrBlockBody
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.classOrFail
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.getPropertyGetter
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.BaseIrGenerator
import ru.hollowhorizon.hollowengine.compiler.coroutine.serializers.*
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.builder
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isAsyncController
import ru.hollowhorizon.hollowengine.compiler.coroutine.util.isSuspendable
import ru.hollowhorizon.hollowengine.compiler.pluginContext
import ru.hollowhorizon.hollowengine.compiler.serializationContext

private val elementType = pluginContext.referenceClass(
    ClassId.topLevel(FqName("kotlinx.serialization.descriptors.ClassSerialDescriptorBuilder"))
)!!.functionByName("element")
private val encodeSerializableElement = pluginContext.referenceClass(
    ClassId.topLevel(FqName("kotlinx.serialization.encoding.CompositeEncoder"))
)!!.functionByName("encodeSerializableElement")

fun CoroutineGenerator.createSerializer(
    values: Collection<IrField> = serializableFields + asyncs,
) {
    val coroutineType = coroutine.defaultType
    val serializerGenerator = object : BaseIrGenerator(coroutine, serializationContext) {}

    serializer.addFunction {
        name = Name.identifier("serialize")
    }.apply {
        val encoder = addValueParameter(Name.identifier("encoder"), Encoder.defaultType)
        addValueParameter(Name.identifier("value"), coroutineType)
        returnType = pluginContext.irBuiltIns.unitType
        overriddenSymbols += SerializationStrategy
        dispatchReceiverParameter = serializer.thisReceiver
        body = builder().irBlockBody {
            if (values.isEmpty()) return@irBlockBody
            val vEncoder = irTemporary(irCall(EBeginStructure).apply {
                dispatchReceiver = irGet(encoder)
                putValueArgument(0, irCall(descriptorProperty.getter!!).apply {
                    dispatchReceiver = irGet(serializer.thisReceiver!!)
                })
            }, "encoder", origin = IrDeclarationOrigin.DEFINED)

            values.forEachIndexed { index, field ->
                val variableSerializer = buildFieldSerializer(field, serializerGenerator)

                serializerDescriptor.reflectionTarget?.owner?.appendField(field, variableSerializer)

                +irIfThen(
                    pluginContext.irBuiltIns.unitType,
                    irNot(irEqualsNull(irGetField(irGet(coroutine.thisReceiver!!), field))),
                    irCall(encodeSerializableElement).apply {
                        dispatchReceiver = irGet(vEncoder)

                        putValueArgument(0, irCall(descriptorProperty.getter!!).apply {
                            dispatchReceiver = irGet(serializer.thisReceiver!!)
                        })
                        putValueArgument(1, irInt(index))
                        putValueArgument(2, variableSerializer)
                        putValueArgument(3, irGetField(irGet(coroutine.thisReceiver!!), field))
                    })
            }

            +irCall(EEndStructure).apply {
                dispatchReceiver = irGet(vEncoder)
                putValueArgument(0, irCall(descriptorProperty.getter!!).apply {
                    dispatchReceiver = irGet(serializer.thisReceiver!!)
                })
            }
        }
    }
    serializer.addFunction {
        name = Name.identifier("deserialize")
    }.apply {
        addValueParameter(Name.identifier("decoder"), Decoder.defaultType)
        returnType = coroutineType
        overriddenSymbols += DeserializationStrategy
        dispatchReceiverParameter = serializer.thisReceiver
        body = builder().irBlockBody {
            if (values.isEmpty()) return@irBlockBody
            val decoder = irTemporary(irCall(DBeginStructure).apply {
                dispatchReceiver = irGet(valueParameters[0])
                putValueArgument(0, irCall(descriptorProperty.getter!!).apply {
                    dispatchReceiver = irGet(serializer.thisReceiver!!)
                })
            }, "decoder", origin = IrDeclarationOrigin.DEFINED)


            +irWhile().apply loop@{
                condition = irBoolean(true)
                body = irBlock {
                    val indexVariable = irTemporary(irCall(decodeElementIndex).apply {
                        dispatchReceiver = irGet(decoder)
                        putValueArgument(0, irCall(descriptorProperty.getter!!).apply {
                            dispatchReceiver = irGet(serializer.thisReceiver!!)
                        })
                    })
                    val serializableBranches = values.mapIndexed { elementIndex, field ->
                        val variableSerializer = buildFieldSerializer(field, serializerGenerator)
                        irBranch(
                            irEquals(irGet(indexVariable), irInt(elementIndex)),
                            irSetField(irGet(coroutine.thisReceiver!!), field, irCall(decodeSerializableElement).apply {
                                dispatchReceiver = irGet(decoder)
                                putTypeArgument(0, field.type)
                                putValueArgument(0, irCall(descriptorProperty.getter!!).apply {
                                    dispatchReceiver = irGet(serializer.thisReceiver!!)
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
                        serializableBranches + endBranch
                    )
                }
            }

            +irCall(DEndStructure).apply {
                dispatchReceiver = irGet(decoder)
                putValueArgument(0, irCall(descriptorProperty.getter!!).apply {
                    dispatchReceiver = irGet(serializer.thisReceiver!!)
                })
            }

            +irReturn(irGet(coroutine.thisReceiver!!))
        }
    }
}

private fun CoroutineGenerator.buildFieldSerializer(
    field: IrField,
    serializerGenerator: BaseIrGenerator,
): IrExpression {
    return when {
        field.type.isSuspendable() || field.type.isAsyncController() -> {
            field.builder().run {
                irCall(field.type.classOrFail.getPropertyGetter("serializer")!!).apply {
                    dispatchReceiver = irGetField(irGet(coroutine.thisReceiver!!), field)
                }
            }
        }
        else -> {
            (field.type as IrSimpleType).makeSerializer(
                field.builder(),
                serializerGenerator
            ) ?: error("${field.name} not serializable")
        }
    }
}

private fun IrFunction.appendField(
    field: IrField,
    variableSerializer: IrExpression,
    isOptional: Boolean = true,
) = builder {
    (body as IrBlockBody).statements.add(
        irCall(elementType).apply {
            dispatchReceiver = irGet(extensionReceiverParameter!!)
            putValueArgument(0, irString(field.name.asString()))
            putValueArgument(
                1,
                irCall(KSerializer.owner.properties.single { it.name.identifier == "descriptor" }.getter!!).apply {
                    dispatchReceiver = variableSerializer
                }
            )
            putValueArgument(3, irBoolean(isOptional))
        }
    )
}