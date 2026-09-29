package ru.hollowhorizon.hollowengine.client.shadergraph

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.api.extensions.ExtensionHandle
import ru.hollowhorizon.hollowengine.api.extensions.ExtensionPoints
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.utils.rl

/** How a preview shows the first output of a node. */
enum class ShaderPreviewStyle {
    /** As a color: a number is gray, a vector its components. */
    VALUE,

    /** A direction, whose components run from -1 to 1, shown `* 0.5 + 0.5`, as a normal map is. */
    SIGNED,

    /** An offset: the mesh moved by it, shaded, since a few hundredths of a block show as black. */
    DISPLACEMENT,
}

/** Where a kind of node shows up in the add menu. */
enum class ShaderNodeCategory {
    INPUT, MATH, VECTOR, NORMAL, UV, TEXTURE, PROCEDURAL, OUTPUT,
}

/** An input of a kind of node: what it takes, and what it is when nothing is linked to it. */
class ShaderPinSpec(
    val name: String,
    val type: ShaderPinType,
    val default: List<Float>,
    val fallback: ShaderInput? = null,
    val shownFor: ((ShaderGraphNode) -> Boolean)? = null,
    val color: Boolean = false,
)

/**
 * What the type of an output can be worked out from: the node as placed, in its graph, and the types
 * its inputs resolved to.
 */
class ShaderTypeContext internal constructor(
    val graph: ShaderGraph,
    val node: ShaderGraphNode,
    private val inputs: Map<String, ShaderType>,
) {
    fun input(name: String): ShaderType? = inputs[name]
}

/**
 * An output of a kind of node. Its type is [type], or what [typeOf] says for the node as placed,
 * which is how a property node takes the type of its property; [typeOf] giving null means the type
 * cannot be worked out.
 */
class ShaderOutputSpec(
    val name: String,
    val type: ShaderPinType,
    val typeOf: (ShaderTypeContext.() -> ShaderPinType?)?,
    val expression: ShaderEmitContext.() -> String,
)

/** How a choice a node offers is edited. */
enum class ShaderOptionKind {
    /** One of [ShaderOptionSpec.values]. */
    CHOICE,

    /** On or off, stored as `true` and `false`. */
    TOGGLE,

    /** Free text. */
    TEXT,

    /** GLSL, as the expression node takes it. */
    EXPRESSION,

    /** One of the properties of the graph. */
    PROPERTY,
}

/** A choice a node offers besides its inputs. */
class ShaderOptionSpec(val name: String, val kind: ShaderOptionKind, val values: List<String>, val default: String) {
    val titleKey: String get() = "hollowengine.gui.shadergraph.option.$name"

    fun valueKey(value: String): String = "hollowengine.gui.shadergraph.option.$name.$value"
}

/** What makes a kind of node the output of a graph: which target, and which of its inputs feed the vertex stage. */
class ShaderMasterSpec(val target: ShaderTarget, val vertexInputs: Set<String>)

/**
 * One kind of node: its pins and options, and the GLSL each output is. Kinds are made with
 * [shaderNode] and live in [ShaderNodeTypes], so an addon can add its own.
 */
class ShaderNodeType(
    val id: String,
    val category: ShaderNodeCategory,
    val group: String,
    private val declaredInputs: List<ShaderPinSpec>,
    private val pinsOf: ((ShaderGraphNode) -> List<ShaderPinSpec>)?,
    val outputs: List<ShaderOutputSpec>,
    val options: List<ShaderOptionSpec>,
    val reads: Set<ShaderInput>,
    val libraries: Set<ShaderLibrary>,
    val fragmentOnly: Boolean,
    val master: ShaderMasterSpec?,
    val previewByDefault: Boolean,
    val previewStyle: ShaderPreviewStyle,
    private val check: ((ShaderGraphNode) -> ShaderDiagnostic?)?,
    /** The icon in the title bar and the add menu, and the one of the category when a kind has none. */
    val icon: String? = null,
) {
    val titleKey: String get() = "hollowengine.gui.shadergraph.node.${id.substringAfter(':').replace('/', '.')}"

    val prototype: ShaderGraphNode get() = ShaderGraphNode("", id)

    fun inputs(node: ShaderGraphNode): List<ShaderPinSpec> =
        pinsOf?.invoke(node) ?: declaredInputs.filter { it.shownFor?.invoke(node) != false }

    fun codeInputs(node: ShaderGraphNode): List<ShaderPinSpec> = pinsOf?.invoke(node) ?: declaredInputs

    fun input(node: ShaderGraphNode, name: String): ShaderPinSpec? = inputs(node).firstOrNull { it.name == name }

    fun output(name: String): ShaderOutputSpec? = outputs.firstOrNull { it.name == name }
    fun option(name: String): ShaderOptionSpec? = options.firstOrNull { it.name == name }

    fun problem(node: ShaderGraphNode): ShaderDiagnostic? = check?.invoke(node)

    fun showsPreview(node: ShaderGraphNode): Boolean =
        (outputs.isNotEmpty() || master != null) && (node.preview ?: previewByDefault)
}

