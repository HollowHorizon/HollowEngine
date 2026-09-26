package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.VertexBuffer
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ShaderInstance
import org.joml.Matrix4f
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL33
import org.lwjgl.system.MemoryStack
import ru.hollowhorizon.hollowengine.client.shadergraph.*
import ru.hollowhorizon.hollowengine.client.ui.render.withCullStatePreserved
import ru.hollowhorizon.hollowengine.client.ui.render.withOwnRenderTarget
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.concurrent.atomic.AtomicInteger

/**
 * What every node computes, drawn into a small texture per node.
 *
 * The whole graph is one program: a uniform picks whose value a draw shows, so an edit compiles once
 * however many nodes there are, and a value typed on a node is a uniform too, so changing it compiles
 * nothing. A node that reads the shape of what it is on, and the output node, are drawn on the mesh
 * the graph picked; the rest on a flat quad.
 */
internal class ShaderGraphPreviews {
    private class Frame(val graph: ShaderGraph, val revision: Int)

    @Volatile
    private var latest: Frame? = null

    /** Why the program did not build, for the editor to show; set on the render thread. */
    @Volatile
    var error: ShaderDiagnostic? = null
        private set

    private val name = "preview_${Counter.incrementAndGet()}"
    private val meshes = ShaderPreviewMeshes()
    private val started = System.nanoTime()
    private var program: ShaderInstance? = null
    private var programSource: String? = null
    private var code: ShaderPreviewCode? = null
    private var drawnRevision = -1
    private var animated = false
    private val targets = HashMap<String, TextureTarget>()

    /** Called from composition with the graph as it is now. */
    fun show(graph: ShaderGraph, revision: Int) {
        if (latest?.revision != revision) latest = Frame(graph, revision)
    }

    /** The texture of [node]'s preview, or 0 while there is none; read while the UI draws. */
    fun texture(node: String): Int = targets[node]?.colorTextureId ?: 0

    /** Draws what changed; runs on the render thread, inside a UI draw. */
    fun render() {
        val frame = latest ?: return
        val changed = frame.revision != drawnRevision
        if (!changed && !animated) return
        drawnRevision = frame.revision
        if (changed) rebuild(frame.graph)
        val shader = program ?: return
        val compiled = code ?: return

        val blend = GL33.glIsEnabled(GL33.GL_BLEND)
        val depth = GL33.glIsEnabled(GL33.GL_DEPTH_TEST)
        withOwnRenderTarget {
            withCullStatePreserved {
                RenderSystem.disableBlend()
                RenderSystem.disableDepthTest()
                val master = frame.graph.nodes.firstOrNull { ShaderNodeTypes.of(it.type)?.master != null }?.id
                compiled.previewIndex.forEach { (node, index) ->
                    val pixels = if (node == master) OUTPUT_PIXELS else PIXELS
                    val target = target(node, pixels)
                    target.setClearColor(0f, 0f, 0f, 0f)
                    target.clear(Minecraft.ON_OSX)
                    target.bindWrite(true)
                    val mesh = frame.graph.preview.mesh.takeIf { node in compiled.spatial } ?: ShaderPreviewMesh.QUAD
                    draw(shader, frame.graph, compiled, mesh, index, pixels)
                }
            }
        }
        if (depth) RenderSystem.enableDepthTest()
        if (blend) RenderSystem.enableBlend()
    }

    private fun target(node: String, pixels: Int): TextureTarget {
        val existing = targets[node]
        if (existing != null && existing.width == pixels) return existing
        existing?.destroyBuffers()
        return TextureTarget(pixels, pixels, false, Minecraft.ON_OSX).also { targets[node] = it }
    }

