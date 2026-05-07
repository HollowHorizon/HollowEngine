package ru.hollowhorizon.hollowengine.katari.processor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import java.io.OutputStreamWriter

class KatariBindingProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return KatariBindingProcessor(environment.codeGenerator, environment.logger)
    }
}

private class KatariBindingProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    private var generated = false
    private val defaultValueSourceReader = KatariDefaultValueSourceReader()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (generated) return emptyList()

        val scriptTypes = resolver.getSymbolsWithAnnotation(SCRIPT_TYPE)
            .filterIsInstance<KSClassDeclaration>()
            .filter(KSAnnotated::validate)
            .mapNotNull(::scriptType)
            .associateBy { it.targetType }
        validateScriptTypeParents(scriptTypes)

        val topLevelFunctions = resolver.getSymbolsWithAnnotation(SCRIPT_BINDING)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter(KSAnnotated::validate)
            .mapNotNull { functionBinding(resolver, it, scriptTypes) }
            .toList()

        val extensionProperties = resolver.getSymbolsWithAnnotation(SCRIPT_BINDING)
            .filterIsInstance<KSPropertyDeclaration>()
            .filter(KSAnnotated::validate)
            .filter { it.parentDeclaration == null && it.extensionReceiver != null }
            .mapNotNull { extensionPropertyBinding(it, scriptTypes) }
            .toList()

        val classBindings = resolver.getSymbolsWithAnnotation(SCRIPT_BINDING)
            .filterIsInstance<KSClassDeclaration>()
            .filter(KSAnnotated::validate)
            .mapNotNull { classBinding(resolver, it, scriptTypes) }
            .toList()

        val enumTypes = collectEnumTypes(topLevelFunctions, extensionProperties, classBindings)
        if (scriptTypes.isEmpty() && enumTypes.isEmpty() && topLevelFunctions.isEmpty() &&
            extensionProperties.isEmpty() && classBindings.isEmpty()
        ) {
            generated = true
            return emptyList()
        }
        validateDuplicates(topLevelFunctions, extensionProperties, classBindings)
        generate(scriptTypes.values.toList(), enumTypes, topLevelFunctions, extensionProperties, classBindings)
        generated = true
        return emptyList()
    }

    private fun scriptType(declaration: KSClassDeclaration): ScriptTypeModel? {
        val typeId = declaration.annotationValue(SCRIPT_TYPE, "typeId").ifBlank {
            logger.error("@ScriptType requires a non-empty typeId", declaration)
            return null
        }
        val target = declaration.getAllSuperTypes()
            .firstOrNull { it.declaration.qualifiedName?.asString() == SCRIPT_SNAPSHOT }
            ?.arguments
            ?.firstOrNull()
            ?.type
            ?.resolve()
        if (target == null) {
            logger.error("@ScriptType class must implement ScriptSnapshot<T>", declaration)
            return null
        }
        if (declaration.classKind != com.google.devtools.ksp.symbol.ClassKind.CLASS) {
            logger.error("@ScriptType can only be used on snapshot classes", declaration)
            return null
        }
        val snapshotName = declaration.qualifiedName?.asString() ?: return null
        val targetName = target.declaration.qualifiedName?.asString() ?: return null
        return ScriptTypeModel(
            typeId = typeId,
            targetType = targetName,
            snapshotType = snapshotName,
            superTypes = declaration.scriptTypeSuperTypes(),
            source = declaration.containingFile,
        )
    }

    private fun KSClassDeclaration.scriptTypeSuperTypes(): List<String> {
        val annotation = annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == SCRIPT_TYPE
        } ?: return emptyList()
        val value = annotation.arguments.firstOrNull { it.name?.asString() == "superTypes" }?.value
            ?: return emptyList()
        return when (value) {
            is KSType -> listOfNotNull(value.declaration.qualifiedName?.asString())
            is List<*> -> value.mapNotNull { item ->
                (item as? KSType)?.declaration?.qualifiedName?.asString()
            }
            else -> emptyList()
        }
    }

    private fun validateScriptTypeParents(scriptTypes: Map<String, ScriptTypeModel>) {
        scriptTypes.values.forEach { scriptType ->
            scriptType.superTypes.forEach { parent ->
                if (parent !in scriptTypes) {
                    logger.error(
                        "@ScriptType `${scriptType.typeId}` references parent `$parent`, but no snapshot is registered for it"
                    )
                }
            }
        }
    }

    private fun functionBinding(
        resolver: Resolver,
        declaration: KSFunctionDeclaration,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): FunctionModel? {
        if (declaration.parentDeclaration != null) {
            logger.error("@ScriptBinding on functions is supported only for top-level declarations", declaration)
            return null
        }
        return callable(
            declaration = declaration,
            scriptName = declaration.bindingName().ifBlank { declaration.simpleName.asString() },
            receiver = declaration.extensionReceiver?.resolve(),
            explicitReceiverExpression = null,
            resolver = resolver,
            scriptTypes = scriptTypes,
        )
    }

    private fun classBinding(
        resolver: Resolver,
        declaration: KSClassDeclaration,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): ClassModel? {
        val className = declaration.qualifiedName?.asString() ?: return null
        if (!declaration.isPublicApi()) {
            logger.error("@ScriptBinding class must be public", declaration)
            return null
        }
        if (declaration.typeParameters.isNotEmpty()) {
            logger.error("@ScriptBinding class generics are not supported in v1", declaration)
            return null
        }
        if (Modifier.VALUE in declaration.modifiers) {
            logger.error("@ScriptBinding value classes are not supported in v1", declaration)
            return null
        }
        val scriptType = scriptTypes[className]
        if (scriptType == null) {
            logger.error("@ScriptBinding class `$className` must have a matching @ScriptType snapshot", declaration)
            return null
        }
        val annotatedClasses = collectAnnotatedParents(declaration).ifEmpty { return null }
        val constructors = declaration.getConstructors()
            .filter { it.isPublicApi() && !it.hasAnnotation(SCRIPT_IGNORE) }
            .mapNotNull { constructorModel(resolver, declaration, it, scriptType, scriptTypes) }
            .toList()
        val functions = annotatedClasses.flatMap { owner ->
            owner.getDeclaredFunctions()
                .filter { it.isPublicApi() && !it.hasAnnotation(SCRIPT_IGNORE) && it.origin != Origin.SYNTHETIC }
                .mapNotNull {
                    callable(
                        declaration = it,
                        scriptName = it.simpleName.asString(),
                        receiver = declaration.asStarProjectedType(),
                        explicitReceiverExpression = null,
                        resolver = resolver,
                        scriptTypes = scriptTypes,
                    )
                }
        }
        val properties = annotatedClasses.flatMap { owner ->
            owner.getDeclaredProperties()
                .filter { it.isPublicApi() && !it.hasAnnotation(SCRIPT_IGNORE) && it.origin != Origin.SYNTHETIC }
                .mapNotNull { memberPropertyModel(declaration, it, scriptType, scriptTypes) }
        }
        return ClassModel(scriptType, constructors, functions, properties, declaration.containingFile)
    }

    private fun extensionPropertyBinding(
        declaration: KSPropertyDeclaration,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): PropertyModel? {
        if (declaration.parentDeclaration != null || declaration.extensionReceiver == null) {
            logger.error("@ScriptBinding on properties is supported only for top-level extension properties", declaration)
            return null
        }
        if (!declaration.isPublicApi()) {
            logger.error("@ScriptBinding extension property must be public", declaration)
            return null
        }
        return extensionPropertyModel(declaration, scriptTypes)
    }

    private fun collectAnnotatedParents(declaration: KSClassDeclaration): List<KSClassDeclaration> {
        val result = linkedMapOf<String, KSClassDeclaration>()
        fun visit(type: KSClassDeclaration) {
            val name = type.qualifiedName?.asString() ?: return
            if (name == "kotlin.Any" || name in result) return
            if (name == VALUE_SNAPSHOT || name == SCRIPT_SNAPSHOT) return
            if (type.classKind != com.google.devtools.ksp.symbol.ClassKind.CLASS) return
            if (!type.hasAnnotation(SCRIPT_BINDING)) {
                logger.error("@ScriptBinding class parent `$name` must also have @ScriptBinding", declaration)
                return
            }
            result[name] = type
            type.superTypes.map { it.resolve().declaration }
                .filterIsInstance<KSClassDeclaration>()
                .forEach(::visit)
        }
        visit(declaration)
        return result.values.toList().asReversed()
    }

    private fun constructorModel(
        resolver: Resolver,
        owner: KSClassDeclaration,
        constructor: KSFunctionDeclaration,
        scriptType: ScriptTypeModel,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): FunctionModel? {
        if (!validateCallable(constructor)) return null
        val params = constructor.parameters.mapIndexed { index, parameter ->
            parameterModel(resolver, parameter, "arg$index", scriptTypes, emptyMap()) ?: return null
        }
        val scriptName = owner.bindingName().ifBlank { owner.simpleName.asString() }
        return FunctionModel(
            scriptName = scriptName,
            receiver = null,
            parameters = params,
            returnType = TypeModel.host(owner.asStarProjectedType(), scriptType),
            typeParameters = emptyList(),
            call = "${owner.qualifiedName?.asString()}(__ARGS__)",
            isSuspend = false,
            passesReceiverAsArgument = false,
            importQualifiedName = null,
            inlineBody = null,
            inlineBodyFormat = InlineBodyFormat.Block,
        )
    }

    private fun memberPropertyModel(
        receiverClass: KSClassDeclaration,
        property: KSPropertyDeclaration,
        receiverType: ScriptTypeModel,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): PropertyModel? {
        val type = typeModel(property.type.resolve(), scriptTypes) ?: return null
        val receiverKotlinType = receiverClass.qualifiedName?.asString() ?: return null
        val name = property.bindingName().ifBlank { property.simpleName.asString() }
        val kotlinName = property.simpleName.asString()
        return PropertyModel(
            scriptName = name,
            receiver = TypeModel.host(receiverClass.asStarProjectedType(), receiverType),
            receiverKotlinType = receiverKotlinType,
            valueType = type,
            writable = property.isMutable && property.setter?.modifiers?.let {
                Modifier.PRIVATE !in it && Modifier.PROTECTED !in it && Modifier.INTERNAL !in it
            } != false,
            getter = "typedReceiver.$kotlinName",
            setter = "typedReceiver.$kotlinName",
            importQualifiedName = null,
            source = property.containingFile,
        )
    }

    private fun extensionPropertyModel(
        property: KSPropertyDeclaration,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): PropertyModel? {
        val receiver = property.extensionReceiver?.resolve() ?: return null
        val receiverType = typeModel(receiver, scriptTypes) ?: return null
        val valueType = typeModel(property.type.resolve(), scriptTypes) ?: return null
        val receiverKotlinType = receiver.declaration.qualifiedName?.asString() ?: return null
        val qualifiedName = property.qualifiedName?.asString() ?: return null
        return PropertyModel(
            scriptName = property.bindingName().ifBlank { property.simpleName.asString() },
            receiver = receiverType,
            receiverKotlinType = receiverKotlinType,
            valueType = valueType,
            writable = property.isMutable && property.setter?.modifiers?.let {
                Modifier.PRIVATE !in it && Modifier.PROTECTED !in it && Modifier.INTERNAL !in it
            } != false,
            getter = "typedReceiver.__PROPERTY__",
            setter = "typedReceiver.__PROPERTY__",
            importQualifiedName = qualifiedName,
            source = property.containingFile,
        )
    }

    private fun callable(
        declaration: KSFunctionDeclaration,
        scriptName: String,
        receiver: KSType?,
        explicitReceiverExpression: String?,
        resolver: Resolver,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): FunctionModel? {
        if (!validateCallable(declaration)) return null
        val typeParameters = typeParameterModels(declaration, scriptTypes) ?: return null
        val typeParameterTypes = typeParameters.associateBy { it.name }
        val receiverType = receiver?.let { typeModel(it, scriptTypes, typeParameterTypes) ?: return null }
        val params = declaration.parameters.mapIndexed { index, parameter ->
            parameterModel(resolver, parameter, "arg$index", scriptTypes, typeParameterTypes) ?: return null
        }
        val returnType = declaration.returnType?.resolve()?.let { typeModel(it, scriptTypes, typeParameterTypes) } ?: TypeModel.unit()
        val callableName = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val inlineBody = if (Modifier.INLINE in declaration.modifiers) {
            defaultValueSourceReader.functionBody(declaration) ?: run {
                logger.error("Katari inline @ScriptBinding function body cannot be read from source", declaration)
                return null
            }
        } else {
            null
        }
        val call = explicitReceiverExpression ?: when {
            declaration.parentDeclaration != null -> "receiver.${declaration.simpleName.asString()}(__ARGS__)"
            declaration.extensionReceiver != null -> "receiver.__CALL__(__ARGS__)"
            else -> "__CALL__(__ARGS__)"
        }
        return FunctionModel(
            scriptName,
            receiverType,
            params,
            returnType,
            typeParameters,
            call,
            isSuspend = Modifier.SUSPEND in declaration.modifiers,
            passesReceiverAsArgument = false,
            importQualifiedName = callableName.takeIf { declaration.parentDeclaration == null },
            inlineBody = inlineBody?.body,
            inlineBodyFormat = inlineBody?.format ?: InlineBodyFormat.Block,
            source = declaration.containingFile,
        )
    }

    private fun validateCallable(declaration: KSFunctionDeclaration): Boolean {
        val vararg = declaration.parameters.withIndex().firstOrNull { it.value.isVararg }
        if (vararg != null && vararg.index != declaration.parameters.lastIndex) {
            logger.error("Katari @ScriptBinding supports vararg only as the last parameter", vararg.value)
            return false
        }
        if (declaration.parameters.count { it.isVararg } > 1) {
            logger.error("Katari @ScriptBinding supports only one vararg parameter", declaration)
            return false
        }
        return true
    }

    private fun parameterModel(
        resolver: Resolver,
        parameter: KSValueParameter,
        fallbackName: String,
        scriptTypes: Map<String, ScriptTypeModel>,
        typeParameters: Map<String, TypeParameterModel>,
    ): ParameterModel? {
        val name = parameter.name?.asString() ?: fallbackName
        val resolvedType = if (parameter.isVararg) {
            varargElementType(resolver, parameter.type.resolve()) ?: run {
                logger.error("Unsupported vararg parameter type `${parameter.type.resolve().render()}`", parameter)
                return null
            }
        } else {
            parameter.type.resolve()
        }
        val type = typeModel(resolvedType, scriptTypes, typeParameters) ?: return null
        return ParameterModel(
            name = name,
            type = type,
            hasDefault = parameter.hasDefault,
            defaultValueExpression = defaultValueSourceReader.defaultValueExpression(parameter),
            isVararg = parameter.isVararg,
        )
    }

    private fun varargElementType(resolver: Resolver, type: KSType): KSType? {
        return when (type.declaration.qualifiedName?.asString()) {
            "kotlin.Array" -> type.arguments.firstOrNull()?.type?.resolve()
            "kotlin.BooleanArray" -> resolver.getClassDeclarationByName("kotlin.Boolean")?.asStarProjectedType()
            "kotlin.IntArray" -> resolver.getClassDeclarationByName("kotlin.Int")?.asStarProjectedType()
            "kotlin.DoubleArray" -> resolver.getClassDeclarationByName("kotlin.Double")?.asStarProjectedType()
            "kotlin.FloatArray" -> resolver.getClassDeclarationByName("kotlin.Float")?.asStarProjectedType()
            else -> type.arguments.firstOrNull()?.type?.resolve()
                ?: type
        }
    }

    private fun typeParameterModels(
        declaration: KSFunctionDeclaration,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): List<TypeParameterModel>? {
        return declaration.typeParameters.map { parameter ->
            val bound = parameter.bounds
                .map { it.resolve() }
                .firstOrNull { it.declaration.qualifiedName?.asString() != "kotlin.Any" }
                ?.let { typeModel(it, scriptTypes, emptyMap()) ?: return null }
            TypeParameterModel(parameter.name.asString(), bound)
        }
    }

    private fun typeModel(
        type: KSType,
        scriptTypes: Map<String, ScriptTypeModel>,
        typeParameters: Map<String, TypeParameterModel> = emptyMap(),
    ): TypeModel? {
        (type.declaration as? KSTypeParameter)?.let { parameter ->
            val name = parameter.name.asString()
            return TypeModel.generic(name, typeParameters[name]?.upperBound, type.isMarkedNullable)
        }
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
        val nullable = type.isMarkedNullable
        return when (qualifiedName) {
            "kotlin.Unit" -> TypeModel.unit(nullable)
            "kotlin.Any" -> TypeModel.any(nullable)
            "kotlin.Boolean" -> TypeModel.primitive("Boolean", "Boolean", "asBoolean", nullable)
            "kotlin.Int" -> TypeModel.primitive("Int", "Int", "asInt", nullable)
            "kotlin.Double" -> TypeModel.primitive("Double", "Double", "asDouble", nullable)
            "kotlin.Float" -> TypeModel.primitive("Float", "Double", "asFloat", nullable)
            "kotlin.String" -> TypeModel.primitive("String", "String", "asString", nullable)
            "kotlin.collections.List", "kotlin.collections.MutableList" -> collectionTypeModel(
                type = type,
                scriptTypes = scriptTypes,
                typeParameters = typeParameters,
                kotlinBaseType = if (qualifiedName.endsWith("MutableList")) "MutableList" else "List",
                katariBaseType = if (qualifiedName.endsWith("MutableList")) "MutableList" else "List",
                kind = CollectionKind.LIST,
                expectedArguments = 1,
            )

            "kotlin.collections.Map", "kotlin.collections.MutableMap" -> collectionTypeModel(
                type = type,
                scriptTypes = scriptTypes,
                typeParameters = typeParameters,
                kotlinBaseType = if (qualifiedName.endsWith("MutableMap")) "MutableMap" else "Map",
                katariBaseType = if (qualifiedName.endsWith("MutableMap")) "MutableMap" else "Map",
                kind = CollectionKind.MAP,
                expectedArguments = 2,
            )

            else -> {
                if (qualifiedName.startsWith("kotlin.Function")) {
                    return functionTypeModel(type, scriptTypes, typeParameters)
                }
                if (type.arguments.isNotEmpty()) {
                    logger.error("Generic script binding type `${type.render()}` is not supported", type.declaration)
                    return null
                }
                if ((type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS) {
                    return TypeModel.enum(type, qualifiedName.substringAfterLast('.'), nullable)
                }
                val scriptType = scriptTypes[qualifiedName]
                if (scriptType == null) {
                    logger.error("Unsupported script binding type `$qualifiedName`; add @ScriptType snapshot first", type.declaration)
                    return null
                }
                TypeModel.host(type, scriptType, nullable)
            }
        }
    }

    private fun collectionTypeModel(
        type: KSType,
        scriptTypes: Map<String, ScriptTypeModel>,
        typeParameters: Map<String, TypeParameterModel>,
        kotlinBaseType: String,
        katariBaseType: String,
        kind: CollectionKind,
        expectedArguments: Int,
    ): TypeModel? {
        if (type.arguments.size != expectedArguments) {
            logger.error("Unsupported collection binding type `${type.render()}`", type.declaration)
            return null
        }
        val arguments = type.arguments.map { argument ->
            if (argument.variance == Variance.STAR) {
                TypeModel.any(nullable = true)
            } else {
                val resolvedArgument = argument.type?.resolve() ?: run {
                    logger.error("Unsupported collection binding type `${type.render()}`", type.declaration)
                    return null
                }
                typeModel(resolvedArgument, scriptTypes, typeParameters) ?: return null
            }
        }
        return TypeModel.collection(kotlinBaseType, katariBaseType, arguments, kind, type.isMarkedNullable)
    }

    private fun functionTypeModel(
        type: KSType,
        scriptTypes: Map<String, ScriptTypeModel>,
        typeParameters: Map<String, TypeParameterModel>,
    ): TypeModel? {
        val arguments = type.arguments.map { argument ->
            val resolvedArgument = argument.type?.resolve() ?: run {
                logger.error("Unsupported function binding type `${type.render()}`", type.declaration)
                return null
            }
            typeModel(resolvedArgument, scriptTypes, typeParameters) ?: return null
        }
        if (arguments.isEmpty()) {
            logger.error("Unsupported function binding type `${type.render()}`", type.declaration)
            return null
        }
        return TypeModel.function(
            parameterTypes = arguments.dropLast(1),
            returnType = arguments.last(),
            nullable = type.isMarkedNullable,
        )
    }

    private fun validateDuplicates(
        functions: List<FunctionModel>,
        properties: List<PropertyModel>,
        classes: List<ClassModel>,
    ) {
        val signatures = linkedSetOf<String>()
        (functions + classes.flatMap { it.constructors + it.functions }).forEach { function ->
            val key = function.signatureKey()
            if (!signatures.add(key)) {
                logger.error("Duplicate Katari binding signature `$key`")
            }
        }
        val propertySignatures = linkedSetOf<PropertySignature>()
        (properties + classes.flatMap { it.properties }).forEach { property ->
            val signature = PropertySignature(property.scriptName, property.receiver)
            if (!propertySignatures.add(signature)) {
                logger.error(
                    "Duplicate Katari property binding `${property.scriptName}` for receiver `${property.receiver.katariTypeExpression}`"
                )
            }
        }
    }

    private fun collectEnumTypes(
        functions: List<FunctionModel>,
        properties: List<PropertyModel>,
        classes: List<ClassModel>,
    ): List<EnumTypeModel> {
        val result = linkedMapOf<String, EnumTypeModel>()
        fun collect(type: TypeModel) {
            val typeId = type.enumTypeId ?: return
            result[type.kotlinType] = EnumTypeModel(typeId, type.kotlinType, null)
        }
        (functions + classes.flatMap { it.constructors + it.functions }).forEach { function ->
            function.receiver?.let(::collect)
            function.parameters.forEach { collect(it.type) }
            collect(function.returnType)
        }
        (properties + classes.flatMap { it.properties }).forEach { property ->
            collect(property.receiver)
            collect(property.valueType)
        }
        return result.values.toList()
    }

    private fun generate(
        scriptTypes: List<ScriptTypeModel>,
        enumTypes: List<EnumTypeModel>,
        functions: List<FunctionModel>,
        properties: List<PropertyModel>,
        classes: List<ClassModel>,
    ) {
        val sources = (
                scriptTypes.mapNotNull { it.source } +
                        enumTypes.mapNotNull { it.source } +
                        functions.mapNotNull { it.source } +
                        properties.mapNotNull { it.source } +
                        classes.mapNotNull { it.source }
                )
            .distinct()
            .toTypedArray()
        val file = codeGenerator.createNewFile(
            Dependencies(aggregating = true, sources = sources),
            GENERATED_PACKAGE,
            "GeneratedKatariBindings",
        )
        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write(KatariBindingCodegen(scriptTypes, functions, classes, properties, enumTypes).generate())
        }
    }

    private fun KSAnnotated.hasAnnotation(name: String): Boolean {
        return annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == name }
    }

    private fun KSAnnotated.annotationValue(annotationName: String, argumentName: String): String {
        return annotations.firstOrNull {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
        }?.arguments?.firstOrNull {
            it.name?.asString() == argumentName || argumentName == "value" && it.name?.asString() == "value"
        }
            ?.value as? String ?: ""
    }

    private fun KSAnnotated.bindingName(): String = annotationValue(SCRIPT_BINDING, "value")

    private fun KSDeclaration.isPublicApi(): Boolean {
        return Modifier.PRIVATE !in modifiers &&
                Modifier.PROTECTED !in modifiers &&
                Modifier.INTERNAL !in modifiers
    }

    private fun KSType.render(): String {
        val base = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val args = arguments.takeIf { it.isNotEmpty() }?.joinToString(prefix = "<", postfix = ">") {
            if (it.variance == Variance.STAR) "*" else it.type?.resolve()?.render().orEmpty()
        }.orEmpty()
        return base + args + if (isMarkedNullable) "?" else ""
    }
}

private const val GENERATED_PACKAGE = "ru.hollowhorizon.hollowengine.common.scripting.katari"
private const val SCRIPT_BINDING = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding"
private const val SCRIPT_IGNORE = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptIgnore"
private const val SCRIPT_TYPE = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType"
private const val SCRIPT_SNAPSHOT = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot"
private const val VALUE_SNAPSHOT = "com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot"
