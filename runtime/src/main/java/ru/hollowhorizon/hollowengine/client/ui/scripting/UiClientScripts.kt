package ru.hollowhorizon.hollowengine.client.ui.scripting

import com.sunnychung.lib.multiplatform.kotlite.katari.*
import com.sunnychung.lib.multiplatform.kotlite.model.*
import com.sunnychung.lib.multiplatform.kotlite.stdlib.AllStdLibModules
import kotlinx.coroutines.runBlocking
import net.minecraft.nbt.*
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.parseHssSelector
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.KatariGeneratedBindingRuntime
import java.util.ArrayDeque

sealed class UiClientScript {
    abstract val name: String
    abstract val source: String

    data class Resource(
        override val name: String,
        override val source: String,
    ) : UiClientScript()

    data class Inline(
        val kind: UiEventKind,
        override val source: String,
        val targetKey: String? = null,
        val sink: UiEventSink? = null,
    ) : UiClientScript() {
        override val name: String = "<inline:${kind.attributeName}${targetKey?.let { ":$it" }.orEmpty()}>"
    }
}

internal fun UiNode.clientScripts(): List<UiClientScript> {
    val ownScripts = modifiers
        .filterIsInstance<UiClientScriptModifier>()
        .flatMap { it.scripts } +
            modifiers
                .filterIsInstance<ScriptEventModifier>()
                .map { modifier ->
                    UiClientScript.Inline(
                        kind = modifier.kind,
                        source = modifier.source,
                        targetKey = UiNodeKeys.key(this),
                        sink = modifier.sink,
                    )
                }
    return ownScripts + children.flatMap { it.clientScripts() }
}

object UiInlineScriptRunner {
    fun run(source: String, event: UiEvent, sink: UiEventSink) {
        UiClientScriptRunner.run(UiClientScript.Inline(event.kind, source), event, event.node, sink, event.variables)
    }
}

object UiClientScriptRunner {
    private val programCache = mutableMapOf<String, Result<KatariProgram>>()

    fun run(
        script: UiClientScript,
        event: UiEvent,
        root: UiNode,
        sink: UiEventSink,
        variables: CompoundTag,
    ) {
        prepare(listOf(script), root, sink, variables).dispatch(event, root, variables)
    }

    fun prepare(
        scripts: List<UiClientScript>,
        root: UiNode,
        sink: UiEventSink,
        variables: CompoundTag,
        applyInputHints: Boolean = true,
    ): UiPreparedClientScripts {
        val prepared = scripts.mapNotNull { script ->
            val code = when (script) {
                is UiClientScript.Inline -> prepareInline(script)
                is UiClientScript.Resource -> prepareResource(script)
            }
            val scriptSink = (script as? UiClientScript.Inline)?.sink ?: sink
            val context = UiScriptExecutionContext(
                event = UiScriptEvent(UiEvent(UiEventKind.INIT, root)),
                gui = UiScriptGui(root),
                variables = UiScriptVariables(variables),
                sink = scriptSink,
            )
            val bindings = createBindings(context)
            val cacheKey = "${script.name}|${code.code}"
            val program = programCache.getOrPut(cacheKey) {
                runCatching { KatariNarrativeProgram(script.name, code.code, bindings) }.onFailure { error ->
                    HollowEngine.LOGGER.error("Failed to compile UI script {}", script.name, error)
                }
            }.getOrElse { return@mapNotNull null }
            PreparedUiClientScript(script.name, code, context, bindings, program)
        }
        val registry = UiPreparedClientScripts.create(prepared)
        if (applyInputHints) registry.applyInputHints(root)
        return registry
    }

