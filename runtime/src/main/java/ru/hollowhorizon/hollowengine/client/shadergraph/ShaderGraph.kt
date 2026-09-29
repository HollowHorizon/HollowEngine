package ru.hollowhorizon.hollowengine.client.shadergraph

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What a graph shades, which decides its output node and the engine [inputs] it can read. */
@Serializable
enum class ShaderTarget(val inputs: Set<ShaderInput>) {
    /** The surface of a particle, a mesh or a ribbon of an effect. */
    SURFACE(ShaderInput.entries.toSet()),

    POST(
        setOf(
            ShaderInput.UV, ShaderInput.TEXTURE_UV, ShaderInput.FRAME_UV, ShaderInput.SCREEN_UV, ShaderInput.TIME,
            ShaderInput.POSITION, ShaderInput.VIEW_DIRECTION, ShaderInput.MAIN_TEXTURE, ShaderInput.SCENE_DEPTH,
            ShaderInput.FRAGMENT_DEPTH, ShaderInput.SCENE_COLOR,
        )
    ),
}

/**
 * One node of a graph: its kind, where it sits on the canvas, the values its unconnected inputs take
 * and the choices it offers, such as which property a property node reads.
 */
@Serializable
data class ShaderGraphNode(
    val id: String,
    val type: String,
    val x: Float = 0f,
    val y: Float = 0f,
    val values: Map<String, List<Float>> = emptyMap(),
    val options: Map<String, String> = emptyMap(),
    val preview: Boolean? = null,
    val collapsed: Boolean = false,
)

/** What the previews of the editor draw a graph on. */
@Serializable
enum class ShaderPreviewMesh {
    QUAD, SPHERE, CUBE,
}

/**
 * How the editor previews a graph. It is kept with the graph so it opens the way it was left, but
 * nothing outside the editor reads it.
 */
@Serializable
data class ShaderGraphPreview(
    val mesh: ShaderPreviewMesh = ShaderPreviewMesh.SPHERE,
    val rotate: Boolean = true,
    val texture: String = DEFAULT_TEXTURE,
    val scene: String = DEFAULT_SCENE,
) {
    companion object {
        const val DEFAULT_TEXTURE = "hollowengine:textures/particle/circle.png"
        const val DEFAULT_SCENE = "hollowengine:textures/gui/preview/sampler_preview.png"

        const val SCREEN_ASPECT = 0.65f
    }
}

/** A link from an output of one node to an input of another. */
@Serializable
data class ShaderGraphLink(
    val from: String,
    val output: String,
    val to: String,
    val input: String,
)

/**
 * A value the graph leaves to whoever uses it: each material that names the graph sets its own. It
 * reaches the shader as a uniform, or a sampler for a texture, under [name].
 */
@Serializable
data class ShaderGraphProperty(
    val name: String,
    val type: ShaderType = ShaderType.FLOAT,
    val default: List<Float> = emptyList(),
    val texture: String = "",
)

/**
 * A shader as a graph: one file for both the vertex and the fragment stage. The compiler splits it
 * by what each output of the output node needs. It is stored as a `.material`, since that is what an
 * effect names: a surface with its properties.
 */
@Serializable
data class ShaderGraph(
    val version: Int = CURRENT_VERSION,
    val target: ShaderTarget = ShaderTarget.SURFACE,
    val nodes: List<ShaderGraphNode> = emptyList(),
    val links: List<ShaderGraphLink> = emptyList(),
    val properties: List<ShaderGraphProperty> = emptyList(),
    val preview: ShaderGraphPreview = ShaderGraphPreview(),
) {
    fun node(id: String): ShaderGraphNode? = nodes.firstOrNull { it.id == id }

    fun linkInto(node: String, input: String): ShaderGraphLink? = links.lastOrNull { it.to == node && it.input == input }

    fun property(name: String): ShaderGraphProperty? = properties.firstOrNull { it.name == name }

    companion object {
        const val CURRENT_VERSION = 1
        const val EXTENSION = ".material"
    }
}

/** How a graph is stored: JSON, with what equals the defaults left out. */
object ShaderGraphFormat {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    fun read(text: String): ShaderGraph =
        if (text.isBlank()) ShaderGraph() else json.decodeFromString(ShaderGraph.serializer(), text)

    fun write(graph: ShaderGraph): String = json.encodeToString(ShaderGraph.serializer(), graph)
}
