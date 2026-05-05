package ru.hollowhorizon.hollowengine.katari.processor

import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSType

internal data class ScriptTypeModel(
    val typeId: String,
    val targetType: String,
    val snapshotType: String,
    val superTypes: List<String>,
    val source: KSFile?,
)

internal data class EnumTypeModel(
    val typeId: String,
    val kotlinType: String,
    val source: KSFile?,
)

internal data class ParameterModel(
    val name: String,
    val type: TypeModel,
    val hasDefault: Boolean,
    val defaultValueExpression: String? = null,
    val isVararg: Boolean,
)

internal data class TypeParameterModel(
    val name: String,
    val upperBound: TypeModel?,
) {
    fun definitionExpression(): String {
        return upperBound?.let { "TypeParameter(\"$name\", \"${it.katariTypeExpression}\")" }
            ?: "TypeParameter(\"$name\", null)"
    }
}

internal data class FunctionModel(
    val scriptName: String,
    val receiver: TypeModel?,
    val parameters: List<ParameterModel>,
    val returnType: TypeModel,
    val typeParameters: List<TypeParameterModel> = emptyList(),
    val call: String,
    val isSuspend: Boolean,
    val passesReceiverAsArgument: Boolean,
    val importQualifiedName: String?,
) {
    val source: KSFile? = null

    fun signatureKey(): String {
        return buildString {
            append(scriptName)
            append("|")
            append(receiver?.katariTypeExpression ?: "")
            append("|")
            append(parameters.joinToString { parameter ->
                buildString {
                    append(parameter.type.katariTypeExpression)
                    if (parameter.hasDefault) append("=")
                    if (parameter.isVararg) append("*")
                }
            })
        }
    }
}

internal data class ClassModel(
    val type: ScriptTypeModel,
    val constructors: List<FunctionModel>,
    val functions: List<FunctionModel>,
    val properties: List<PropertyModel>,
    val source: KSFile?,
)

internal data class PropertyModel(
    val scriptName: String,
    val receiver: TypeModel,
    val receiverKotlinType: String,
    val valueType: TypeModel,
    val writable: Boolean,
    val getter: String,
    val setter: String?,
    val importQualifiedName: String?,
    val source: KSFile?,
)

internal data class PropertySignature(
    val scriptName: String,
    val receiver: TypeModel,
)