/**
 * What an output expression is written against: the GLSL of each input, already of the type the pin
 * resolved to, the node's options, and the variables of the node's earlier outputs.
 */
class ShaderEmitContext internal constructor(
    val graph: ShaderGraph,
    val node: ShaderGraphNode,
    val dynamic: ShaderType,
    val outputType: ShaderType,
    val vertex: Boolean,
    private val inputCode: Map<String, String>,
    private val outputVariables: Map<String, String>,
    private val kind: ShaderNodeType,
) {
    /** The GLSL of this input. */
    val ShaderPinSpec.code: String get() = inputCode.getValue(name)

    /** The GLSL of the input named [name]. */
    fun input(name: String): String = inputCode.getValue(name)

    /** Whether something is linked into this input, rather than it taking its value or its fallback. */
    val ShaderPinSpec.linked: Boolean get() = graph.linkInto(node.id, name) != null && kind.inputs(node).any { it.name == name }

    /** The variable an earlier output of this node was written to. */
    fun output(name: String): String = outputVariables.getValue(name)

    fun option(name: String): String = node.options[name] ?: kind.option(name)?.default.orEmpty()

    fun toggle(name: String): Boolean = option(name) == "true"
}

/** Builds a kind of node; see [shaderNode]. */
class ShaderNodeBuilder internal constructor(private val id: String, private val category: ShaderNodeCategory) {
    private val inputs = ArrayList<ShaderPinSpec>()
    private val outputs = ArrayList<ShaderOutputSpec>()
    private val options = ArrayList<ShaderOptionSpec>()
    private val reads = LinkedHashSet<ShaderInput>()
    private val libraries = LinkedHashSet<ShaderLibrary>()
    private var group = ""
    private var pinsOf: ((ShaderGraphNode) -> List<ShaderPinSpec>)? = null
    private var check: ((ShaderGraphNode) -> ShaderDiagnostic?)? = null
    private var fragmentOnly = false
    private var preview = true
    private var previewStyle = ShaderPreviewStyle.VALUE
    private var master: ShaderMasterSpec? = null
    private var icon: String? = null

    fun input(
        name: String,
        vararg default: Float,
        type: ShaderPinType = ShaderPinType.DYNAMIC,
        fallback: ShaderInput? = null,
        shownFor: ((ShaderGraphNode) -> Boolean)? = null,
        color: Boolean = false,
    ): ShaderPinSpec = ShaderPinSpec(name, type, default.toList(), fallback, shownFor, color).also { inputs += it }

    /** Works the inputs out of the node itself, in place of the ones declared with [input]. */
    fun pins(of: (ShaderGraphNode) -> List<ShaderPinSpec>) {
        pinsOf = of
    }

    fun output(name: String, type: ShaderPinType = ShaderPinType.DYNAMIC, expression: ShaderEmitContext.() -> String) {
        outputs += ShaderOutputSpec(name, type, null, expression)
    }

    /** An output whose type depends on how the node is set up rather than on its inputs. */
    fun output(
        name: String,
        typeOf: ShaderTypeContext.() -> ShaderPinType?,
        expression: ShaderEmitContext.() -> String,
    ) {
        outputs += ShaderOutputSpec(name, ShaderPinType.DYNAMIC, typeOf, expression)
    }

    fun option(name: String, vararg values: String, default: String = values.firstOrNull().orEmpty()) {
        options += ShaderOptionSpec(name, ShaderOptionKind.CHOICE, values.toList(), default)
    }

    fun toggle(name: String, default: Boolean = false) {
        options += ShaderOptionSpec(name, ShaderOptionKind.TOGGLE, listOf("false", "true"), default.toString())
    }

    fun text(name: String, default: String = "") {
        options += ShaderOptionSpec(name, ShaderOptionKind.TEXT, emptyList(), default)
    }

    fun expression(name: String, default: String) {
        options += ShaderOptionSpec(name, ShaderOptionKind.EXPRESSION, emptyList(), default)
    }

    fun property(name: String) {
        options += ShaderOptionSpec(name, ShaderOptionKind.PROPERTY, emptyList(), "")
    }

    /** Reports what is wrong with a node as it is set up; see [ShaderNodeType.problem]. */
    fun check(problem: (ShaderGraphNode) -> ShaderDiagnostic?) {
        check = problem
    }

    fun group(name: String) {
        group = name
    }

