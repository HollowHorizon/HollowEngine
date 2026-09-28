package ru.hollowhorizon.hollowengine.client.shadergraph

/** The two stages of a compiled graph, ready for a shader program. */
class ShaderGraphSources(val vertex: String, val fragment: String)

/**
 * A shader a graph is compiled into: GLSL of the engine with marked places where the graph's code
 * goes, and the engine inputs it defines for the graph.
 */
class ShaderGraphTemplate(
    val name: String,
    private val vertexPath: String?,
    private val fragmentPath: String,
    val available: Set<ShaderInput>,
) {
    val vertexSource: String? by lazy { vertexPath?.let(::readTemplate) }
    val fragmentSource: String by lazy { readTemplate(fragmentPath) }
}

/**
 * The templates the engine has, and how a compiled graph is put into one.
 *
 * Markers in a template: `//#uniforms` for the properties, `//#functions` for what the nodes share,
 * `//#vertex` and `//#fragment` for each stage's code with the output node's inputs written to
 * `sg_out_*`, and `//#preview` for the preview program.
 */
object ShaderGraphTemplates {
    private const val ROOT = "/assets/hollowengine/shaders/graph/"

    /** Particle quads, drawn with the instance attributes of the engine particle program. */
    val PARTICLE = ShaderGraphTemplate(
        name = "particle",
        vertexPath = ROOT + "particle.vsh",
        fragmentPath = ROOT + "particle.fsh",
        available = ShaderInput.entries.toSet(),
    )

    /** The built-in meshes, drawn with the instance attributes of the engine mesh program. */
    val MESH = ShaderGraphTemplate(
        name = "mesh",
        vertexPath = ROOT + "mesh.vsh",
        fragmentPath = ROOT + "surface.fsh",
        available = ShaderInput.entries.toSet(),
    )

    /** Trails and beams, built on the CPU in the vanilla particle format. */
    val RIBBON = ShaderGraphTemplate(
        name = "ribbon",
        vertexPath = ROOT + "ribbon.vsh",
        fragmentPath = ROOT + "surface.fsh",
        available = ShaderInput.entries.toSet(),
    )

    /** The previews of the editor: a quad over a small target per node. */
    val PREVIEW = ShaderGraphTemplate(
        name = "preview",
        vertexPath = ROOT + "preview.vsh",
        fragmentPath = ROOT + "preview.fsh",
        available = ShaderInput.entries.toSet(),
    )

    /** [code] of a surface graph put into [template]. */
    fun surface(code: ShaderGraphCode, template: ShaderGraphTemplate): ShaderGraphSources {
        val vertexSource = requireNotNull(template.vertexSource) { "${template.name} has no vertex stage" }
        val vertex = buildString {
            appendLine(code.vertex.text)
            if (SurfaceOutputs.VERTEX_OFFSET in code.linkedOutputs) {
                append("sg_out_vertex_offset = ${code.outputs.getValue(SurfaceOutputs.VERTEX_OFFSET)};")
            }
        }
        val fragment = buildString {
            appendLine(code.fragment.text)
            appendLine("sg_out_color = ${code.outputs.getValue(SurfaceOutputs.COLOR)};")
            appendLine("sg_out_alpha = ${code.outputs.getValue(SurfaceOutputs.ALPHA)};")
            appendLine("sg_out_alpha_clip = ${code.outputs.getValue(SurfaceOutputs.ALPHA_CLIP)};")
            if (SurfaceOutputs.EMISSION in code.linkedOutputs) {
                appendLine("sg_out_emission = ${code.outputs.getValue(SurfaceOutputs.EMISSION)};")
                append("sg_has_emission = true;")
            }
        }
        return ShaderGraphSources(
            vertex = fill(vertexSource, code.properties, code.vertexLibraries, "//#vertex" to vertex),
            fragment = fill(template.fragmentSource, code.properties, code.fragmentLibraries, "//#fragment" to fragment),
        )
    }

    /** The fragment stage of the preview program of a graph. */
    fun preview(code: ShaderPreviewCode): String = fill(
        PREVIEW.fragmentSource,
        code.properties,
        code.libraries,
        "//#preview" to code.statements.text + "\n" + code.selection,
        extraUniforms = previewValues(code),
    )

    /** The vertex stage of the preview program of a graph: the offsets of the previews that move the mesh. */
    fun previewVertex(code: ShaderPreviewCode): String {
        val body = if (code.displaced.isEmpty()) "" else code.vertex.text + "\n" + code.vertexSelection
        return fill(
            requireNotNull(PREVIEW.vertexSource),
            code.properties,
            code.vertexLibraries,
            "//#vertex" to body,
            extraUniforms = previewValues(code),
        )
    }

    /** The array the values typed on nodes are uploaded into; both stages declare it alike, so they share it. */
    private fun previewValues(code: ShaderPreviewCode): String =
        if (code.values.isEmpty()) "" else "uniform vec4 PreviewValues[${code.values.size}];"

    /**
     * The line of [source], a preview program [preview] made out of [code], each statement of the graph
     * starts on, counted from 1 as GLSL compilers report them.
     */
    fun previewStatementLines(source: String, code: ShaderPreviewCode): Int? {
        val first = code.statements.statements.firstOrNull() ?: return null
        val at = source.indexOf("    $first").takeIf { it >= 0 } ?: return null
        return source.substring(0, at).count { it == '\n' } + 1
    }

    private fun fill(
        source: String,
        properties: List<ShaderGraphProperty>,
        libraries: List<ShaderLibrary>,
        body: Pair<String, String>,
        extraUniforms: String = "",
    ): String = source
        .replace(
            "//#uniforms",
            (properties.map { "uniform ${it.type.glsl} ${propertyUniform(it.name)};" } + extraUniforms)
                .filter { it.isNotEmpty() }
                .joinToString("\n"),
        )
        .replace("//#functions", libraries.joinToString("\n") { it.code.trimIndent() })
        .replace(body.first, body.second.prependIndent("    "))
}

private fun readTemplate(path: String): String =
    ShaderGraphTemplates::class.java.getResourceAsStream(path)?.use { it.reader().readText() }
        ?: error("The shader graph template is missing: $path")
