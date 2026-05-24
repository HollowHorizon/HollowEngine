package ru.hollowhorizon.hollowengine.katari.processor

internal class KatariBindingCodegen(
    private val scriptTypes: List<ScriptTypeModel>,
    private val functions: List<FunctionModel>,
    private val classes: List<ClassModel>,
    private val properties: List<PropertyModel> = emptyList(),
    private val enumTypes: List<EnumTypeModel> = emptyList(),
    private val events: List<EventModel> = emptyList(),
) {
    private val callableFunctions = functions + classes.flatMap { it.constructors + it.functions } + events.flatMap { it.functions }
    private val importAliases = callableFunctions
        .mapNotNull { function -> function.importQualifiedName?.let { function to it } }
        .withIndex()
        .associate { (index, pair) -> pair.first to "generatedKatariFunction$index" }
    private val propertyImportAliases = properties
        .mapNotNull { property -> property.importQualifiedName?.let { property to it } }
        .withIndex()
        .associate { (index, pair) -> pair.first to "generatedKatariProperty$index" }
    private val eventSnapshotNames = events.associate { it.type.targetType to it.safeSnapshotName() }

    fun generate(): String = buildString {
        appendLine("package ru.hollowhorizon.hollowengine.common.scripting.katari")
        appendLine()
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionDefinition")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.ExtensionProperty")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.FunctionBodyFormat")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.FunctionModifier")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.FunctionResponse")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallContext")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallDispatchContext")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallResult")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeCallable")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.NullValue")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.SourcePosition")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.TypeParameter")
        appendLine("import kotlinx.coroutines.launch")
        appendLine("import kotlinx.serialization.SerialName")
        appendLine("import kotlinx.serialization.Serializable")
        appendLine("import net.minecraft.server.MinecraftServer")
        appendLine("import ru.hollowhorizon.hollowengine.common.events.Event")
        appendLine("import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedKatariErrorResponse")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedRuntimeValueResponse")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot")
        importAliases.forEach { (function, alias) ->
            appendLine("import ${function.importQualifiedName} as $alias")
        }
        propertyImportAliases.forEach { (property, alias) ->
            appendLine("import ${property.importQualifiedName} as $alias")
        }
        appendLine()
        appendGeneratedEventSnapshots()
        appendLine()
        appendLine("@Suppress(\"UNUSED_PARAMETER\")")
        appendLine("internal fun NarrativeBindingsBuilder.registerGeneratedKatariBindings(server: MinecraftServer? = null) {")
        registerTypes()
        registerEnums()
        registerFunctions(callableFunctions)
        registerProperties(classes.flatMap { it.properties } + properties + events.flatMap { it.properties })
        appendLine("}")
        appendLine()
        appendLine("internal fun generatedKatariTypeSuperTypes(): Map<String, Set<String>> = mapOf(")
        appendGeneratedTypeSuperTypes()
        appendLine(")")
        appendLine()
        appendGeneratedEventTypes()
    }

    private fun StringBuilder.appendGeneratedEventSnapshots() {
        events.sortedBy { it.type.typeId }.forEach { event ->
            val snapshotName = event.safeSnapshotName()
            appendLine("@Serializable")
            appendLine("@SerialName(\"${event.serialName}\")")
            val classKeyword = if (event.constructorParameters.isEmpty()) "class" else "data class"
            appendLine("private $classKeyword $snapshotName(")
            event.constructorParameters.forEach { field ->
                appendLine("    val ${field.name}: ${field.snapshotStorageType()},")
            }
            appendLine(") : ValueSnapshot(), ScriptSnapshot<${event.className}> {")
            appendLine("    override suspend fun restore(context: ValueRestoreContext): ${event.className} {")
            event.constructorParameters.forEach { field ->
                appendLine("        val ${field.name}Value = ${field.restoreExpression("context")}")
            }
            appendLine("        return ${event.className}(")
            event.constructorParameters.forEach { field ->
                appendLine("            ${field.name} = ${field.name}Value,")
            }
            appendLine("        )")
            appendLine("    }")
            appendLine()
            appendLine("    companion object {")
            appendLine("        fun capture(value: ${event.className}): $snapshotName {")
            appendLine("            return $snapshotName(")
            event.constructorParameters.forEach { field ->
                appendLine("                ${field.name} = ${field.captureExpression("value")},")
            }
            appendLine("            )")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
            appendLine()
        }
    }

    private fun EventFieldModel.snapshotStorageType(): String {
        val base = snapshotType?.safeSnapshotName() ?: type.kotlinType
        return if (type.nullable) "$base?" else base
    }

    private fun EventFieldModel.restoreExpression(contextName: String): String {
        val snapshot = snapshotType ?: return name
        val restored = "$name.restore($contextName)${restoreCast(snapshot)}"
        return if (type.nullable) "$name?.let { it.restore($contextName)${restoreCast(snapshot)} }" else restored
    }

    private fun EventFieldModel.restoreCast(snapshot: ScriptTypeModel): String {
        val targetType = type.kotlinType.removeSuffix("?")
        return if (snapshot.targetType == targetType) "" else " as $targetType"
    }

    private fun EventFieldModel.captureExpression(valueName: String): String {
        val snapshot = snapshotType ?: return "$valueName.$propertyName"
        val snapshotName = snapshot.safeSnapshotName()
        val capture = "$snapshotName.capture($valueName.$propertyName)"
        return if (type.nullable) "$valueName.$propertyName?.let { $snapshotName.capture(it) }" else capture
    }

    private fun EventModel.safeSnapshotName(): String {
        return snapshotName.substringAfterLast('.').sanitizeKotlinIdentifier()
    }

    private fun ScriptTypeModel.safeSnapshotName(): String {
        return eventSnapshotNames[targetType] ?: snapshotType
    }

    private fun String.sanitizeKotlinIdentifier(): String {
        return replace(Regex("[^A-Za-z0-9]+"), "_")
            .split('_')
            .filter(String::isNotBlank)
            .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } }
            .ifBlank { "GeneratedKatariSnapshot" }
    }

    private fun StringBuilder.appendGeneratedTypeSuperTypes() {
        val orderedTypes = orderedScriptTypes()
        val types = orderedTypes.associate { it.targetType to it.typeId }
        orderedTypes.forEach { scriptType ->
            val superTypes = scriptType.superTypes.map { types[it] ?: error("Snapshot for $it not found!") }
            if (superTypes.isEmpty()) return@forEach
            appendLine("    \"${scriptType.typeId}\" to setOf(${superTypes.joinToString { "\"$it\"" }}),")
        }
    }

    private fun StringBuilder.registerEnums() {
        enumTypes.sortedBy { it.typeId }.forEach { enumType ->
            appendLine("    registerEnum(${enumType.kotlinType}::class, \"${enumType.typeId}\", ${enumType.kotlinType}::class.java.enumConstants.toList())")
        }
    }

    private fun StringBuilder.registerTypes() {
        val orderedTypes = orderedScriptTypes()
        val types = orderedTypes.associate { it.targetType to it.typeId }
        orderedTypes.forEach { scriptType ->
            val superTypes = scriptType.superTypes.map { types[it] ?: error("Snapshot for $it not found!") }
            val superTypesArgument = if (superTypes.isEmpty()) {
                "emptyList()"
            } else {
                "listOf(${superTypes.joinToString { "\"$it\"" }})"
            }
            appendLine("    registerHostType(")
            appendLine("        ${scriptType.targetType}::class,")
            appendLine("        \"${scriptType.typeId}\",")
            appendLine("        $superTypesArgument,")
            val snapshotName = scriptType.safeSnapshotName()
            appendLine("        $snapshotName::class,")
            appendLine("        $snapshotName.serializer(),")
            appendLine("        serialize = { $snapshotName.capture(it) },")
            appendLine("        deserialize = { snapshot, context -> snapshot.restore(context) },")
            appendLine("    )")
            appendLine("    KatariGeneratedBindingRuntime.registerHostType(")
            appendLine("        ${scriptType.targetType}::class,")
            appendLine("        \"${scriptType.typeId}\",")
            appendLine("        $superTypesArgument,")
            appendLine("    )")
        }
    }

    private fun StringBuilder.appendGeneratedEventTypes() {
        appendLine("internal fun generatedKatariEventTypes(): List<KatariEventType<out Event>> = listOf(")
        events.sortedBy { it.type.typeId }
            .filter { it.handlerExpression != null }
            .forEach { event ->
                appendLine("    KatariEventType(\"${event.type.typeId}\", ${event.handlerExpression}),")
            }
        appendLine(")")
    }

    private fun orderedScriptTypes(): List<ScriptTypeModel> {
        val byTarget = scriptTypes.associateBy { it.targetType }
        val result = mutableListOf<ScriptTypeModel>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(type: ScriptTypeModel) {
            if (type.targetType in visited) return
            check(visiting.add(type.targetType)) { "Cycle in Katari @ScriptType inheritance at ${type.targetType}" }
            type.superTypes.mapNotNull(byTarget::get).sortedBy { it.typeId }.forEach(::visit)
            visiting.remove(type.targetType)
            visited += type.targetType
            result += type
        }

        scriptTypes.sortedBy { it.typeId }.forEach(::visit)
        return result
    }

    private fun StringBuilder.registerFunctions(functions: List<FunctionModel>) {
        if (functions.isEmpty()) return
        functions.forEach { function ->
            when {
                function.inlineBody != null -> appendInlineFunction(function)
                function.isSuspend -> appendSuspendableFunction(function)
                else -> appendImmediateFunction(function)
            }
        }
    }

    private fun StringBuilder.appendInlineFunction(function: FunctionModel) {
        appendLine("    register(object : NarrativeCallable {")
        appendLine("        override val id: String = \"${function.scriptName}\"")
        appendLine("        override val receiverType: String? = ${function.receiver?.katariTypeExpression?.let { "\"$it\"" } ?: "null"}")
        appendLine("        override val returnType: String = \"${function.returnType.katariTypeExpression}\"")
        appendLine("        override val typeParameters: List<TypeParameter> = ${function.typeParameterListExpression()}")
        appendLine("        override val valueParameters: List<CustomFunctionParameter> = listOf(")
        function.parameters.forEach { parameter ->
            val typeExpression = if (parameter.isVararg) {
                "${parameter.type.katariTypeExpression}.repeated()"
            } else {
                parameter.type.katariTypeExpression
            }
            appendLine("            ${parameter.parameterExpression(typeExpression)},")
        }
        appendLine("        )")
        appendLine("        override val semanticFunctionDefinition: CustomFunctionDefinition = CustomFunctionDefinition(")
        appendLine("            position = SourcePosition.BUILTIN,")
        appendLine("            receiverType = receiverType,")
        appendLine("            functionName = id,")
        appendLine("            returnType = returnType,")
        appendLine("            typeParameters = typeParameters,")
        appendLine("            parameterTypes = valueParameters,")
        appendLine("            modifiers = setOf(FunctionModifier.inline),")
        appendLine("            inlineFunctionBody = \"${function.parserInlineBody().escapeKotlinString()}\",")
        appendLine("            inlineFunctionBodyFormat = FunctionBodyFormat.${function.inlineBodyFormat.name},")
        appendLine("            executable = { _, _, _, _ -> error(\"Inline Katari binding `${function.scriptName}` must be compiled before execution\") },")
        appendLine("        )")
        appendLine("        override suspend fun startCall(arguments: List<RuntimeValue>, context: NarrativeCallContext): NarrativeCallResult =")
        appendLine("            error(\"Inline Katari binding `${function.scriptName}` must be compiled before execution\")")
        appendLine("        override suspend fun resumeCall(arguments: List<RuntimeValue>, response: FunctionResponse?, context: NarrativeCallContext): NarrativeCallResult =")
        appendLine("            error(\"Inline Katari binding `${function.scriptName}` cannot be resumed\")")
        appendLine("        override fun dispatch(arguments: List<RuntimeValue>, context: NarrativeCallDispatchContext, resume: (FunctionResponse?) -> Unit) {")
        appendLine("            error(\"Inline Katari binding `${function.scriptName}` cannot be dispatched\")")
        appendLine("        }")
        appendLine("    })")
    }

    private fun StringBuilder.appendImmediateFunction(function: FunctionModel) {
        appendLine("    immediateFunction(")
        appendLine("        \"${function.scriptName}\",")
        appendSignature(function)
        appendLine("    ) { arguments, context ->")
        appendInvocation(function, indent = "            ")
        appendLine("    }")
    }

    private fun StringBuilder.appendSuspendableFunction(function: FunctionModel) {
        appendLine("    if (server == null) {")
        appendSemanticOnlyFunction(function)
        appendLine("    } else {")
        appendLine("    suspendableFunction(")
        appendLine("        \"${function.scriptName}\",")
        appendSignature(function)
        appendLine("    onDispatch = { arguments, context, resume ->")
        appendLine("        server.coroutineScope.launch {")
        appendLine("            val result = runCatching {")
        appendInvocation(function, indent = "                ")
        appendLine("            }")
        appendLine("            resume(")
        appendLine("                result.fold(")
        appendLine("                    onSuccess = { GeneratedRuntimeValueResponse(it) },")
        appendLine("                    onFailure = { GeneratedKatariErrorResponse(it.message ?: it::class.java.simpleName) },")
        appendLine("                )")
        appendLine("            )")
        appendLine("        }")
        appendLine("    },")
        appendLine("    onResume = { arguments, response, resume ->")
        appendLine("        when (response) {")
        appendLine("            is GeneratedRuntimeValueResponse -> response.value")
        appendLine("            is GeneratedKatariErrorResponse -> error(response.message)")
        appendLine("            else -> NullValue")
        appendLine("        }")
        appendLine("    },")
        appendLine(")")
        appendLine("    }")
    }

    private fun StringBuilder.appendSemanticOnlyFunction(function: FunctionModel) {
        appendLine("        immediateFunction(")
        appendLine("            \"${function.scriptName}\",")
        appendSignature(function)
        appendLine("        ) { _, _ ->")
        appendLine("            NullValue")
        appendLine("        }")
    }

    private fun StringBuilder.appendSignature(function: FunctionModel) {
        appendLine("        listOf(")
        function.parameters.forEach { parameter ->
            val typeExpression = if (parameter.isVararg) {
                "${parameter.type.katariTypeExpression}.repeated()"
            } else {
                parameter.type.katariTypeExpression
            }
            appendLine("            ${parameter.parameterExpression(typeExpression)},")
        }
        appendLine("        ),")
        appendLine("        returnType = \"${function.returnType.katariTypeExpression}\",")
        if (function.typeParameters.isNotEmpty()) {
            appendLine("        typeParameters = ${function.typeParameterListExpression()},")
        }

        function.receiver?.let {
            appendLine("            receiverType = \"${it.katariTypeExpression}\",")
        }
    }

    private fun ParameterModel.parameterExpression(typeExpression: String): String {
        val arguments = buildList {
            add("\"$name\"")
            add("\"$typeExpression\"")
            defaultValueExpression
                // Katari пока не поддерживает Float, используем такой костыль
                ?.let { it.toFloatOrNull()?.toDouble()?.toString() ?: it }
                ?.let { add("defaultValueExpression = \"${it.escapeKotlinString()}\"") }
            if (isVararg) add("modifiers = setOf(\"vararg\")")
        }
        return "CustomFunctionParameter(${arguments.joinToString()})"
    }

    private fun StringBuilder.appendInvocation(function: FunctionModel, indent: String) {
        function.receiver?.let {
            appendLine("${indent}val receiver = ${it.convertExpression("arguments.getOrNull(0)", "receiver")}")
        }
        val receiverOffset = if (function.receiver == null) 0 else 1
        val fixedParameters = function.parameters.filterNot { it.isVararg }
        val vararg = function.parameters.firstOrNull { it.isVararg }
        fixedParameters.forEachIndexed { index, parameter ->
            val offset = receiverOffset + index
            appendLine("${indent}val ${parameter.name}Argument = arguments.getOrNull($offset)")
            appendLine(
                "${indent}val ${parameter.name} = ${
                    parameter.type.convertExpression(
                        "${parameter.name}Argument",
                        parameter.name
                    )
                }"
            )
        }
        vararg?.let { parameter ->
            val offset = receiverOffset + fixedParameters.size
            appendLine("${indent}val ${parameter.name}Arguments = arguments.drop($offset)")
            appendLine(
                "${indent}val ${parameter.name} = ${
                    parameter.type.varargArrayExpression(
                        "${parameter.name}Arguments",
                        parameter.name
                    )
                }"
            )
        }
        appendReturn(function, callArguments(function, fixedParameters, vararg), indent)
    }

    private fun callArguments(
        function: FunctionModel,
        fixedParameters: List<ParameterModel>,
        vararg: ParameterModel?,
    ): List<String> {
        return buildList {
            if (function.passesReceiverAsArgument) add("receiver")
            fixedParameters.forEach { add("${it.name} = ${it.name}") }
            vararg?.let { add("${it.name} = ${it.name}") }
        }
    }

    private fun StringBuilder.appendReturn(function: FunctionModel, arguments: List<String>, indent: String) {
        val aliasedCall = importAliases[function]?.let {
            function.call.replace("__CALL__", it + function.kotlinTypeArguments())
        } ?: function.call
        val call = aliasedCall.replace("__ARGS__", arguments.joinToString())
        if (function.returnType.kotlinType == "Unit") {
            appendLine("$indent$call")
            appendLine("${indent}KatariGeneratedBindingRuntime.toRuntimeValue(Unit, symbolTable = context.symbolTable)")
        } else {
            appendLine("${indent}KatariGeneratedBindingRuntime.toRuntimeValue($call, ${function.returnType.returnHostTypeExpression()}, symbolTable = context.symbolTable)")
        }
    }

    private fun FunctionModel.kotlinTypeArguments(): String {
        if (typeParameters.isEmpty()) return ""
        return typeParameters.joinToString(prefix = "<", postfix = ">") { parameter ->
            parameter.upperBound?.kotlinType ?: "Any?"
        }
    }

    private fun FunctionModel.parserInlineBody(): String {
        val body = inlineBody.orEmpty()
        return when (inlineBodyFormat) {
            InlineBodyFormat.Expression -> "= $body"
            else -> body
        }
    }

    private fun FunctionModel.typeParameterListExpression(): String {
        if (typeParameters.isEmpty()) return "emptyList()"
        return typeParameters.joinToString(prefix = "listOf(", postfix = ")") { it.definitionExpression() }
    }

    private fun StringBuilder.registerProperties(properties: List<PropertyModel>) {
        properties.forEach { property ->
            val getter = property.resolvedGetter()
            appendLine("    registerKotliteExtensionProperty(")
            appendLine("        ExtensionProperty(")
            appendLine("            declaredName = \"${property.scriptName}\",")
            appendLine("            receiver = \"${property.receiver.katariTypeExpression}\",")
            appendLine("            type = \"${property.valueType.katariTypeExpression}\",")
            appendLine("            getter = { interpreter, receiver, _ ->")
            appendLine(
                "                val typedReceiver = ${
                    property.receiver.convertExpression(
                        "receiver",
                        "${property.scriptName} receiver"
                    )
                }"
            )
            appendLine("                KatariGeneratedBindingRuntime.toRuntimeValue($getter, ${property.valueType.returnHostTypeExpression()}, interpreter.symbolTable())")
            appendLine("            },")
            if (property.writable && property.setter != null) {
                val setter = property.resolvedSetter()
                appendLine("        setter = { interpreter, receiver, value, _ ->")
                appendLine(
                    "            val typedReceiver = ${
                        property.receiver.convertExpression(
                            "receiver",
                            "${property.scriptName} receiver"
                        )
                    }"
                )
                appendLine(
                    "            $setter = ${
                        property.valueType.convertExpression(
                            "value",
                            property.scriptName
                        )
                    }"
                )
                appendLine("        },")
            }
            appendLine("        )")
            appendLine("    )")
        }
    }

    private fun PropertyModel.resolvedGetter(): String {
        return getter.replace("__PROPERTY__", propertyImportAliases[this] ?: scriptName)
    }

    private fun PropertyModel.resolvedSetter(): String {
        return setter?.replace("__PROPERTY__", propertyImportAliases[this] ?: scriptName).orEmpty()
    }

    private fun String.escapeKotlinString(): String {
        return buildString {
            this@escapeKotlinString.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    }
}