    /** An SVG for the kind, as a resource location; see [graphIcon] for the ones the engine ships. */
    fun icon(location: String) {
        icon = location
    }

    /** New nodes of this kind start with their preview off. */
    fun noPreview() {
        preview = false
    }

    /** How the preview shows what the node computes, when a plain color says little. */
    fun preview(style: ShaderPreviewStyle) {
        previewStyle = style
    }

    fun reads(vararg inputs: ShaderInput) {
        reads += inputs
    }

    fun uses(vararg library: ShaderLibrary) {
        libraries += library
    }

    fun fragmentOnly() {
        fragmentOnly = true
    }

    fun master(target: ShaderTarget, vararg vertexInputs: String) {
        master = ShaderMasterSpec(target, vertexInputs.toSet())
    }

    internal fun build() = ShaderNodeType(
        id = id,
        category = category,
        group = group,
        declaredInputs = inputs.toList(),
        pinsOf = pinsOf,
        outputs = outputs.toList(),
        options = options.toList(),
        reads = reads.toSet(),
        libraries = libraries.toSet(),
        fragmentOnly = fragmentOnly,
        master = master,
        previewByDefault = preview,
        previewStyle = previewStyle,
        check = check,
        icon = icon,
    )
}

/** The icon of the engine's graph icon set called [name], such as `time` or `noise`. */
fun graphIcon(name: String): String = "hollowengine:textures/gui/icons/graph/$name.svg"

/**
 * A kind of node, declared the way it reads:
 *
 * ```
 * shaderNode("hollowengine:math/multiply", ShaderNodeCategory.MATH) {
 *     val a = input("A", 1f)
 *     val b = input("B", 1f)
 *     output("Out") { "${a.code} * ${b.code}" }
 * }
 * ```
 */
fun shaderNode(id: String, category: ShaderNodeCategory, build: ShaderNodeBuilder.() -> Unit): ShaderNodeType =
    ShaderNodeBuilder(id, category).apply(build).build()

/**
 * Every kind of node a graph can use: the engine's, the ones addons register once, and the ones
 * [RegisterShaderNodesEvent] brings on every resource reload.
 */
object ShaderNodeTypes {
    val point = ExtensionPoints.create<ShaderNodeType>("hollowengine:shadergraph/node_types".rl)

    /** What the last [RegisterShaderNodesEvent] registered, taken back on the next. */
    private var reloaded: List<ExtensionHandle> = emptyList()

    /** Bumped on every reload, so an open editor compiles its graph again against the kinds there are now. */
    var revision by mutableStateOf(0)
        private set

    init {
        ShaderNodeLibrary.all.forEach(::register)
    }

    fun register(type: ShaderNodeType): ExtensionHandle = point.register(type.id.rl, type)

    /** Every kind, in the order they were registered, which is the order the add menu lists them in. */
    val all: List<ShaderNodeType> get() = point.extensions

    fun of(id: String): ShaderNodeType? = all.firstOrNull { it.id == id }

    /** The output node kind of [target]. */
    fun master(target: ShaderTarget): ShaderNodeType? = all.firstOrNull { it.master?.target == target }

    /**
     * Takes back what the previous reload registered and asks again. The engine's kinds go in first,
     * so one a script replaced last time is back if the script no longer replaces it.
     */
    fun reload() {
        reloaded.forEach(ExtensionHandle::close)
        ShaderNodeLibrary.all.forEach(::register)
        val event = RegisterShaderNodesEvent.post(RegisterShaderNodesEvent())
        reloaded = event.types.map(::register)
        revision++
    }
}

/**
 * Fires on every resource reload of the client: where a `reload.kts` or an addon adds kinds of node
 * to the shader graph. What it registered on the previous reload is taken back first, so a script
 * that stops registering a kind loses it, and one that registers the id of an engine kind replaces it.
 *
 * ```
 * @SubscribeEvent
 * fun nodes(event: RegisterShaderNodesEvent) {
 *     event.register("mymod:math/double", ShaderNodeCategory.MATH) {
 *         val value = input("In", 1f)
 *         output("Out") { "${value.code} * 2.0" }
 *     }
 * }
 * ```
 */
class RegisterShaderNodesEvent : ClientEvent {
    internal val types = ArrayList<ShaderNodeType>()

    fun register(type: ShaderNodeType) {
        types += type
    }

    fun register(id: String, category: ShaderNodeCategory, build: ShaderNodeBuilder.() -> Unit) =
        register(shaderNode(id, category, build))

    companion object : EventHandler<RegisterShaderNodesEvent>()
}

/** Asks for the kinds of node again whenever the client reloads its resources. */
object ShaderNodeReloadListener : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) = ShaderNodeTypes.reload()
}