    private fun createBindings(context: UiScriptExecutionContext) = NarrativeBindings {
        install(AllStdLibModules())
        registerHostType(UiScriptEvent::class, "UiEvent")
        registerHostType(UiScriptGui::class, "Ui")
        registerHostType(UiScriptElement::class, "UiElement")
        registerHostType(UiScriptVariables::class, "UiVariables")
        registerUiScriptProperties()
        global("__hollow_ui_event", context.event)
        global("it", context.event)
        global("gui", context.gui)
        global("vars", context.variables)
        immediateFunction(
            name = "emit",
            valueParameters = listOf(CustomFunctionParameter("payload", "Any")),
        ) { arguments, _ ->
            context.sink.emit(arguments.singleOrNull().toCompoundTag())
            NullValue
        }
        immediateFunction("consume", receiverType = "UiEvent") { arguments, _ ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptEvent
            target.consume()
            NullValue
        }
        immediateFunction(
            name = "matches",
            valueParameters = listOf(CustomFunctionParameter("selector", "String")),
            receiverType = "UiEvent",
            returnType = "Boolean",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptEvent
            val selector = (arguments[1] as StringValue).value
            BooleanValue(target.matches(selector), context.symbolTable)
        }
        immediateFunction(
            name = "modify",
            valueParameters = listOf(
                CustomFunctionParameter("selector", "String"),
                CustomFunctionParameter("attribute", "String"),
                CustomFunctionParameter("value", "String"),
            ),
            receiverType = "Ui",
            returnType = "UiElement?",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptGui
            val selector = (arguments[1] as StringValue).value
            val attribute = (arguments[2] as StringValue).value
            val value = (arguments[3] as StringValue).value
            target.modify(selector, attribute, value)?.let {
                NarrativeHostValue("UiElement", it, context.symbolTable)
            } ?: NullValue
        }
        immediateFunction(
            name = "removeAttribute",
            valueParameters = listOf(
                CustomFunctionParameter("selector", "String"),
                CustomFunctionParameter("attribute", "String"),
            ),
            receiverType = "Ui",
            returnType = "UiElement?",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptGui
            val selector = (arguments[1] as StringValue).value
            val attribute = (arguments[2] as StringValue).value
            target.removeAttribute(selector, attribute)?.let {
                NarrativeHostValue("UiElement", it, context.symbolTable)
            } ?: NullValue
        }
        immediateFunction(
            name = "get",
            valueParameters = listOf(CustomFunctionParameter("key", "String")),
            receiverType = "UiVariables",
            returnType = "Any",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptVariables
            val key = (arguments[1] as StringValue).value
            target.value(key)?.toRuntimeValue(context.symbolTable) ?: NullValue
        }
        immediateFunction(
            name = "string",
            valueParameters = listOf(CustomFunctionParameter("key", "String")),
            receiverType = "UiVariables",
            returnType = "String",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptVariables
            val key = (arguments[1] as StringValue).value
            StringValue(target.string(key), context.symbolTable)
        }
        immediateFunction(
            name = "int",
            valueParameters = listOf(CustomFunctionParameter("key", "String")),
            receiverType = "UiVariables",
            returnType = "Int",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptVariables
            val key = (arguments[1] as StringValue).value
            IntValue(target.int(key), context.symbolTable)
        }
        immediateFunction(
            name = "double",
            valueParameters = listOf(CustomFunctionParameter("key", "String")),
            receiverType = "UiVariables",
            returnType = "Double",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptVariables
            val key = (arguments[1] as StringValue).value
            DoubleValue(target.double(key), context.symbolTable)
        }
        immediateFunction(
            name = "boolean",
            valueParameters = listOf(CustomFunctionParameter("key", "String")),
            receiverType = "UiVariables",
            returnType = "Boolean",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptVariables
            val key = (arguments[1] as StringValue).value
            BooleanValue(target.boolean(key), context.symbolTable)
        }
        immediateFunction(
            name = "attribute",
            valueParameters = listOf(CustomFunctionParameter("attribute", "String")),
            receiverType = "UiElement",
            returnType = "String",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptElement
            val attribute = (arguments[1] as StringValue).value
            StringValue(target.attribute(attribute), context.symbolTable)
        }
        immediateFunction(
            name = "modify",
            valueParameters = listOf(
                CustomFunctionParameter("attribute", "String"),
                CustomFunctionParameter("value", "String"),
            ),
            receiverType = "UiElement",
            returnType = "UiElement",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptElement
            val attribute = (arguments[1] as StringValue).value
            val value = (arguments[2] as StringValue).value
            NarrativeHostValue("UiElement", target.modify(attribute, value), context.symbolTable)
        }
        immediateFunction(
            name = "removeAttribute",
            valueParameters = listOf(CustomFunctionParameter("attribute", "String")),
            receiverType = "UiElement",
            returnType = "UiElement",
        ) { arguments, context ->
            val target = (arguments[0] as NarrativeHostValue).value as UiScriptElement
            val attribute = (arguments[1] as StringValue).value
            NarrativeHostValue("UiElement", target.removeAttribute(attribute), context.symbolTable)
        }
    }

