package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.geary.components.ai.EntityReference
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties

enum class FieldValueKind {
    TEXT,
    NUMBER,
    BOOLEAN,
    ENUM,
    UUID,
    RESOURCE_LOCATION,
    VEC3,
    ENTITY_REFERENCE,
    CLASS,
    LIST,
    UNSUPPORTED,
}

data class ComponentSchema(
    val key: String,
    val descriptorId: ResourceLocation? = null,
    val displayName: String,
    val icon: String,
    val serializer: KSerializer<Any>,
    val ownerType: KType?,
    val valueKind: FieldValueKind,
    val fields: List<ComponentFieldSchema>,
)

data class ComponentFieldSchema(
    val ownerSchemaKey: String,
    val name: String,
    val displayName: String,
    val icon: String,
    val serializer: KSerializer<Any>,
    val ownerType: KType?,
    val valueKind: FieldValueKind,
    val defaultJson: kotlinx.serialization.json.JsonElement?,
    val nestedSchemaKey: String? = null,
    val listElementType: KType? = null,
    val listElementSerializer: KSerializer<Any>? = null,
    val listElementKind: FieldValueKind? = null,
    val listElementSchemaKey: String? = null,
    val enumEntries: List<String> = emptyList(),
    val range: EditorRange? = null,
)

object ComponentSchemaRegistry {
    fun componentSchemas(): List<ComponentSchema> = buildSnapshot().componentSchemas

    fun nestedSchemas(): List<ComponentSchema> = buildSnapshot().nestedSchemas

    fun allSchemas(): Map<String, ComponentSchema> = buildSnapshot().schemasByKey

    fun schema(key: String): ComponentSchema? = buildSnapshot().schemasByKey[key]

    fun descriptorSchema(id: ResourceLocation): ComponentSchema? = schema(id.toString())

    fun schemaFor(ownerType: KType?, serializer: KSerializer<Any>): ComponentSchema? {
        return allSchemas().values.firstOrNull {
            it.ownerType == ownerType && it.serializer.descriptor.serialName == serializer.descriptor.serialName
        } ?: allSchemas().values.firstOrNull {
            it.serializer.descriptor.serialName == serializer.descriptor.serialName
        }
    }

    fun displayName(id: ResourceLocation): String =
        descriptorSchema(id)?.displayName ?: id.toString().substringAfter(':').humanizeComponentName()

    fun icon(id: ResourceLocation): String =
        descriptorSchema(id)?.icon ?: "hollowengine:textures/gui/icons/autocomplete_class.svg"

    private fun buildSnapshot(): SchemaSnapshot {
        val builder = SchemaSnapshotBuilder()
        val components = ComponentDescriptorRegistry
            .map { it.value }
            .sortedBy { it.id.toString() }
            .map { builder.componentSchema(it) }

        return SchemaSnapshot(
            schemasByKey = builder.schemasByKey.toMap(),
            componentSchemas = components,
            nestedSchemas = builder.schemasByKey.values.filter { it.descriptorId == null }.sortedBy { it.displayName },
        )
    }

    private data class SchemaSnapshot(
        val schemasByKey: Map<String, ComponentSchema>,
        val componentSchemas: List<ComponentSchema>,
        val nestedSchemas: List<ComponentSchema>,
    )

    private class SchemaSnapshotBuilder {
        val schemasByKey = linkedMapOf<String, ComponentSchema>()

        fun componentSchema(descriptor: ComponentDescriptor<*>): ComponentSchema {
            val key = descriptor.id.toString()
            return schemasByKey.getOrPut(key) {
                buildSchema(
                    key = key,
                    descriptorId = descriptor.id,
                    serializer = descriptor.serializer as KSerializer<Any>,
                    ownerType = descriptor.value.createType(),
                    displayName = resolveSchemaDisplayName(descriptor.serializer as KSerializer<Any>),
                    icon = resolveSchemaIcon(descriptor.serializer as KSerializer<Any>),
                )
            }
        }

        private fun nestedSchema(
            serializer: KSerializer<Any>,
            ownerType: KType?,
            displayNameOverride: String? = null,
            iconOverride: String? = null,
        ): ComponentSchema {
            val key = buildNestedSchemaKey(serializer, ownerType)
            return schemasByKey.getOrPut(key) {
                buildSchema(
                    key = key,
                    descriptorId = null,
                    serializer = serializer,
                    ownerType = ownerType,
                    displayName = displayNameOverride ?: resolveSchemaDisplayName(serializer),
                    icon = iconOverride ?: resolveSchemaIcon(serializer),
                )
            }
        }

