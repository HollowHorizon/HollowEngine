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

        val annotatedClasses = resolver.getSymbolsWithAnnotation(SCRIPT_BINDING)
            .filterIsInstance<KSClassDeclaration>()
            .filter(KSAnnotated::validate)
            .toList()
        val eventScriptTypes = annotatedClasses
            .filter(::isEventType)
            .associate { declaration ->
                val className = declaration.qualifiedName?.asString() ?: return@associate "" to null
                val typeId = declaration.eventTypeId()
                className to ScriptTypeModel(
                    typeId = typeId,
                    targetType = className,
                    snapshotType = generatedEventSnapshotName(typeId),
                    superTypes = declaration.eventSuperTypes(scriptTypes),
                    source = declaration.containingFile,
                    targetKSType = declaration.asStarProjectedType(),
                    targetTypeDepth = declaration.typeDepth(),
                )
            }
            .filterKeys { it.isNotBlank() }
            .mapNotNull { (key, value) -> value?.let { key to it } }
            .toMap()
        val availableScriptTypes = scriptTypes + eventScriptTypes

        val topLevelFunctions = resolver.getSymbolsWithAnnotation(SCRIPT_BINDING)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter(KSAnnotated::validate)
            .mapNotNull { functionBinding(resolver, it, availableScriptTypes) }
            .toList()

        val extensionProperties = resolver.getSymbolsWithAnnotation(SCRIPT_BINDING)
            .filterIsInstance<KSPropertyDeclaration>()
            .filter(KSAnnotated::validate)
            .filter { it.parentDeclaration == null && it.extensionReceiver != null }
            .mapNotNull { extensionPropertyBinding(it, availableScriptTypes) }
            .toList()

        val eventBindings = annotatedClasses
            .filter(::isEventType)
            .mapNotNull { eventBinding(resolver, it, availableScriptTypes, eventScriptTypes) }
            .toList()

        val classBindings = annotatedClasses
            .filterNot(::isEventType)
            .mapNotNull { classBinding(resolver, it, scriptTypes) }
            .toList()

        val enumTypes = collectEnumTypes(topLevelFunctions, extensionProperties, classBindings, eventBindings)
        if (scriptTypes.isEmpty() && enumTypes.isEmpty() && topLevelFunctions.isEmpty() &&
            extensionProperties.isEmpty() && classBindings.isEmpty() && eventBindings.isEmpty()
        ) {
            generated = true
            return emptyList()
        }
        validateDuplicates(topLevelFunctions, extensionProperties, classBindings, eventBindings)
        generate(
            scriptTypes = (scriptTypes + eventScriptTypes).values.toList(),
            enumTypes = enumTypes,
            functions = topLevelFunctions,
            properties = extensionProperties,
            classes = classBindings,
            events = eventBindings,
        )
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
            targetKSType = target,
            targetTypeDepth = (target.declaration as? KSClassDeclaration)?.typeDepth() ?: 0,
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
                .filter { it.isScriptMemberFunction() }
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

    private fun eventBinding(
        resolver: Resolver,
        declaration: KSClassDeclaration,
        scriptTypes: Map<String, ScriptTypeModel>,
        eventScriptTypes: Map<String, ScriptTypeModel>,
    ): EventModel? {
        val className = declaration.qualifiedName?.asString() ?: return null
        if (!declaration.isPublicApi()) {
            logger.error("@ScriptBinding event class must be public", declaration)
            return null
        }
        if (declaration.typeParameters.isNotEmpty()) {
            logger.error("@ScriptBinding event class generics are not supported", declaration)
            return null
        }
        val scriptType = eventScriptTypes[className] ?: return null
        val constructor = declaration.primaryConstructor ?: run {
            if (declaration.classKind == ClassKind.CLASS && declaration.getConstructors().count() == 0) {
                return EventModel(
                    type = scriptType,
                    className = className,
                    snapshotName = scriptType.snapshotType,
                    serialName = generatedEventSerialName(scriptType.typeId),
                    constructorParameters = emptyList(),
                    functions = eventFunctionModels(resolver, declaration, scriptTypes),
                    properties = eventPropertyModels(declaration, scriptType, scriptTypes),
                    handlerExpression = declaration.eventHandlerExpression(),
                    source = declaration.containingFile,
                )
            }
            logger.error("@ScriptBinding event class `$className` must have a primary constructor", declaration)
            return null
        }
        if (!validateCallable(constructor)) return null
        val constructorParameters = constructor.parameters.mapIndexed { index, parameter ->
            eventFieldModel(resolver, parameter, "arg$index", scriptTypes) ?: return null
        }
        return EventModel(
            type = scriptType,
            className = className,
            snapshotName = scriptType.snapshotType,
            serialName = generatedEventSerialName(scriptType.typeId),
            constructorParameters = constructorParameters,
            functions = eventFunctionModels(resolver, declaration, scriptTypes),
            properties = eventPropertyModels(declaration, scriptType, scriptTypes),
            handlerExpression = declaration.eventHandlerExpression(),
            source = declaration.containingFile,
        )
    }

    private fun eventFunctionModels(
        resolver: Resolver,
        declaration: KSClassDeclaration,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): List<FunctionModel> {
        return collectAnnotatedEventParents(declaration).flatMap { owner ->
            owner.getDeclaredFunctions()
                .filter { it.isScriptMemberFunction() }
                .mapNotNull {
                    callable(
                        declaration = it,
                        scriptName = it.bindingName().ifBlank { it.simpleName.asString() },
                        receiver = declaration.asStarProjectedType(),
                        explicitReceiverExpression = null,
                        resolver = resolver,
                        scriptTypes = scriptTypes,
                    )
                }
        }
    }

    private fun eventPropertyModels(
        declaration: KSClassDeclaration,
        scriptType: ScriptTypeModel,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): List<PropertyModel> {
        val receiver = TypeModel.host(declaration.asStarProjectedType(), scriptType)
        val receiverKotlinType = declaration.qualifiedName?.asString() ?: return emptyList()
        return collectAnnotatedEventParents(declaration).flatMap { owner ->
            owner.getDeclaredProperties()
                .filter { it.isPublicApi() && !it.hasAnnotation(SCRIPT_IGNORE) && it.origin != Origin.SYNTHETIC }
                .mapNotNull { property ->
                    val type = typeModel(property.type.resolve(), scriptTypes, emptyMap(), reportUnsupported = false)
                        ?: return@mapNotNull null
                    val name = property.bindingName().ifBlank { property.simpleName.asString() }
                    val kotlinName = property.simpleName.asString()
                    PropertyModel(
                        scriptName = name,
                        receiver = receiver,
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
        }
    }

    private fun eventFieldModel(
        resolver: Resolver,
        parameter: KSValueParameter,
        fallbackName: String,
        scriptTypes: Map<String, ScriptTypeModel>,
    ): EventFieldModel? {
        val name = parameter.name?.asString() ?: fallbackName
        val resolvedType = if (parameter.isVararg) {
            varargElementType(resolver, parameter.type.resolve()) ?: run {
                logger.error("Unsupported event vararg parameter type `${parameter.type.resolve().render()}`", parameter)
                return null
            }
        } else {
            parameter.type.resolve()
        }
        val type = typeModel(resolvedType, scriptTypes) ?: return null
        return EventFieldModel(
            name = name,
            propertyName = name,
            type = type,
            snapshotType = type.hostTypeId?.let { typeId -> scriptTypes.values.firstOrNull { it.typeId == typeId } },
        )
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

    private fun collectAnnotatedEventParents(declaration: KSClassDeclaration): List<KSClassDeclaration> {
        val result = linkedMapOf<String, KSClassDeclaration>()
        fun visit(type: KSClassDeclaration) {
            val name = type.qualifiedName?.asString() ?: return
            if (name == "kotlin.Any" || name in result) return
            if (type.hasAnnotation(SCRIPT_BINDING)) {
                result[name] = type
            }
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

    private val primitiveTypes = mapOf(
        "kotlin.Unit" to { n: Boolean -> TypeModel.unit(n) },
        "kotlin.Any" to { n: Boolean -> TypeModel.any(n) },
        "kotlin.Boolean" to { n: Boolean -> TypeModel.primitive("Boolean", "Boolean", "asBoolean", n) },
        "kotlin.Int" to { n: Boolean -> TypeModel.primitive("Int", "Int", "asInt", n) },
        "kotlin.Double" to { n: Boolean -> TypeModel.primitive("Double", "Double", "asDouble", n) },
        "kotlin.Float" to { n: Boolean -> TypeModel.primitive("Float", "Double", "asFloat", n) },
        "kotlin.String" to { n: Boolean -> TypeModel.primitive("String", "String", "asString", n) },
        "com.sunnychung.lib.multiplatform.kotlite.model.XmlValue" to { n: Boolean ->
            TypeModel.primitive("XmlValue", "XmlValue", "asXml", n)
        }
    )

    private fun typeModel(
        type: KSType,
        scriptTypes: Map<String, ScriptTypeModel>,
        typeParameters: Map<String, TypeParameterModel> = emptyMap(),
        reportUnsupported: Boolean = true,
    ): TypeModel? {
        (type.declaration as? KSTypeParameter)?.let { parameter ->
            val name = parameter.name.asString()
            return TypeModel.generic(name, typeParameters[name]?.upperBound, type.isMarkedNullable)
        }

        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return null
        val nullable = type.isMarkedNullable

        primitiveTypes[qualifiedName]?.let { return it(nullable) }

        return when (qualifiedName) {
            "kotlin.collections.List", "kotlin.collections.MutableList" -> {
                resolveCollection(type, scriptTypes, typeParameters, qualifiedName, CollectionKind.LIST, 1)
            }
            "kotlin.collections.Map", "kotlin.collections.MutableMap" -> {
                resolveCollection(type, scriptTypes, typeParameters, qualifiedName, CollectionKind.MAP, 2)
            }
            else -> {
                resolveCustomOrFallbackType(type, scriptTypes, typeParameters, qualifiedName, nullable, reportUnsupported)
            }
        }
    }

    private fun resolveCollection(
        type: KSType,
        scriptTypes: Map<String, ScriptTypeModel>,
        typeParameters: Map<String, TypeParameterModel>,
        qualifiedName: String,
        kind: CollectionKind,
        expectedArguments: Int
    ): TypeModel? {
        val isMutable = qualifiedName.endsWith("Mutable${kind.name.lowercase().replaceFirstChar { it.uppercase() }}")
        val baseName = if (isMutable) "Mutable${kind.name}" else kind.name
        return collectionTypeModel(
            type = type,
            scriptTypes = scriptTypes,
            typeParameters = typeParameters,
            kotlinBaseType = baseName,
            katariBaseType = baseName,
            kind = kind,
            expectedArguments = expectedArguments,
        )
    }

    private fun resolveCustomOrFallbackType(
        type: KSType,
        scriptTypes: Map<String, ScriptTypeModel>,
        typeParameters: Map<String, TypeParameterModel>,
        qualifiedName: String,
        nullable: Boolean,
        reportUnsupported: Boolean
    ): TypeModel? {
        if (qualifiedName.startsWith("kotlin.Function")) {
            return functionTypeModel(type, scriptTypes, typeParameters)
        }

        if (type.arguments.isNotEmpty()) {
            if (reportUnsupported) {
                logger.error("Generic script binding type `${type.render()}` is not supported", type.declaration)
            }
            return null
        }

        if ((type.declaration as? KSClassDeclaration)?.classKind == ClassKind.ENUM_CLASS) {
            return TypeModel.enum(type, qualifiedName.substringAfterLast('.'), nullable)
        }

        val scriptType = findMatchingScriptType(type, scriptTypes, qualifiedName)
        if (scriptType == null) {
            if (reportUnsupported) {
                logger.error("Unsupported script binding type `$qualifiedName`; add @ScriptType snapshot first", type.declaration)
            }
            return null
        }

        return TypeModel.host(type, scriptType, nullable)
    }

    private fun findMatchingScriptType(
        type: KSType,
        scriptTypes: Map<String, ScriptTypeModel>,
        qualifiedName: String
    ): ScriptTypeModel? {
        return scriptTypes[qualifiedName] ?: scriptTypes.values
            .filter { scriptType ->
                scriptType.targetKSType?.let { target ->
                    target.isAssignableFrom(type) || type.isAssignableFrom(target)
                } == true || type.isSameOrSubtypeOf(scriptType.targetType)
            }
            .maxByOrNull { it.targetTypeDepth }
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
        events: List<EventModel>,
    ) {
        val signatures = linkedSetOf<String>()
        (functions + classes.flatMap { it.constructors + it.functions } + events.flatMap { it.functions }).forEach { function ->
            val key = function.signatureKey()
            if (!signatures.add(key)) {
                logger.error("Duplicate Katari binding signature `$key`")
            }
        }
        val propertySignatures = linkedSetOf<PropertySignature>()
        (properties + classes.flatMap { it.properties } + events.flatMap { it.properties }).forEach { property ->
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
        events: List<EventModel>,
    ): List<EnumTypeModel> {
        val result = linkedMapOf<String, EnumTypeModel>()
        fun collect(type: TypeModel) {
            val typeId = type.enumTypeId ?: return
            result[type.kotlinType] = EnumTypeModel(typeId, type.kotlinType, null)
        }
        (functions + classes.flatMap { it.constructors + it.functions } + events.flatMap { it.functions }).forEach { function ->
            function.receiver?.let(::collect)
            function.parameters.forEach { collect(it.type) }
            collect(function.returnType)
        }
        (properties + classes.flatMap { it.properties } + events.flatMap { it.properties }).forEach { property ->
            collect(property.receiver)
            collect(property.valueType)
        }
        events.forEach { event ->
            event.constructorParameters.forEach { collect(it.type) }
            event.properties.forEach { property ->
                collect(property.receiver)
                collect(property.valueType)
            }
        }
        return result.values.toList()
    }

    private fun generate(
        scriptTypes: List<ScriptTypeModel>,
        enumTypes: List<EnumTypeModel>,
        functions: List<FunctionModel>,
        properties: List<PropertyModel>,
        classes: List<ClassModel>,
        events: List<EventModel>,
    ) {
        val sources = (
                scriptTypes.mapNotNull { it.source } +
                        enumTypes.mapNotNull { it.source } +
                        functions.mapNotNull { it.source } +
                        properties.mapNotNull { it.source } +
                        classes.mapNotNull { it.source } +
                        events.mapNotNull { it.source }
                )
            .distinct()
            .toTypedArray()
        val file = codeGenerator.createNewFile(
            Dependencies(aggregating = true, sources = sources),
            GENERATED_PACKAGE,
            "GeneratedKatariBindings",
        )
        OutputStreamWriter(file, Charsets.UTF_8).use { writer ->
            writer.write(KatariBindingCodegen(scriptTypes, functions, classes, properties, enumTypes, events).generate())
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

    private fun KSFunctionDeclaration.isScriptMemberFunction(): Boolean {
        return functionKind == FunctionKind.MEMBER &&
                simpleName.asString() != "<init>" &&
                isPublicApi() &&
                !hasAnnotation(SCRIPT_IGNORE) &&
                origin != Origin.SYNTHETIC
    }

    private fun KSType.render(): String {
        val base = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val args = arguments.takeIf { it.isNotEmpty() }?.joinToString(prefix = "<", postfix = ">") {
            if (it.variance == Variance.STAR) "*" else it.type?.resolve()?.render().orEmpty()
        }.orEmpty()
        return base + args + if (isMarkedNullable) "?" else ""
    }

    private fun isEventType(declaration: KSClassDeclaration): Boolean {
        return declaration.getAllSuperTypes().any {
            it.declaration.qualifiedName?.asString() == EVENT
        }
    }

    private fun KSClassDeclaration.eventTypeId(): String {
        return bindingName().ifBlank {
            val qualifiedName = qualifiedName?.asString() ?: simpleName.asString()
            val packagePrefix = "${packageName.asString()}."
            qualifiedName.removePrefix(packagePrefix)
        }
    }

    private fun KSClassDeclaration.eventSuperTypes(scriptTypes: Map<String, ScriptTypeModel>): List<String> {
        if (qualifiedName?.asString() == EVENT) return emptyList()
        val result = linkedSetOf<String>()
        superTypes
            .map { it.resolve().declaration }
            .filterIsInstance<KSClassDeclaration>()
            .forEach { parent ->
                val parentName = parent.qualifiedName?.asString() ?: return@forEach
                when {
                    parentName in scriptTypes -> result += parentName
                    parent.hasAnnotation(SCRIPT_BINDING) && isEventType(parent) -> result += parentName
                }
            }
        if (result.isEmpty()) result += EVENT
        return result.toList()
    }

    private fun generatedEventSnapshotName(typeId: String): String {
        return typeId
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .split('_')
            .filter(String::isNotBlank)
            .joinToString(prefix = "Generated", postfix = "Snapshot") { part ->
                part.replaceFirstChar { it.uppercase() }
            }
    }

    private fun generatedEventSerialName(typeId: String): String {
        val path = typeId
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .lowercase()
        return "hollowengine:katari/generated_event/$path"
    }

    private fun KSClassDeclaration.eventHandlerExpression(): String? {
        val companion = declarations.filterIsInstance<KSClassDeclaration>()
            .firstOrNull { it.isCompanionObject } ?: return null
        val hasHandler = companion.getAllSuperTypes().any {
            it.declaration.qualifiedName?.asString() == EVENT_HANDLER
        }
        if (!hasHandler) return null
        return qualifiedName?.asString()
    }

    private fun KSClassDeclaration.typeDepth(): Int {
        return getAllSuperTypes().count {
            it.declaration.qualifiedName?.asString() != "kotlin.Any"
        }
    }

    private fun KSType.isSameOrSubtypeOf(qualifiedName: String): Boolean {
        if (declaration.qualifiedName?.asString() == qualifiedName) return true
        return (declaration as? KSClassDeclaration)?.getAllSuperTypes()
            ?.any { it.declaration.qualifiedName?.asString() == qualifiedName } == true
    }
}

private const val GENERATED_PACKAGE = "ru.hollowhorizon.hollowengine.common.scripting.katari"
private const val SCRIPT_BINDING = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding"
private const val SCRIPT_IGNORE = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptIgnore"
private const val SCRIPT_TYPE = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType"
private const val SCRIPT_SNAPSHOT = "ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot"
private const val VALUE_SNAPSHOT = "com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot"
private const val EVENT = "ru.hollowhorizon.hollowengine.common.events.Event"
private const val EVENT_HANDLER = "ru.hollowhorizon.hollowengine.common.events.factory.EventHandler"