    private fun prepareInline(script: UiClientScript.Inline): PreparedUiScript {
        val functionName = "__hollow_ui_inline_${script.kind.attributeName.replace("-", "_")}"
        return PreparedUiScript(
            code = """
                fun $functionName(it: UiEvent) {
                    ${script.source}
                }
                $functionName(__hollow_ui_event)
            """.trimIndent(),
            kinds = setOf(script.kind),
            handlers = listOf(HandlerDeclaration(script.kind, null, script.source, targetKey = script.targetKey)),
            targetKey = script.targetKey,
        )
    }

    private fun prepareResource(script: UiClientScript.Resource): PreparedUiScript {
        val declarations = extractHandlerDeclarations(script.source)
        if (declarations.isEmpty()) {
            return PreparedUiScript(script.source, UiEventKind.entries.toSet(), emptyList())
        }
        val generatedFunctions = declarations.mapIndexed { index, declaration ->
            val functionName = declaration.functionName(index)
            """
            fun $functionName(it: UiEvent) {
                ${declaration.body}
            }
            """.trimIndent()
        }
        val dispatch = declarations.mapIndexed { index, declaration ->
            val functionName = declaration.functionName(index)
            val selector = declaration.selector?.replace("\\", "\\\\")?.replace("\"", "\\\"").orEmpty()
            val selectorCheck = if (selector.isBlank()) "true" else "__hollow_ui_event.matches(\"$selector\")"
            """
            if (__hollow_ui_event.kind == "${declaration.kind.attributeName}" && $selectorCheck) {
                $functionName(__hollow_ui_event)
            }
            """.trimIndent()
        }
        return PreparedUiScript(
            code = buildString {
                appendLine(declarations.strippedSource)
                generatedFunctions.forEach { appendLine(it) }
                dispatch.forEach { appendLine(it) }
            },
            kinds = declarations.map { it.kind }.toSet(),
            handlers = declarations,
        )
    }

    private fun NarrativeBindingsBuilder.registerUiScriptProperties() {
        fun <T : Any?> eventProperty(name: String, type: String, getter: UiScriptEvent.() -> T) {
            registerKotliteExtensionProperty(
                ExtensionProperty(
                    declaredName = name,
                    receiver = "UiEvent",
                    type = type,
                    getter = { interpreter, receiver, _ ->
                        val event = KatariGeneratedBindingRuntime.asHost<UiScriptEvent>(receiver, "UiEvent", "$name receiver")
                        KatariGeneratedBindingRuntime.toRuntimeValue(event.getter(), null, interpreter.symbolTable())
                    },
                )
            )
        }

        fun <T : Any?> elementProperty(name: String, type: String, getter: UiScriptElement.() -> T) {
            registerKotliteExtensionProperty(
                ExtensionProperty(
                    declaredName = name,
                    receiver = "UiElement",
                    type = type,
                    getter = { interpreter, receiver, _ ->
                        val element = KatariGeneratedBindingRuntime.asHost<UiScriptElement>(receiver, "UiElement", "$name receiver")
                        KatariGeneratedBindingRuntime.toRuntimeValue(element.getter(), null, interpreter.symbolTable())
                    },
                )
            )
        }

        eventProperty("kind", "String") { kind }
        eventProperty("nodeKey", "String") { nodeKey }
        eventProperty("id", "String") { id }
        eventProperty("type", "String") { type }
        eventProperty("tags", "List<String>") { tags }
        eventProperty("mouseButton", "Int") { mouseButton }
        eventProperty("button", "Int") { button }
        eventProperty("x", "Double") { x }
        eventProperty("y", "Double") { y }
        eventProperty("localX", "Double") { localX }
        eventProperty("localY", "Double") { localY }
        eventProperty("deltaX", "Double") { deltaX }
        eventProperty("deltaY", "Double") { deltaY }
        eventProperty("scrollX", "Double") { scrollX }
        eventProperty("scrollY", "Double") { scrollY }
        eventProperty("key", "Int") { key }
        eventProperty("scanCode", "Int") { scanCode }
        eventProperty("modifiers", "Int") { modifiers }
        eventProperty("char", "String") { char }
        eventProperty("consumed", "Boolean") { consumed }

        elementProperty("id", "String") { id }
        elementProperty("type", "String") { type }
        elementProperty("tags", "List<String>") { tags }
        registerElementEnabledProperty("enabled")
        registerElementEnabledProperty("enable")
    }