        private fun buildSchema(
            key: String,
            descriptorId: ResourceLocation?,
            serializer: KSerializer<Any>,
            ownerType: KType?,
            displayName: String,
            icon: String,
        ): ComponentSchema {
            val valueKind = resolveFieldValueKind(ownerType, serializer)
            val fields = if (valueKind == FieldValueKind.CLASS) {
                buildFieldSchemas(key, serializer, ownerType)
            } else {
                emptyList()
            }
            return ComponentSchema(
                key = key,
                descriptorId = descriptorId,
                displayName = displayName,
                icon = icon,
                serializer = serializer,
                ownerType = ownerType,
                valueKind = valueKind,
                fields = fields,
            )
        }

        private fun buildFieldSchemas(
            ownerSchemaKey: String,
            serializer: KSerializer<Any>,
            ownerType: KType?,
        ): List<ComponentFieldSchema> {
            val fields = ArrayList<ComponentFieldSchema>()
            val ownerDefaults = AutoEditor.defaultJson(serializer) as? JsonObject
            for (index in 0 until serializer.descriptor.elementsCount) {
                val fieldName = serializer.descriptor.getElementName(index)
                val fieldDescriptor = serializer.descriptor.getElementDescriptor(index)
                val annotations = serializer.descriptor.getElementAnnotations(index)
                if (annotations.any { it is EditorHidden }) continue

                val fieldType = resolveFieldType(ownerType, fieldName)
                val fieldSerializer = AutoEditor.serializerOrNull(fieldType)
                    ?: serializerFromDescriptorKind(fieldDescriptor.kind)
                    ?: continue
                val fieldKind = resolveFieldValueKind(fieldType, fieldSerializer)
                val range = annotations.filterIsInstance<EditorRange>().firstOrNull()

                val (nestedSchemaKey, listElementType, listElementSerializer, listElementKind, listElementSchemaKey) =
                    resolveNestedMetadata(fieldType, fieldKind, fieldSerializer)

                fields += ComponentFieldSchema(
                    ownerSchemaKey = ownerSchemaKey,
                    name = fieldName,
                    displayName = annotations.filterIsInstance<EditorName>().firstOrNull()?.name
                        ?: fieldName.humanizeComponentName(),
                    icon = annotations.filterIsInstance<EditorIcon>().firstOrNull()?.icon
                        ?: "hollowengine:textures/gui/icons/autocomplete_class.svg",
                    serializer = fieldSerializer,
                    ownerType = fieldType,
                    valueKind = fieldKind,
                    defaultJson = ownerDefaults?.get(fieldName) ?: fieldSerializer.let(AutoEditor::defaultJson),
                    nestedSchemaKey = nestedSchemaKey,
                    listElementType = listElementType,
                    listElementSerializer = listElementSerializer,
                    listElementKind = listElementKind,
                    listElementSchemaKey = listElementSchemaKey,
                    enumEntries = if (fieldKind == FieldValueKind.ENUM) {
                        (0 until fieldSerializer.descriptor.elementsCount).map(fieldSerializer.descriptor::getElementName)
                    } else {
                        emptyList()
                    },
                    range = range,
                )
            }
            return fields
        }

        private fun resolveNestedMetadata(
            fieldType: KType?,
            fieldKind: FieldValueKind,
            fieldSerializer: KSerializer<Any>,
        ): NestedMetadata {
            return when (fieldKind) {
                FieldValueKind.CLASS -> {
                    val schema = nestedSchema(fieldSerializer, fieldType)
                    NestedMetadata(
                        nestedSchemaKey = schema.key,
                    )
                }

                FieldValueKind.LIST -> {
                    val elementType = fieldType?.arguments?.firstOrNull()?.type
                    val elementSerializer = AutoEditor.serializerOrNull(elementType)
                        ?: serializerFromDescriptorKind(fieldSerializer.descriptor.getElementDescriptor(0).kind)
                    val elementKind = elementSerializer?.let { resolveFieldValueKind(elementType, it) }
                    val elementSchemaKey = if (elementSerializer != null && elementKind == FieldValueKind.CLASS) {
                        nestedSchema(elementSerializer, elementType).key
                    } else {
                        null
                    }
                    NestedMetadata(
                        listElementType = elementType,
                        listElementSerializer = elementSerializer,
                        listElementKind = elementKind,
                        listElementSchemaKey = elementSchemaKey,
                    )
                }

                else -> NestedMetadata()
            }
        }