    private fun draw(
        shader: ShaderInstance,
        graph: ShaderGraph,
        compiled: ShaderPreviewCode,
        mesh: ShaderPreviewMesh,
        index: Int,
        pixels: Int,
    ) {
        val seconds = (System.nanoTime() - started) / 1_000_000_000f
        val flat = mesh == ShaderPreviewMesh.QUAD
        val (modelView, projection) = if (flat) Matrix4f() to Matrix4f() else camera(
            seconds,
            graph.preview.rotate,
            mesh
        )
        if (flat) RenderSystem.disableCull() else RenderSystem.enableCull()

        val buffer = meshes.of(mesh)
        buffer.bind()
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLES, modelView, projection, Minecraft.getInstance().window)
        shader.safeGetUniform("PreviewNode").set(index)
        shader.safeGetUniform("PreviewTime").set(seconds)
        shader.safeGetUniform("PreviewFlat").set(if (flat) 1f else 0f)
        shader.safeGetUniform("PreviewPixels").set(pixels.toFloat())
        shader.setSampler("Sampler0", textureAt(graph.preview.texture))
        graph.properties.forEach { property ->
            if (property.type == ShaderType.TEXTURE) {
                if (property.texture.isNotBlank()) shader.setSampler(
                    propertyUniform(property.name),
                    textureAt(property.texture)
                )
            } else {
                val values = FloatArray(property.type.width) {
                    property.default.getOrElse(it) {
                        property.default.lastOrNull() ?: 0f
                    }
                }
                shader.safeGetUniform(propertyUniform(property.name)).set(values)
            }
        }
        shader.apply()
        uploadValues(shader, graph, compiled.values)
        buffer.draw()
        shader.clear()
        VertexBuffer.unbind()
    }

    /** The values typed on the nodes, read from the graph as it is now; the program only knows their slots. */
    private fun uploadValues(shader: ShaderInstance, graph: ShaderGraph, slots: List<ShaderValueSlot>) {
        if (slots.isEmpty()) return
        val location = GL20.glGetUniformLocation(shader.id, "PreviewValues")
        if (location < 0) return
        MemoryStack.stackPush().use { stack ->
            val buffer = stack.mallocFloat(slots.size * 4)
            slots.forEach { slot ->
                val node = graph.node(slot.node)
                val pin = node?.let {
                    ShaderNodeTypes.of(it.type)?.codeInputs(it)?.firstOrNull { pin -> pin.name == slot.pin }
                }
                val values =
                    node?.values?.get(slot.pin)?.takeIf { it.isNotEmpty() } ?: pin?.default?.ifEmpty { null } ?: listOf(
                        0f
                    )
                repeat(4) { component -> buffer.put(values.getOrElse(component) { values.last() }) }
            }
            buffer.flip()
            GL20.glUniform4fv(location, buffer)
        }
    }

    /** A camera a little above the mesh, turning around it while [rotate] is on, with a cube made small enough to fit. */
    private fun camera(seconds: Float, rotate: Boolean, mesh: ShaderPreviewMesh): Pair<Matrix4f, Matrix4f> {
        val angle = if (rotate) seconds * TURN_SPEED else RESTING_ANGLE
        val size = if (mesh == ShaderPreviewMesh.CUBE) CUBE_SIZE else 1f
        val modelView = Matrix4f().translate(0f, 0f, -CAMERA_DISTANCE).rotateX(CAMERA_TILT).rotateY(angle).scale(size)
        val projection = Matrix4f().perspective(Math.toRadians(FIELD_OF_VIEW).toFloat(), 1f, 0.1f, 20f)
        return modelView to projection
    }

    private fun rebuild(graph: ShaderGraph) {
        val compiled = ShaderGraphCompiler.compilePreview(graph)
        val source = ShaderGraphTemplates.preview(compiled)
        code = compiled
        animated =
            ShaderInput.TIME.glsl in compiled.statements.text || graph.preview.rotate && graph.preview.mesh != ShaderPreviewMesh.QUAD && compiled.spatial.any { it in compiled.previewIndex }
        (targets.keys - compiled.previewIndex.keys).forEach { targets.remove(it)?.destroyBuffers() }
        if (source == programSource) return

        program?.close()
        program = null
        programSource = source
        val stage = "${name}_${Integer.toHexString(source.hashCode())}"
        val built = ShaderGraphPrograms.create(
            name = stage,
            json = ShaderGraphPrograms.json(
                vertex = ShaderGraphPrograms.stage(stage),
                fragment = ShaderGraphPrograms.stage(stage),
                attributes = listOf("Position", "Color", "UV0", "UV1", "UV2", "Normal"),
                samplers = listOf("Sampler0") + ShaderGraphPrograms.propertySamplers(graph.properties),
                uniforms = listOf(
                    ShaderGraphUniform("ModelViewMat", "matrix4x4", IDENTITY),
                    ShaderGraphUniform("ProjMat", "matrix4x4", IDENTITY),
                    ShaderGraphUniform("PreviewNode", "int", listOf(0f)),
                    ShaderGraphUniform("PreviewTime", "float", listOf(0f)),
                    ShaderGraphUniform("PreviewFlat", "float", listOf(1f)),
                    ShaderGraphUniform("PreviewPixels", "float", listOf(PIXELS.toFloat())),
                ) + ShaderGraphPrograms.propertyUniforms(graph.properties),
            ),
            stages = mapOf("vsh" to requireNotNull(ShaderGraphTemplates.PREVIEW.vertexSource), "fsh" to source),
            format = meshes.format,
        )
        program = built.getOrNull()
        error = built.exceptionOrNull()?.let { diagnose(it.message.orEmpty(), source, compiled) }
    }

    /**
     * What the driver said, pinned to the node whose statement it points at when it names a line of
     * the graph's code, which drivers write as `0(12)` or `0:12`.
     */
    private fun diagnose(message: String, source: String, compiled: ShaderPreviewCode): ShaderDiagnostic {
        val first = ShaderGraphTemplates.previewStatementLines(source, compiled)
        val line = ErrorLine.find(message)?.groupValues?.get(1)?.toIntOrNull()
        val node = if (first != null && line != null) compiled.statementNodes.getOrNull(line - first) else null
        val text =
            message.lineSequence().firstOrNull { "error" in it.lowercase() && ErrorLine.containsMatchIn(it) }?.trim()
                ?: message
        return ShaderDiagnostic(ShaderProblem.GLSL_ERROR, node, text.take(ERROR_LENGTH))
    }

    /** Frees the program, the meshes and the textures; GL objects, so on the render thread. */
    fun release() {
        program?.close()
        program = null
        programSource = null
        drawnRevision = -1
        meshes.release()
        targets.values.forEach(TextureTarget::destroyBuffers)
        targets.clear()
    }

    private fun textureAt(location: String): Int = Minecraft.getInstance().textureManager.getTexture(location.rl).id

    private companion object {
        const val PIXELS = 128
        const val OUTPUT_PIXELS = 192
        const val TURN_SPEED = 0.6f
        const val RESTING_ANGLE = 0.6f
        const val CAMERA_DISTANCE = 3.4f
        const val CAMERA_TILT = 0.35f
        const val CUBE_SIZE = 0.72f
        const val FIELD_OF_VIEW = 34.0
        const val ERROR_LENGTH = 400
        val IDENTITY = listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val ErrorLine = Regex("""\b0[(:](\d+)""")

        /** Programs are cached by name, so every editor gets names of its own. */
        val Counter = AtomicInteger()
    }
}