    private fun NarrativeBindingsBuilder.registerElementEnabledProperty(name: String) {
        registerKotliteExtensionProperty(
            ExtensionProperty(
                declaredName = name,
                receiver = "UiElement",
                type = "Boolean",
                getter = { interpreter, receiver, _ ->
                    val element = KatariGeneratedBindingRuntime.asHost<UiScriptElement>(receiver, "UiElement", "$name receiver")
                    KatariGeneratedBindingRuntime.toRuntimeValue(element.enabled, null, interpreter.symbolTable())
                },
                setter = { _, receiver, value, _ ->
                    val element = KatariGeneratedBindingRuntime.asHost<UiScriptElement>(receiver, "UiElement", "$name receiver")
                    element.enabled = KatariGeneratedBindingRuntime.asBoolean(value, name)
                },
            )
        )
    }
}

class UiPreparedClientScripts private constructor(
    private val scripts: List<PreparedUiClientScript>,
) {
    fun dispatch(event: UiEvent, root: UiNode, variables: CompoundTag): Boolean {
        var handled = false
        scripts.asSequence()
            .filter { it.accepts(event) }
            .forEach { script ->
                if (!event.consumed) {
                    script.run(event, root, variables)
                    handled = true
                }
            }
        return handled
    }

    fun applyInputHints(root: UiNode) {
        val hints = scripts.flatMap { it.inputHints() }
        if (hints.isEmpty()) return
        root.walk { node ->
            val style = hints
                .asSequence()
                .filter { it.matches(node) }
                .map { it.kind.inputStyle() }
                .fold(UiInputStyle()) { current, input -> current.merge(input) }
            if (style != UiInputStyle()) node.modifiers += Modifier.input(
                hoverable = style.hoverable,
                clickable = style.clickable,
                focusable = style.focusable,
                draggable = style.draggable,
                scrollable = style.scrollable,
            )
        }
    }

    companion object {
        val Empty = UiPreparedClientScripts(emptyList())

        internal fun create(scripts: List<PreparedUiClientScript>) = UiPreparedClientScripts(scripts)
    }
}

internal class PreparedUiClientScript(
    private val name: String,
    private val script: PreparedUiScript,
    private val context: UiScriptExecutionContext,
    private val bindings: KatariBindings,
    private val program: KatariProgram,
) {
    fun handles(kind: UiEventKind): Boolean = script.handles(kind)

    fun accepts(event: UiEvent): Boolean {
        if (!handles(event.kind)) return false
        val targetKey = script.targetKey ?: return true
        return UiNodeKeys.key(event.node) == targetKey
    }

    fun inputHints(): List<HandlerDeclaration> = script.handlers.filter { it.kind.requiresNodeInput() }

    fun run(event: UiEvent, root: UiNode, variables: CompoundTag) {
        context.event.event = event
        context.gui.root = root
        context.variables.variables = variables
        val instance = KatariInstance(
            program = program,
            initialState = KatariState(
                programVersion = program.version,
                tasks = listOf(TaskState(id = program.entryTaskId)),
                globals = bindings.globals,
            ),
            executionEnvironment = bindings.executionEnvironment,
            snapshotCodec = bindings.snapshotCodec,
        )
        runCatching {
            runBlocking {
                instance.start()
                instance.join()
            }
        }.onFailure { error ->
            HollowEngine.LOGGER.error("Failed to execute UI script {}", name, error)
        }
    }
}