        private fun buildNestedSchemaKey(serializer: KSerializer<Any>, ownerType: KType?): String {
            val owner = ownerType?.toString()?.replace(' ', '_')
            return buildString {
                append("schema:")
                append(serializer.descriptor.serialName.replace('.', '/'))
                if (!owner.isNullOrBlank()) {
                    append(':')
                    append(owner)
                }
            }
        }

        private data class NestedMetadata(
            val nestedSchemaKey: String? = null,
            val listElementType: KType? = null,
            val listElementSerializer: KSerializer<Any>? = null,
            val listElementKind: FieldValueKind? = null,
            val listElementSchemaKey: String? = null,
        )
    }
}

fun resolveSchemaDisplayName(serializer: KSerializer<Any>): String {
    return serializer.descriptor.annotations.filterIsInstance<EditorName>().firstOrNull()?.name
        ?: serializer.descriptor.serialName.humanizedSerialName()
}

fun resolveSchemaIcon(serializer: KSerializer<Any>): String {
    return serializer.descriptor.annotations.filterIsInstance<EditorIcon>().firstOrNull()?.icon
        ?: "hollowengine:textures/gui/icons/autocomplete_class.svg"
}

@OptIn(InternalSerializationApi::class)
fun serializerFromDescriptorKind(kind: SerialKind): KSerializer<Any>? {
    @Suppress("UNCHECKED_CAST")
    return when (kind) {
        PrimitiveKind.STRING -> serializer<String>() as KSerializer<Any>
        PrimitiveKind.INT -> serializer<Int>() as KSerializer<Any>
        PrimitiveKind.LONG -> serializer<Long>() as KSerializer<Any>
        PrimitiveKind.SHORT -> serializer<Short>() as KSerializer<Any>
        PrimitiveKind.BYTE -> serializer<Byte>() as KSerializer<Any>
        PrimitiveKind.FLOAT -> serializer<Float>() as KSerializer<Any>
        PrimitiveKind.DOUBLE -> serializer<Double>() as KSerializer<Any>
        PrimitiveKind.BOOLEAN -> serializer<Boolean>() as KSerializer<Any>
        else -> null
    }
}

fun resolveFieldType(ownerType: KType?, elementName: String): KType? {
    val ownerClass = ownerType?.classifier as? KClass<*> ?: return null
    return ownerClass.memberProperties.firstOrNull { it.name == elementName }?.returnType
}

fun resolveFieldValueKind(ownerType: KType?, serializer: KSerializer<Any>): FieldValueKind {
    val classifier = ownerType?.classifier as? KClass<*>
    val descriptor = serializer.descriptor
    return when {
        classifier == EntityReference::class -> FieldValueKind.ENTITY_REFERENCE
        classifier == Vec3::class || descriptor.serialName == "Vector3d" -> FieldValueKind.VEC3
        classifier == UUID::class || descriptor.serialName == "Uuid" -> FieldValueKind.UUID
        classifier == ResourceLocation::class || descriptor.serialName == "Identifier" -> FieldValueKind.RESOURCE_LOCATION
        descriptor.kind == PrimitiveKind.STRING -> FieldValueKind.TEXT
        descriptor.kind == PrimitiveKind.INT ||
            descriptor.kind == PrimitiveKind.LONG ||
            descriptor.kind == PrimitiveKind.SHORT ||
            descriptor.kind == PrimitiveKind.BYTE ||
            descriptor.kind == PrimitiveKind.FLOAT ||
            descriptor.kind == PrimitiveKind.DOUBLE -> FieldValueKind.NUMBER
        descriptor.kind == PrimitiveKind.BOOLEAN -> FieldValueKind.BOOLEAN
        descriptor.kind == SerialKind.ENUM -> FieldValueKind.ENUM
        descriptor.kind == StructureKind.CLASS -> FieldValueKind.CLASS
        descriptor.kind == StructureKind.LIST -> FieldValueKind.LIST
        else -> FieldValueKind.UNSUPPORTED
    }
}

fun String.humanizedSerialName(): String = substringAfter(':').humanizeComponentName()

fun String.humanizeComponentName(): String {
    return split('_', '-', '.', '/')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        .ifBlank { this }
}