internal data class TypeModel(
    val kotlinType: String,
    val katariTypeExpression: String,
    val hostTypeId: String?,
    val converter: String?,
    val enumTypeId: String?,
    val nullable: Boolean,
    val arguments: List<TypeModel> = emptyList(),
    val genericName: String? = null,
    val genericUpperBound: TypeModel? = null,
    val collectionKind: CollectionKind? = null,
) {
    fun returnHostTypeExpression(): String = (hostTypeId ?: enumTypeId)?.let { "\"$it\"" } ?: "null"

    fun convertExpression(valueExpression: String, name: String): String {
        val nonNull = when {
            collectionKind == CollectionKind.LIST -> listConvertExpression(valueExpression, name)
            collectionKind == CollectionKind.MAP -> mapConvertExpression(valueExpression, name)
            genericName != null -> genericUpperBound?.convertExpression(valueExpression, name)
                ?: "KatariGeneratedBindingRuntime.asAny($valueExpression, \"$name\")"
            hostTypeId != null -> "KatariGeneratedBindingRuntime.asHost<$kotlinType>($valueExpression, \"$hostTypeId\", \"$name\")"
            enumTypeId != null -> "KatariGeneratedBindingRuntime.asEnum<$kotlinType>($valueExpression, \"$enumTypeId\", \"$name\")"
            converter != null -> "KatariGeneratedBindingRuntime.$converter($valueExpression, \"$name\")"
            kotlinType == "Unit" -> "Unit"
            else -> error("Unsupported type model `$this`")
        }
        return if (!nullable) nonNull
        else "KatariGeneratedBindingRuntime.nullable($valueExpression) { value -> ${nonNull.replace(valueExpression, "value")} }"
    }

    fun varargArrayExpression(valuesExpression: String, name: String): String {
        return when (kotlinType) {
            "Boolean" -> "BooleanArray($valuesExpression.size) { index -> KatariGeneratedBindingRuntime.asBoolean($valuesExpression[index], \"$name[\$index]\") }"
            "Int" -> "IntArray($valuesExpression.size) { index -> KatariGeneratedBindingRuntime.asInt($valuesExpression[index], \"$name[\$index]\") }"
            "Double" -> "DoubleArray($valuesExpression.size) { index -> KatariGeneratedBindingRuntime.asDouble($valuesExpression[index], \"$name[\$index]\") }"
            "Float" -> "FloatArray($valuesExpression.size) { index -> KatariGeneratedBindingRuntime.asFloat($valuesExpression[index], \"$name[\$index]\") }"
            else -> "Array($valuesExpression.size) { index -> ${convertExpression("$valuesExpression[index]", "$name[\$index]")} }"
        }
    }

    private fun listConvertExpression(valueExpression: String, name: String): String {
        val element = arguments.single()
        return "KatariGeneratedBindingRuntime.asList($valueExpression, \"$name\") { item, index -> ${
            element.convertExpression("item", "$name[\$index]")
        } }"
    }

    private fun mapConvertExpression(valueExpression: String, name: String): String {
        val key = arguments[0]
        val value = arguments[1]
        return "KatariGeneratedBindingRuntime.asMap($valueExpression, \"$name\", " +
                "convertKey = { item, index -> ${key.convertExpression("item", "$name key[\$index]")} }, " +
                "convertValue = { item, index -> ${value.convertExpression("item", "$name value[\$index]")} })"
    }

    companion object {
        fun unit(nullable: Boolean = false) = TypeModel(
            kotlinType = "Unit",
            katariTypeExpression = if (nullable) "Unit?" else "Unit",
            hostTypeId = null,
            converter = null,
            enumTypeId = null,
            nullable = nullable,
        )

        fun any(nullable: Boolean = true) = TypeModel(
            kotlinType = if (nullable) "Any?" else "Any",
            katariTypeExpression = if (nullable) "Any?" else "Any",
            hostTypeId = null,
            converter = "asAny",
            enumTypeId = null,
            nullable = nullable,
        )

        fun generic(name: String, upperBound: TypeModel?, nullable: Boolean) = TypeModel(
            kotlinType = upperBound?.kotlinType ?: if (nullable) "Any?" else "Any",
            katariTypeExpression = "$name${if (nullable) "?" else ""}",
            hostTypeId = upperBound?.hostTypeId,
            converter = upperBound?.converter,
            enumTypeId = upperBound?.enumTypeId,
            nullable = nullable,
            genericName = name,
            genericUpperBound = upperBound,
        )

        fun collection(
            kotlinBaseType: String,
            katariBaseType: String,
            arguments: List<TypeModel>,
            kind: CollectionKind,
            nullable: Boolean,
        ) = TypeModel(
            kotlinType = "$kotlinBaseType<${arguments.joinToString { it.kotlinType }}>",
            katariTypeExpression = "$katariBaseType<${arguments.joinToString { it.katariTypeExpression }}>${if (nullable) "?" else ""}",
            hostTypeId = null,
            converter = null,
            enumTypeId = null,
            nullable = nullable,
            arguments = arguments,
            collectionKind = kind,
        )

        fun primitive(
            kotlinType: String,
            katariTypeExpression: String,
            converter: String,
            nullable: Boolean,
        ) = TypeModel(
            kotlinType = kotlinType,
            katariTypeExpression = if (nullable) "$katariTypeExpression?" else katariTypeExpression,
            hostTypeId = null,
            converter = converter,
            enumTypeId = null,
            nullable = nullable,
        )

        fun host(type: KSType, scriptType: ScriptTypeModel, nullable: Boolean = type.isMarkedNullable) = TypeModel(
            kotlinType = type.declaration.qualifiedName?.asString() ?: type.declaration.simpleName.asString(),
            katariTypeExpression = if (nullable) {
                "${scriptType.typeId}?"
            } else {
                scriptType.typeId
            },
            hostTypeId = scriptType.typeId,
            converter = null,
            enumTypeId = null,
            nullable = nullable,
        )

        fun enum(type: KSType, typeId: String, nullable: Boolean = type.isMarkedNullable) = TypeModel(
            kotlinType = type.declaration.qualifiedName?.asString() ?: type.declaration.simpleName.asString(),
            katariTypeExpression = if (nullable) {
                "$typeId?"
            } else {
                typeId
            },
            hostTypeId = null,
            converter = null,
            enumTypeId = typeId,
            nullable = nullable,
        )
    }
}

internal enum class CollectionKind {
    LIST,
    MAP,
}