internal data class UiScriptExecutionContext(
    val event: UiScriptEvent,
    val gui: UiScriptGui,
    val variables: UiScriptVariables,
    val sink: UiEventSink,
)

class UiScriptEvent(internal var event: UiEvent) {
    val kind: String get() = event.kind.attributeName
    val nodeKey: String get() = UiNodeKeys.key(event.node)
    val id: String get() = event.node.id.orEmpty()
    val type: String get() = event.node.type
    val tags: List<String> get() = event.node.tags.toList()
    val mouseButton: Int get() = event.button
    val button: Int get() = event.button
    val x: Double get() = event.x.toDouble()
    val y: Double get() = event.y.toDouble()
    val localX: Double get() = event.localX.toDouble()
    val localY: Double get() = event.localY.toDouble()
    val deltaX: Double get() = event.deltaX.toDouble()
    val deltaY: Double get() = event.deltaY.toDouble()
    val scrollX: Double get() = event.scrollX.toDouble()
    val scrollY: Double get() = event.scrollY.toDouble()
    val key: Int get() = event.key
    val scanCode: Int get() = event.scanCode
    val modifiers: Int get() = event.modifiers
    val char: String get() = event.codePoint.takeIf { it > 0 }?.let { String(Character.toChars(it)) }.orEmpty()
    val consumed: Boolean get() = event.consumed

    fun consume() = event.consume()

    fun matches(selector: String): Boolean = event.matches(selector)
}

class UiScriptGui(internal var root: UiNode) {
    fun findNode(selector: String): UiScriptElement? = root.findNode(selector)?.let(::UiScriptElement)

    fun modify(selector: String, attribute: String, value: String): UiScriptElement? {
        val node = root.findNode(selector) ?: return null
        node.attributes[attribute] = value
        return UiScriptElement(node)
    }

    fun removeAttribute(selector: String, attribute: String): UiScriptElement? {
        val node = root.findNode(selector) ?: return null
        node.attributes.remove(attribute)
        return UiScriptElement(node)
    }
}

class UiScriptElement(private val node: UiNode) {
    val id: String get() = node.id.orEmpty()
    val type: String get() = node.type
    val tags: List<String> get() = node.tags.toList()
    fun attribute(name: String): String = node.attributes[name].orEmpty()

    fun modify(attribute: String, value: String): UiScriptElement {
        node.attributes[attribute] = value
        return this
    }

    fun removeAttribute(attribute: String): UiScriptElement {
        node.attributes.remove(attribute)
        return this
    }

    var enabled: Boolean
        get() = UiState.DISABLED !in node.states
        set(value) {
            if (value) node.states -= UiState.DISABLED else node.states += UiState.DISABLED
        }
}

class UiScriptVariables(internal var variables: CompoundTag) {
    fun value(key: String): Tag? = variables.get(key)

    fun string(key: String): String = variables.getString(key)

    fun int(key: String): Int = variables.getInt(key)

    fun double(key: String): Double = variables.getDouble(key)

    fun boolean(key: String): Boolean = variables.getBoolean(key)
}

internal data class PreparedUiScript(
    val code: String,
    val kinds: Set<UiEventKind>,
    val handlers: List<HandlerDeclaration>,
    val targetKey: String? = null,
) {
    fun handles(kind: UiEventKind): Boolean = kind in kinds
}

internal data class HandlerDeclaration(
    val kind: UiEventKind,
    val selector: String?,
    val body: String,
    val targetKey: String? = null,
)

private class HandlerDeclarations(
    private val values: List<HandlerDeclaration>,
    val strippedSource: String,
) : List<HandlerDeclaration> by values

