package ru.hollowhorizon.hollowengine.katari.processor

internal class KatariBindingCodegen(
    private val scriptTypes: List<ScriptTypeModel>,
    private val functions: List<FunctionModel>,
    private val classes: List<ClassModel>,
    private val properties: List<PropertyModel> = emptyList(),
    private val enumTypes: List<EnumTypeModel> = emptyList(),
) {
    private val callableFunctions = functions + classes.flatMap { it.constructors + it.functions }
    private val importAliases = callableFunctions
        .mapNotNull { function -> function.importQualifiedName?.let { function to it } }
        .withIndex()
        .associate { (index, pair) -> pair.first to "generatedKatariFunction$index" }
    private val propertyImportAliases = properties
        .mapNotNull { property -> property.importQualifiedName?.let { property to it } }
        .withIndex()
        .associate { (index, pair) -> pair.first to "generatedKatariProperty$index" }

    fun generate(): String = buildString {
        appendLine("package ru.hollowhorizon.hollowengine.common.scripting.katari")
        appendLine()
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.CustomFunctionParameter")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.DefaultArgumentMarker")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.ExtensionProperty")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.model.NullValue")
        appendLine("import kotlinx.coroutines.launch")
        appendLine("import net.minecraft.server.MinecraftServer")
        appendLine("import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedKatariErrorResponse")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedRuntimeValueResponse")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime")
        importAliases.forEach { (function, alias) ->
            appendLine("import ${function.importQualifiedName} as $alias")
        }
        propertyImportAliases.forEach { (property, alias) ->
            appendLine("import ${property.importQualifiedName} as $alias")
        }
        appendLine()
        appendLine("@Suppress(\"UNUSED_PARAMETER\")")
        appendLine("internal fun NarrativeBindingsBuilder.registerGeneratedKatariBindings(server: MinecraftServer) {")
        registerTypes()
        registerEnums()
        registerFunctions(callableFunctions)
        registerProperties(classes.flatMap { it.properties } + properties)
        appendLine("}")
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
                "listOf(${superTypes.joinToString(prefix = "\"", postfix = "\"")})"
            }
            appendLine("    registerHostType(")
            appendLine("        ${scriptType.targetType}::class,")
            appendLine("        \"${scriptType.typeId}\",")
            appendLine("        $superTypesArgument,")
            appendLine("        ${scriptType.snapshotType}::class,")
            appendLine("        ${scriptType.snapshotType}.serializer(),")
            appendLine("        serialize = { ${scriptType.snapshotType}.capture(it) },")
            appendLine("        deserialize = { snapshot, context -> snapshot.restore(context) },")
            appendLine("    )")
        }
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
            if (function.isSuspend) appendSuspendableFunction(function) else appendImmediateFunction(function)
        }
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
    }

    private fun StringBuilder.appendSignature(function: FunctionModel) {
        appendLine("        listOf(")
        function.parameters.forEach { parameter ->
            val typeExpression = if (parameter.isVararg) {
                "${parameter.type.katariTypeExpression}.repeated()"
            } else {
                parameter.type.katariTypeExpression
            }
            appendLine("            CustomFunctionParameter(\"${parameter.name}\", \"$typeExpression\"${if (parameter.isVararg) ", modifiers=setOf(\"vararg\")" else ""}),")
        }
        appendLine("        ),")
        appendLine("        returnType = \"${function.returnType.katariTypeExpression}\",")
        if (function.parameters.any { it.hasDefault }) {
            appendLine("        parameterDefaults = listOf(")
            function.parameters.forEach { parameter ->
                appendLine("            ${if (parameter.hasDefault) "DefaultArgumentMarker" else "null"},")
            }
            appendLine("        ),")
        }

        function.receiver?.let {
            appendLine("            receiverType = \"${it.katariTypeExpression}\",")
        }
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
            if (!parameter.hasDefault) {
                appendLine(
                    "${indent}val ${parameter.name} = ${
                        parameter.type.convertExpression(
                            "${parameter.name}Argument",
                            parameter.name
                        )
                    }"
                )
            }
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
        appendDefaultBranches(function, fixedParameters, vararg, indent)
    }

    private fun StringBuilder.appendDefaultBranches(
        function: FunctionModel,
        fixedParameters: List<ParameterModel>,
        vararg: ParameterModel?,
        indent: String,
    ) {
        val defaultParameters = fixedParameters.filter { it.hasDefault }

        if (defaultParameters.isEmpty()) {
            appendReturn(function, callArguments(function, fixedParameters, vararg, emptyList()), indent)
            return
        }

        val checkCondition = defaultParameters.joinToString(" || ") { "${it.name}Argument === DefaultArgumentMarker" }

        appendLine("${indent}if ($checkCondition) {")

        val minimalArgs = callArguments(function, fixedParameters, vararg, defaultParameters)
        appendReturn(function, minimalArgs, indent + "    ")

        appendLine("${indent}} else {")

        appendProvidedDefaultLocals(defaultParameters, indent + "    ")
        val fullArgs = callArguments(function, fixedParameters, vararg, emptyList())
        appendReturn(function, fullArgs, indent + "    ")

        appendLine("${indent}}")
    }

    private fun StringBuilder.appendProvidedDefaultLocals(parameters: List<ParameterModel>, indent: String) {
        parameters.forEach { parameter ->
            appendLine(
                "${indent}val ${parameter.name} = ${
                    parameter.type.convertExpression(
                        "${parameter.name}Argument",
                        parameter.name
                    )
                }"
            )
        }
    }

    private fun callArguments(
        function: FunctionModel,
        fixedParameters: List<ParameterModel>,
        vararg: ParameterModel?,
        omitted: List<ParameterModel>,
    ): List<String> {
        return buildList {
            if (function.passesReceiverAsArgument) add("receiver")
            fixedParameters.filterNot { it in omitted }.forEach { add("${it.name} = ${it.name}") }
            vararg?.let { add("${it.name} = ${it.name}") }
        }
    }

    private fun StringBuilder.appendReturn(function: FunctionModel, arguments: List<String>, indent: String) {
        val aliasedCall = importAliases[function]?.let { function.call.replace("__CALL__", it) } ?: function.call
        val call = aliasedCall.replace("__ARGS__", arguments.joinToString())
        if (function.returnType.kotlinType == "Unit") {
            appendLine("$indent$call")
            appendLine("${indent}KatariGeneratedBindingRuntime.toRuntimeValue(Unit, symbolTable = context.symbolTable)")
        } else {
            appendLine("${indent}KatariGeneratedBindingRuntime.toRuntimeValue($call, ${function.returnType.returnHostTypeExpression()}, symbolTable = context.symbolTable)")
        }
    }

    private fun defaultCombinations(parameters: List<ParameterModel>): List<List<ParameterModel>> {
        return parameters.fold(listOf<List<ParameterModel>>(emptyList())) { combinations, parameter ->
            combinations + combinations.map { it + parameter }
        }.sortedWith(compareByDescending<List<ParameterModel>> { it.size }.thenBy { list ->
            list.joinToString { it.name }
        })
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
}
