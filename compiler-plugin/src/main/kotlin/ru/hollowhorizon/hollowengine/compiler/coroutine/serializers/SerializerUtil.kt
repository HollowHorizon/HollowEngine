package ru.hollowhorizon.hollowengine.compiler.coroutine.serializers

import org.jetbrains.kotlin.backend.jvm.functionByName
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrPackageFragment
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.symbols.IrClassifierSymbol
import org.jetbrains.kotlin.ir.symbols.IrTypeParameterSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classifierOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isFunctionTypeOrSubtype
import org.jetbrains.kotlin.ir.util.superClass
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.BaseIrGenerator
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.SerializationBaseContext
import org.jetbrains.kotlinx.serialization.compiler.backend.ir.findTypeSerializerOrContextUnchecked
import org.jetbrains.kotlinx.serialization.compiler.extensions.SerializationPluginContext
import org.jetbrains.kotlinx.serialization.compiler.resolve.SerializationRuntimeClassIds
import org.jetbrains.kotlinx.serialization.compiler.resolve.SerializersClassIds
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFUNCTION_PACKAGE
import ru.hollowhorizon.hollowengine.compiler.pluginContext

fun IrType.isSerializable(generator: BaseIrGenerator, context: SerializationBaseContext = generator.compilerContext): Boolean {
    if (isFunctionTypeOrSubtype()) return false
    generator.findTypeSerializerOrContextUnchecked(context, this) ?: return false

    (this as? IrSimpleType)?.arguments?.forEach {
        val type = it.typeOrNull ?: return false
        if (!type.isSerializable(generator, context)) return false
    } ?: return false

    return true
}

fun IrSimpleType.makeSerializer(
    builder: IrBuilderWithScope,
    generator: BaseIrGenerator,
    context: SerializationPluginContext = generator.compilerContext,
): IrExpression? {
    val serializer = generator.findTypeSerializerOrContextUnchecked(context, this)
        ?: error("Invalid serializer")
    builder.apply {
        generator.apply {
            return serializerInstance(
                serializer,
                context,
                this@makeSerializer,
                this@makeSerializer.genericIndex
            ) { index, type ->
                (type as IrSimpleType).makeSerializer(builder, generator, context)!!
            }
        }
    }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
fun IrClassifierSymbol.isClassWithNamePrefix(prefix: String, packageFqName: FqName): Boolean {
    val declaration = owner as IrDeclarationWithName

    return declaration.name.asString().startsWith(prefix) && (declaration.parent as? IrPackageFragment)?.packageFqName == packageFqName ||
            (declaration as? IrClass)?.superClass?.symbol?.isClassWithNamePrefix(prefix, packageFqName) == true
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
val IrType.genericIndex: Int?
    get() = (this.classifierOrNull as? IrTypeParameterSymbol)?.owner?.index

val KSerializer = pluginContext.referenceClass(
    SerializersClassIds.kSerializerId
)!!
val SerializationStrategy = pluginContext.referenceClass(
    ClassId.topLevel(FqName("kotlinx.serialization.SerializationStrategy"))
)!!.functionByName("serialize")
val DeserializationStrategy = pluginContext.referenceClass(
    ClassId.topLevel(FqName("kotlinx.serialization.DeserializationStrategy"))
)!!.functionByName("deserialize")
val SerialDescriptor = pluginContext.referenceClass(
    SerializationRuntimeClassIds.descriptorClassId
)!!
val Encoder = pluginContext.referenceClass(ClassId.topLevel(FqName("kotlinx.serialization.encoding.Encoder")))!!
val CompositeEncoder =
    pluginContext.referenceClass(ClassId.topLevel(FqName("kotlinx.serialization.encoding.CompositeEncoder")))!!
val CompositeDecoder =
    pluginContext.referenceClass(ClassId.topLevel(FqName("kotlinx.serialization.encoding.CompositeDecoder")))!!
val EBeginStructure = Encoder.functionByName("beginStructure")
val EEndStructure = CompositeEncoder.functionByName("endStructure")
val Decoder = pluginContext.referenceClass(ClassId.topLevel(FqName("kotlinx.serialization.encoding.Decoder")))!!
val DBeginStructure = Decoder.functionByName("beginStructure")
val decodeElementIndex = CompositeDecoder.functionByName("decodeElementIndex")
val decodeSerializableElement = CompositeDecoder.functionByName("decodeSerializableElement")
val DEndStructure = CompositeDecoder.functionByName("endStructure")
val serialBuilder = pluginContext.referenceFunctions(
    CallableId(
        FqName("kotlinx.serialization.descriptors"),
        Name.identifier("buildClassSerialDescriptor")
    )
).single()

val IntSerializer =
    pluginContext.referenceClass(ClassId.topLevel(FqName("kotlinx.serialization.internal.IntSerializer")))!!