private fun HandlerDeclaration.functionName(index: Int): String {
    return "__hollow_ui_${kind.attributeName.replace("-", "_")}_$index"
}

private fun HandlerDeclaration.matches(node: UiNode): Boolean {
    if (targetKey != null) return UiNodeKeys.key(node) == targetKey
    return selector?.let(node::matchesSelector) ?: true
}

private fun UiEventKind.requiresNodeInput(): Boolean {
    return when (this) {
        UiEventKind.ENTER,
        UiEventKind.EXIT,
        UiEventKind.HOVER,
        UiEventKind.PRESS,
        UiEventKind.CLICK,
        UiEventKind.RELEASE,
        UiEventKind.DRAG,
        UiEventKind.SCROLL,
        UiEventKind.CHAR_TYPED,
        UiEventKind.KEY_PRESSED,
        UiEventKind.FOCUS,
        UiEventKind.UNFOCUS -> true

        UiEventKind.INIT,
        UiEventKind.UPDATE,
        UiEventKind.CLOSE -> false
    }
}

private fun UiEventKind.inputStyle(): UiInputStyle {
    return when (this) {
        UiEventKind.ENTER,
        UiEventKind.EXIT,
        UiEventKind.HOVER -> UiInputStyle(hoverable = true)

        UiEventKind.PRESS,
        UiEventKind.CLICK,
        UiEventKind.RELEASE -> UiInputStyle(hoverable = true, clickable = true)

        UiEventKind.DRAG -> UiInputStyle(hoverable = true, draggable = true)

        UiEventKind.SCROLL -> UiInputStyle(hoverable = true, scrollable = true)

        UiEventKind.CHAR_TYPED,
        UiEventKind.KEY_PRESSED,
        UiEventKind.FOCUS,
        UiEventKind.UNFOCUS -> UiInputStyle(hoverable = true, focusable = true)

        UiEventKind.INIT,
        UiEventKind.UPDATE,
        UiEventKind.CLOSE -> UiInputStyle()
    }
}

private fun UiNode.walk(visitor: (UiNode) -> Unit) {
    val stack = ArrayDeque<UiNode>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        visitor(node)
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
}

private fun extractHandlerDeclarations(source: String): HandlerDeclarations {
    val declarations = mutableListOf<HandlerDeclaration>()
    val stripped = StringBuilder()
    var index = 0
    var depth = 0
    while (index < source.length) {
        val stringEnd = source.stringEndAt(index)
        if (stringEnd != null) {
            stripped.append(source, index, stringEnd)
            index = stringEnd
            continue
        }
        val char = source[index]
        if (char == '{') depth++
        if (char == '}') depth--
        val declaration = if (depth == 0) source.handlerAt(index) else null
        if (declaration == null) {
            stripped.append(char)
            index++
        } else {
            declarations += declaration.first
            repeat(declaration.second - index) { stripped.append('\n') }
            index = declaration.second
        }
    }
    return HandlerDeclarations(declarations, stripped.toString())
}

private fun String.handlerAt(start: Int): Pair<HandlerDeclaration, Int>? {
    if (start > 0 && this[start - 1].isIdentifierPart()) return null
    val name = UiEventKind.entries.firstNotNullOfOrNull { kind ->
        val eventName = "on" + kind.attributeName.split('-').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        eventName.takeIf { regionMatches(start, it, 0, it.length) }?.let { kind to it.length }
    } ?: return null
    val afterName = start + name.second
    if (afterName < length && this[afterName].isIdentifierPart()) return null
    var cursor = skipWhitespace(afterName)
    var selector: String? = null
    if (cursor < length && this[cursor] == '(') {
        val close = findMatching(cursor, '(', ')') ?: return null
        selector = substring(cursor + 1, close).trim().trim('"', '\'').takeIf { it.isNotBlank() }
        cursor = skipWhitespace(close + 1)
    }
    if (cursor >= length || this[cursor] != '{') return null
    val close = findMatching(cursor, '{', '}') ?: return null
    return HandlerDeclaration(name.first, selector, substring(cursor + 1, close)) to close + 1
}

