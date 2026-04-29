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
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.ImmediateKatariFunctionDefinition")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.KatariCallableSignature")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.KatariParameterType")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.KatariTypes")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeBindingsBuilder")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.SuspendableKatariFunctionDefinition")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.asValueParameter")
        appendLine("import com.sunnychung.lib.multiplatform.kotlite.katari.toKatari")
        appendLine("import kotlinx.coroutines.launch")
        appendLine("import net.minecraft.server.MinecraftServer")
        appendLine("import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedKatariErrorResponse")
        appendLine("import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.GeneratedKatariValueResponse")
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
        enumTypes.sortedBy { it.typeId }.forEachIndexed { index, enumType ->
            appendLine("    val generatedEnum$index = ${enumType.kotlinType}::class.toKatari(\"${enumType.typeId}\")")
            appendLine("    registerEnum(generatedEnum$index, ${enumType.kotlinType}::class.java.enumConstants.toList())")
        }
    }

    private fun StringBuilder.registerTypes() {
        val orderedTypes = orderedScriptTypes()
        val variables = orderedTypes.withIndex().associate { (index, type) -> type.targetType to "generatedType$index" }
        orderedTypes.forEachIndexed { index, scriptType ->
            val superTypes = scriptType.superTypes.mapNotNull(variables::get)
            val superTypesArgument = if (superTypes.isEmpty()) {
                ""
            } else {
                ", superTypes = listOf(${superTypes.joinToString()})"
            }
            appendLine("    val generatedType$index = ${scriptType.targetType}::class.toKatari(\"${scriptType.typeId}\"$superTypesArgument)")
            appendLine("    registerHostType(")
            appendLine("        generatedType$index,")
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
        appendLine("    register(")
        appendLine("        listOf(")
        functions.forEach { function ->
            if (function.isSuspend) appendSuspendableFunction(function) else appendImmediateFunction(function)
        }
        appendLine("        )")
        appendLine("    )")
    }

    private fun StringBuilder.appendImmediateFunction(function: FunctionModel) {
        appendLine("            ImmediateKatariFunctionDefinition(")
        appendLine("                id = \"${function.scriptName}\",")
        appendSignature(function)
        appendLine("            ) { arguments, _ ->")
        appendInvocation(function, indent = "                ")
        appendLine("            },")
    }

    private fun StringBuilder.appendSuspendableFunction(function: FunctionModel) {
        appendLine("            SuspendableKatariFunctionDefinition(")
        appendLine("                id = \"${function.scriptName}\",")
        appendSignature(function)
        appendLine("                onDispatch = { arguments, _, resume ->")
        appendLine("                    server.coroutineScope.launch {")
        appendLine("                        val result = runCatching {")
        appendInvocation(function, indent = "                            ")
        appendLine("                        }")
        appendLine("                        resume(")
        appendLine("                            result.fold(")
        appendLine("                                onSuccess = { GeneratedKatariValueResponse(it) },")
        appendLine("                                onFailure = { GeneratedKatariErrorResponse(it.message ?: it::class.java.simpleName) },")
        appendLine("                            )")
        appendLine("                        )")
        appendLine("                    }")
        appendLine("                },")
        appendLine("                onResume = { _, response, _ ->")
        appendLine("                    when (response) {")
        appendLine("                        is GeneratedKatariValueResponse -> response.value")
        appendLine("                        is GeneratedKatariErrorResponse -> error(response.message)")
        appendLine("                        else -> KatariValue.Null")
        appendLine("                    }")
        appendLine("                },")
        appendLine("            ),")
    }

    private fun StringBuilder.appendSignature(function: FunctionModel) {
        appendLine("                signature = KatariCallableSignature(")
            function.receiver?.let {
                appendLine("                    dispatchReceiverType = ${it.katariTypeExpression},")
            }
            appendLine("                    valueParameters = listOf(")
            function.parameters.forEach { parameter ->
                val typeExpression = if (parameter.isVararg) {
                    "${parameter.type.katariTypeExpression}.repeated()"
                } else {
                    parameter.type.katariTypeExpression
                }
                appendLine("                        $typeExpression.asValueParameter(\"${parameter.name}\", hasDefault = ${parameter.hasDefault}),")
            }
            appendLine("                    ),")
            appendLine("                    returnType = ${function.returnType.katariTypeExpression},")
            appendLine("                ),")
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
                appendLine("${indent}val ${parameter.name} = ${parameter.type.convertExpression("${parameter.name}Argument", parameter.name)}")
            }
        }
        vararg?.let { parameter ->
            val offset = receiverOffset + fixedParameters.size
            appendLine("${indent}val ${parameter.name}Arguments = arguments.drop($offset)")
            appendLine("${indent}val ${parameter.name} = ${parameter.type.varargArrayExpression("${parameter.name}Arguments", parameter.name)}")
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
        val combinations = defaultCombinations(defaultParameters)
        appendLine("${indent}when {")
        combinations.dropLast(1).forEach { omitted ->
            val condition = omitted.joinToString(" && ") { "${it.name}Argument == KatariValue.DefaultArgument" }
            appendLine("$indent    $condition -> {")
            appendProvidedDefaultLocals(defaultParameters - omitted.toSet(), indent + "        ")
            val args = callArguments(function, fixedParameters, vararg, omitted)
            appendReturn(function, args, indent + "        ")
            appendLine("$indent    }")
        }
        appendLine("${indent}    else -> {")
        appendProvidedDefaultLocals(defaultParameters, indent + "        ")
        appendReturn(function, callArguments(function, fixedParameters, vararg, emptyList()), indent + "        ")
        appendLine("${indent}    }")
        appendLine("$indent}")
    }

    private fun StringBuilder.appendProvidedDefaultLocals(parameters: List<ParameterModel>, indent: String) {
        parameters.forEach { parameter ->
            appendLine("${indent}val ${parameter.name} = ${parameter.type.convertExpression("${parameter.name}Argument", parameter.name)}")
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
            appendLine("${indent}KatariGeneratedBindingRuntime.toKatariValue(Unit)")
        } else {
            appendLine("${indent}KatariGeneratedBindingRuntime.toKatariValue($call, ${function.returnType.returnHostTypeExpression()})")
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
            appendLine("    extensionProperty(")
            appendLine("        name = \"${property.scriptName}\",")
            appendLine("        receiver = ${property.receiver.katariTypeExpression},")
            appendLine("        valueType = ${property.valueType.katariTypeExpression},")
            appendLine("        getter = { receiver, _ ->")
            appendLine("            val typedReceiver = ${property.receiver.convertExpression("receiver", "${property.scriptName} receiver")}")
            appendLine("            KatariGeneratedBindingRuntime.toKatariValue($getter, ${property.valueType.returnHostTypeExpression()})")
            appendLine("        },")
            if (property.writable && property.setter != null) {
                val setter = property.resolvedSetter()
                appendLine("        setter = { receiver, value, _ ->")
                appendLine("            val typedReceiver = ${property.receiver.convertExpression("receiver", "${property.scriptName} receiver")}")
                appendLine("            $setter = ${property.valueType.convertExpression("value", property.scriptName)}")
                appendLine("        },")
            }
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
