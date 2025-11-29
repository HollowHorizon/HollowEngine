package ru.hollowhorizon.hollowengine.client.models.internal

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import de.fabmax.kool.math.*
import de.fabmax.kool.util.Color
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import ru.hollowhorizon.hollowengine.client.models.gltf.GltfMesh
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.client.utils.areShadersEnabled
import ru.hollowhorizon.hollowengine.client.utils.hasShaders
import ru.hollowhorizon.hollowengine.client.utils.math.MikkTSpaceContext
import ru.hollowhorizon.hollowengine.client.utils.math.MikktspaceTangentGenerator
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix3f
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix4f
import ru.hollowhorizon.hollowengine.client.utils.toTexture
import java.nio.FloatBuffer

class Primitive(
    private var positions: Array<Vec3f>? = null,
    private var normals: Array<Vec3f>? = null,
    private var texCoords: Array<Vec2f>? = null,
    private var midCoords: Array<Vec2f>? = null,
    private var tangents: Array<Vec4f>? = null,
    private var joints: Array<Vec4i>? = null,
    private var jointWeights: Array<Vec4f>? = null,
    private val indices: IntArray? = null,
    private val material: Material,
    private val morphTargets: List<Map<String, FloatArray>> = listOf(),
    private var weights: FloatArray = floatArrayOf(),
) {
    val hasSkinning = joints != null && jointWeights != null
    private val indexCount = indices?.size ?: 0
    private val positionsCount = (positions?.size ?: 0) * 3
    var jointCount = 0
    private val morphCommands = ArrayList<(FloatArray) -> Unit>()

    // Порог для переключения между режимами рендеринга
    val useBatching = false // positionsCount < 512 && !hasSkinning && morphTargets.isEmpty()

    private var vao = -1
    private var skinningVao = -1

    private var vertexBuffer = -1
    private var normalBuffer = -1
    private var tangentBuffer = -1
    private var texCoordsBuffer = -1
    private var midCoordsBuffer = -1
    private var indexBuffer = -1

    private var glTexture = -1
    private var jointBuffer = -1
    private var weightsBuffer = -1
    private var skinVertexBuffer = -1
    private var skinNormalBuffer = -1
    private var jointMatrixBuffer = -1

    // Кэш для RenderType
    private var cachedRenderType: RenderType? = null

    fun setWeights(values: FloatArray) {
        if (values.isEmpty()) return
        weights = values
    }

    fun setupPipeline(pipeline: RenderPipeline, skinGetter: SkinGetter, matrixGetter: MatrixGetter) {
        if (useBatching) {
            initBatching(pipeline, matrixGetter, positions ?: return, texCoords ?: return, normals ?: return)
        } else {
            initVAO(pipeline, matrixGetter)
            initSkinning(pipeline, skinGetter)
        }
    }

    fun init() {
        if (useBatching) return
        val currentVAO = GL33.glGetInteger(GL33.GL_VERTEX_ARRAY_BINDING)
        val currentArrayBuffer = GL33.glGetInteger(GL33.GL_ARRAY_BUFFER_BINDING)
        val currentElementArrayBuffer = GL33.glGetInteger(GL33.GL_ELEMENT_ARRAY_BUFFER_BINDING)

        if (hasSkinning) initTransformFeedback()
        initBuffers()

        GL33.glBindVertexArray(currentVAO)
        GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, currentArrayBuffer)
        GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer)

        releaseCpu()
    }

    private fun initBuffers() {
        vao = GL33.glGenVertexArrays()
        GL33.glBindVertexArray(vao)

        if (skinningVao == -1) {
            positions?.let { positions ->
                val buffer = BufferUtils.createFloatBuffer(positions.size * 3)
                positions.forEach { buffer.put(it.x).put(it.y).put(it.z) }
                buffer.flip()

                morphCommands += { array ->
                    for (i in 0 until positions.size * 3) {
                        var value = positions[i / 3].get(i % 3)
                        array.forEachIndexed { j, shapeKey ->
                            morphTargets[j][GltfMesh.Primitive.ATTRIBUTE_POSITION]?.let {
                                value += it[i] * shapeKey
                            }
                        }
                        buffer.put(i, value)
                    }
                    GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vertexBuffer)
                    GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
                }

                vertexBuffer = GL33.glGenBuffers()
                GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vertexBuffer)
                GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
                GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(0)
            }

            normals?.let { normals ->
                val buffer = BufferUtils.createFloatBuffer(normals.size * 3)
                for (n in normals) buffer.put(n.x).put(n.y).put(n.z)
                buffer.flip()

                morphCommands += { array ->
                    for (i in 0 until normals.size * 3) {
                        var value = normals[i / 3].get(i % 3)
                        array.forEachIndexed { j, percent ->
                            morphTargets[j][GltfMesh.Primitive.ATTRIBUTE_NORMAL]?.let {
                                value += it[i] * percent
                            }
                        }
                        buffer.put(i, value)
                    }
                    GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, normalBuffer)
                    GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
                }

                normalBuffer = GL33.glGenBuffers()
                GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, normalBuffer)
                GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
                GL33.glVertexAttribPointer(5, 3, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(5)

                if (tangents == null) positions?.let { positions ->
                    val tangents = BufferUtils.createFloatBuffer(normals.size * 4)
                    MikktspaceTangentGenerator.genTangSpaceDefault(object : MikkTSpaceContext {
                        override fun getNumFaces(): Int = positionsCount / 9
                        override fun getNumVerticesOfFace(face: Int): Int = 3
                        override fun getPosition(posOut: FloatArray, face: Int, vert: Int) {
                            val index = (face * 3) + vert
                            posOut[0] = positions[index].x
                            posOut[1] = positions[index].y
                            posOut[2] = positions[index].z
                        }

                        override fun getNormal(normOut: FloatArray, face: Int, vert: Int) {
                            val index = (face * 3) + vert
                            normOut[0] = normals[index].x
                            normOut[1] = normals[index].y
                            normOut[2] = normals[index].z
                        }

                        override fun getTexCoord(texOut: FloatArray, face: Int, vert: Int) {
                            val index = (face * 3) + vert
                            texOut[0] = texCoords?.get(index)?.x ?: 0f
                            texOut[1] = texCoords?.get(index)?.y ?: 0f
                        }

                        override fun setTSpaceBasic(tangent: FloatArray, sign: Float, face: Int, vert: Int) {
                            tangents.put(tangent[0]).put(tangent[1]).put(tangent[2]).put(-sign)
                        }

                        override fun setTSpace(
                            tangent: FloatArray?,
                            biTangent: FloatArray?,
                            magS: Float,
                            magT: Float,
                            isOrientationPreserving: Boolean,
                            face: Int,
                            vert: Int,
                        ) {
                        }
                    })
                    tangents.flip()
                    tangentBuffer = GL33.glGenBuffers()
                    GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, tangentBuffer)
                    GL33.glBufferData(GL33.GL_ARRAY_BUFFER, tangents, GL33.GL_STATIC_DRAW)
                    GL33.glVertexAttribPointer(9, 4, GL33.GL_FLOAT, false, 0, 0)
                    GL33.glEnableVertexAttribArray(9)
                }
            }

            tangents?.let { tangents ->
                val buffer = BufferUtils.createFloatBuffer(tangents.size * 4)
                for (t in tangents) buffer.put(t.x).put(t.y).put(t.z).put(1f)
                buffer.flip()
                morphCommands += { array ->
                    for (i in 0 until tangents.size * 3) {
                        var value = tangents[i / 3].get(i % 3)
                        array.forEachIndexed { j, percent ->
                            morphTargets[j][GltfMesh.Primitive.ATTRIBUTE_TANGENT]?.let {
                                value += it[i] * percent
                            }
                        }
                        buffer.put(i, value)
                    }
                    GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, tangentBuffer)
                    GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
                }
                tangentBuffer = GL33.glGenBuffers()
                GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, tangentBuffer)
                GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
                GL33.glVertexAttribPointer(9, 4, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(9)
            }
        } else {
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, vertexBuffer)
            GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(0)
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, normalBuffer)
            GL33.glVertexAttribPointer(5, 3, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(5)
        }

        texCoords?.let { texCoords ->
            val buffer = BufferUtils.createFloatBuffer(texCoords.size * 2)
            for (t in texCoords) buffer.put(t.x).put(t.y)
            buffer.flip()
            texCoordsBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, texCoordsBuffer)
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
            GL33.glVertexAttribPointer(2, 2, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(2)
            if (midCoords == null) {
                GL33.glVertexAttribPointer(8, 2, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(8)
            }
        }

        midCoords?.let { midCoords ->
            val buffer = BufferUtils.createFloatBuffer(midCoords.size * 2)
            for (t in midCoords) buffer.put(t.x).put(t.y)
            buffer.flip()
            midCoordsBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, midCoordsBuffer)
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
            GL33.glVertexAttribPointer(8, 2, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(8)
        }

        GL33.glDisableVertexAttribArray(1)

        if (indices != null) {
            val buffer = BufferUtils.createIntBuffer(indexCount)
            for (n in indices) buffer.put(n)
            buffer.flip()
            indexBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
            GL33.glBufferData(GL33.GL_ELEMENT_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
        }
    }

    private fun initTransformFeedback() {
        skinningVao = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(skinningVao)

        var posSize = -1L
        var norSize = -1L

        joints?.let { joints ->
            val jointBuffer = BufferUtils.createIntBuffer(joints.size * 4)
            for (n in joints) jointBuffer.put(n.x).put(n.y).put(n.z).put(n.w)
            jointBuffer.flip()
            this.jointBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, this.jointBuffer)
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, jointBuffer, GL33.GL_STATIC_DRAW)
            GL33.glVertexAttribPointer(0, 4, GL33.GL_INT, false, 0, 0)
            GL33.glEnableVertexAttribArray(0)
        }

        jointWeights?.let { weights ->
            val weightsBuffer = BufferUtils.createFloatBuffer(weights.size * 4)
            for (n in weights) weightsBuffer.put(n.x).put(n.y).put(n.z).put(n.w)
            weightsBuffer.flip()
            this.weightsBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, this.weightsBuffer)
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, weightsBuffer, GL33.GL_STATIC_DRAW)
            GL33.glVertexAttribPointer(1, 4, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(1)
        }

        positions?.let { positions ->
            posSize = positions.size * 12L
            val buffer = BufferUtils.createFloatBuffer(positions.size * 3)
            for (n in positions) buffer.put(n.x).put(n.y).put(n.z)
            buffer.flip()
            skinVertexBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, skinVertexBuffer)
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
            GL33.glVertexAttribPointer(2, 3, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(2)
        }

        normals?.let { normals ->
            norSize = normals.size * 12L
            val buffer = BufferUtils.createFloatBuffer(normals.size * 3)
            for (n in normals) buffer.put(n.x).put(n.y).put(n.z)
            buffer.flip()
            skinNormalBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, skinNormalBuffer)
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
            GL33.glVertexAttribPointer(3, 3, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(3)
        }

        vertexBuffer = GL33.glGenBuffers()
        GL33.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, vertexBuffer)
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, posSize, GL33.GL_STATIC_DRAW)

        normalBuffer = GL33.glGenBuffers()
        GL33.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, normalBuffer)
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, norSize, GL33.GL_STATIC_DRAW)

        jointMatrixBuffer = GL15.glGenBuffers()
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, jointMatrixBuffer)
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, jointCount * 64L, GL15.GL_STATIC_DRAW)
        glTexture = GL11.glGenTextures()
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, glTexture)
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, jointMatrixBuffer)
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, 0)

        GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0)
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0)
    }

    private fun releaseCpu() {
        positions = null
        normals = null
        texCoords = null
        midCoords = null
        tangents = null
        joints = null
        jointWeights = null
        cachedRenderType = null
    }


    private fun initVAO(
        pipeline: RenderPipeline,
        node: MatrixGetter,
    ) {
        if (useBatching) return
        if (morphTargets.isNotEmpty()) {
            pipeline.addVAORenderable {
                updateMorphTargets()
            }
        }
        pipeline.addVAORenderable {
            renderVAO(node, stack)
        }
    }

    fun renderVAO(
        node: MatrixGetter,
        stack: PoseStack,
    ) {
        val matrix = node()
        val shader = SHADER
        val (normal, specular) = applyMaterial(shader, material)

        RenderSystem.glBindVertexArray(::vao)
        if (indexBuffer != -1) RenderSystem.glBindBuffer(GL33.GL_ELEMENT_ARRAY_BUFFER, ::indexBuffer)

        val modelView = Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose())
        modelView.mul(matrix.asMatrix4f())
        shader.MODEL_VIEW_MATRIX?.set(modelView)
        shader.MODEL_VIEW_MATRIX?.upload()

        shader.getUniform("NormalMat")?.let {
            val normal = Matrix3f(stack.last().normal())
            normal.mul(matrix.getUpperLeft(MutableMat3f()).asMatrix3f())
            it.set(normal)
            it.upload()
        }

        if (indexBuffer != -1) GL33.glDrawElements(GL33.GL_TRIANGLES, indexCount, GL33.GL_UNSIGNED_INT, 0L)
        else GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, positionsCount)

        if (hasShaders) {
            GL33.glGetUniformLocation(shader.id, "normals").takeIf { it != -1 }?.let {
                RenderSystem.activeTexture(COLOR_MAP_INDEX + GL33.glGetUniformi(shader.id, it))
                RenderSystem.bindTexture(normal)
            }
            GL33.glGetUniformLocation(shader.id, "specular").takeIf { it != -1 }?.let {
                RenderSystem.activeTexture(COLOR_MAP_INDEX + GL33.glGetUniformi(shader.id, it))
                RenderSystem.bindTexture(specular)
            }
        }
    }

    fun initBatching(
        pipeline: RenderPipeline,
        matrixGetter: MatrixGetter,
        positions: Array<Vec3f>,
        texCoords: Array<Vec2f>,
        normals: Array<Vec3f>,
    ) {
        val color = material.color

        val renderType = getRenderType()
        if (indices != null) {
            pipeline.addBatchedRenderable {
                val vertexConsumer = source.getBuffer(renderType)
                val pose = stack.last().pose()
                val normal = stack.last().normal()

                for (index in indices) {
                    putVertex(
                        matrixGetter, positions, texCoords, normals, vertexConsumer, pose,
                        normal, index, color, overlay, light
                    )
                }
            }
        } else {
            pipeline.addBatchedRenderable {
                val vertexConsumer = source.getBuffer(renderType)
                val pose = stack.last().pose()
                val normal = stack.last().normal()
                for (i in 0 until positions.size) {
                    putVertex(
                        matrixGetter, positions, texCoords, normals, vertexConsumer, pose,
                        normal, i, color, overlay, light
                    )
                }
            }
        }
    }

    private fun putVertex(
        getter: MatrixGetter,
        positions: Array<Vec3f>,
        texCoords: Array<Vec2f>,
        normals: Array<Vec3f>,
        consumer: VertexConsumer,
        pose: Matrix4f,
        normalMat: Matrix3f,
        index: Int,
        color: Color,
        overlayCoords: Int,
        packedLight: Int,
    ) {
        val global = getter()
        val pos = global.transform(positions[index], 1f, MutableVec3f())
        val normal = global.getUpperLeft(MutableMat3f()).transform(normals[index], MutableVec3f())

        consumer
            .vertex(pose, pos.x, pos.y, pos.z)
            .color(color.r, color.g, color.b, color.a)
            .uv(texCoords[index].x, texCoords[index].y)
            .overlayCoords(overlayCoords)
            .uv2(packedLight)
            .normal(normalMat, normal.x, normal.y, normal.z)
            .endVertex()
    }

    private fun getRenderType(): RenderType {
        return batchingRenderType.apply(material)
    }

    private fun applyMaterial(
        shader: ShaderInstance,
        material: Material,
    ): Pair<Int, Int> {
        GL33.glVertexAttrib4f(1, material.color.r, material.color.g, material.color.b, material.color.a)

        var normal = 0
        var specular = 0

        if (areShadersEnabled) {
            GL33.glGetUniformLocation(shader.id, "normals").takeIf { it != -1 }?.let {
                RenderSystem.activeTexture(COLOR_MAP_INDEX + GL33.glGetUniformi(shader.id, it))
                normal = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                RenderSystem.bindTexture(material.normalTexture.toTexture().id)
            }
            GL33.glGetUniformLocation(shader.id, "specular").takeIf { it != -1 }?.let {
                RenderSystem.activeTexture(COLOR_MAP_INDEX + GL33.glGetUniformi(shader.id, it))
                specular = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
                RenderSystem.bindTexture(material.specularTexture.toTexture().id)
            }
        }

        RenderSystem.activeTexture(COLOR_MAP_INDEX)
        RenderSystem.bindTexture(Minecraft.getInstance().textureManager.getTexture(material.texture).id)

        if (material.doubleSided) RenderSystem.disableCull()
        else RenderSystem.enableCull()

        when (material.blend) {
            Material.Blend.OPAQUE -> RenderSystem.disableBlend()
            Material.Blend.BLEND -> {
                RenderSystem.enableBlend()
                RenderSystem.defaultBlendFunc()
            }
        }

        return normal to specular
    }

    private fun updateMorphTargets() {
        morphCommands.forEach { it(weights) }
    }

    private fun initSkinning(pipeline: RenderPipeline, node: SkinGetter) {
        if(hasSkinning) {
            pipeline.addSkinnable { transformSkinning(node) }
        }
    }

    private fun transformSkinning(node: SkinGetter) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL33.glBindBuffer(GL33.GL_TEXTURE_BUFFER, jointMatrixBuffer)
        GL33.glBufferSubData(GL33.GL_TEXTURE_BUFFER, 0, computeMatrices(node))

        GL33.glBindTexture(GL33.GL_TEXTURE_BUFFER, glTexture)

        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, vertexBuffer)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, normalBuffer)

        GL30.glBeginTransformFeedback(GL11.GL_POINTS)
        GL30.glBindVertexArray(skinningVao)

        GL11.glDrawArrays(GL11.GL_POINTS, 0, positionsCount)

        GL30.glEndTransformFeedback()
    }

    private fun computeMatrices(node: SkinGetter): FloatBuffer {
        val matrices = node()
        val buffer = BufferUtils.createFloatBuffer(matrices.size * 16)
        for (m in matrices) {
            buffer.put(m.m00).put(m.m01).put(m.m02).put(m.m03)
                .put(m.m10).put(m.m11).put(m.m12).put(m.m13)
                .put(m.m20).put(m.m21).put(m.m22).put(m.m23)
                .put(m.m30).put(m.m31).put(m.m32).put(m.m33)
        }
        buffer.flip()
        return buffer
    }

    fun destroy() {
        if (useBatching) {
            releaseCpu()
        } else {
            GL30.glDeleteVertexArrays(vao)
            GL30.glDeleteVertexArrays(skinningVao)
            GL30.glDeleteBuffers(indexBuffer)
            GL30.glDeleteBuffers(vertexBuffer)
            GL30.glDeleteBuffers(texCoordsBuffer)
            GL30.glDeleteBuffers(normalBuffer)
            GL30.glDeleteBuffers(midCoordsBuffer)
            GL30.glDeleteBuffers(skinVertexBuffer)
            GL30.glDeleteBuffers(skinNormalBuffer)
        }
    }
}

typealias MatrixGetter = () -> Mat4f
typealias SkinGetter = () -> Array<Mat4f>

fun Vec3f.get(i: Int): Float {
    return when (i) {
        0 -> x
        1 -> y
        2 -> z
        else -> error("Invalid Vec3f index")
    }
}

fun Vec4f.get(i: Int): Float {
    return when (i) {
        0 -> x
        1 -> y
        2 -> z
        3 -> w
        else -> error("Invalid Vec4f index")
    }
}