private fun String.findMatching(start: Int, open: Char, close: Char): Int? {
    var depth = 0
    var index = start
    while (index < length) {
        val stringEnd = stringEndAt(index)
        if (stringEnd != null) {
            index = stringEnd
            continue
        }
        when (this[index]) {
            open -> depth++
            close -> {
                depth--
                if (depth == 0) return index
            }
        }
        index++
    }
    return null
}

private fun String.stringEndAt(start: Int): Int? {
    val quote = getOrNull(start)?.takeIf { it == '"' || it == '\'' } ?: return null
    var index = start + 1
    while (index < length) {
        if (this[index] == '\\') {
            index += 2
        } else if (this[index] == quote) {
            return index + 1
        } else {
            index++
        }
    }
    return length
}

private fun String.skipWhitespace(start: Int): Int {
    var index = start
    while (index < length && this[index].isWhitespace()) index++
    return index
}

private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'

private fun UiNode.findNode(selector: String): UiNode? {
    if (matchesSelector(selector)) return this
    return children.firstNotNullOfOrNull { it.findNode(selector) }
}

private fun UiNode.matchesSelector(selector: String): Boolean {
    val clean = selector.trim()
    if (clean.isBlank()) return true
    return runCatching { parseHssSelector(clean).matches(this) }.getOrDefault(false) || when {
        clean.startsWith("#") -> id == clean.removePrefix("#")
        clean.startsWith(".") -> clean.removePrefix(".") in tags
        clean.any { it in "[:#" } -> false
        else -> id == clean || type == clean || clean in tags
    }
}

private fun RuntimeValue?.toCompoundTag(): CompoundTag {
    val value = this ?: return CompoundTag()
    return when (value) {
        is StructValue -> value.toCompoundTag()
        is NarrativeHostValue -> value.value as? CompoundTag ?: CompoundTag()
        else -> CompoundTag().apply { put("value", value.toNbtTag()) }
    }
}

private fun StructValue.toCompoundTag(): CompoundTag {
    return CompoundTag().apply {
        fields.forEach { (key, value) -> put(key, value.toNbtTag()) }
    }
}

private fun RuntimeValue.toNbtTag(): Tag {
    return when (this) {
        is BooleanValue -> ByteTag.valueOf(value)
        is IntValue -> IntTag.valueOf(value)
        is LongValue -> LongTag.valueOf(value)
        is ShortValue -> ShortTag.valueOf(value)
        is DoubleValue -> DoubleTag.valueOf(value)
        is FloatValue -> FloatTag.valueOf(value)
        is CharValue -> StringTag.valueOf(value.toString())
        is StringValue -> StringTag.valueOf(value)
        is StructValue -> toCompoundTag()
        is StructArrayValue -> ListTag().also { list -> elements.forEach { list.add(it.toNbtTag()) } }
        is NarrativeHostValue -> (value as? CompoundTag)?.copy() ?: StringTag.valueOf(convertToString())
        NullValue -> CompoundTag()
        else -> StringTag.valueOf(convertToString())
    }
}

private fun Tag.toRuntimeValue(symbolTable: SymbolTable): RuntimeValue {
    return when (this) {
        is CompoundTag -> NarrativeHostValue("UiVariables", UiScriptVariables(this), symbolTable)
        is ByteTag -> IntValue(asByte.toInt(), symbolTable)
        is ShortTag -> IntValue(asShort.toInt(), symbolTable)
        is IntTag -> IntValue(asInt, symbolTable)
        is LongTag -> LongValue(asLong, symbolTable)
        is FloatTag -> DoubleValue(asFloat.toDouble(), symbolTable)
        is DoubleTag -> DoubleValue(asDouble, symbolTable)
        is StringTag -> StringValue(asString, symbolTable)
        is ListTag -> {
            val elements = map { it.toRuntimeValue(symbolTable) }
            val elementType = elements.firstOrNull()?.type() ?: symbolTable.AnyType
            ListValue(elements, elementType, symbolTable)
        }

        else -> StringValue(asString, symbolTable)
    }
}
