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
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.RenderPipeline
import ru.hollowhorizon.hollowengine.client.utils.areShadersEnabled
import ru.hollowhorizon.hollowengine.client.utils.hasShaders
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix3f
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix4f
import ru.hollowhorizon.hollowengine.client.utils.toTexture
import java.nio.FloatBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
    val morphTargets: List<Map<String, FloatArray>> = listOf(),
    var weights: FloatArray = FloatArray(morphTargets.size) { 0f },
) {
    val hasSkinning = joints != null && jointWeights != null
    private val indexCount = indices?.size ?: 0
    val positionsCount = (positions?.size ?: 0) * 3
    var jointCount = 0
    private val morphCommands = ArrayList<(FloatArray) -> Unit>()

    // Порог для переключения между режимами рендеринга
    val useBatching = positionsCount < 512 && !hasSkinning && morphTargets.isEmpty()
    val localBounds: Pair<Vec3f, Vec3f>? = positions?.takeIf { it.isNotEmpty() }?.let { positions ->
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        positions.forEach { position ->
            minX = min(minX, position.x)
            minY = min(minY, position.y)
            minZ = min(minZ, position.z)
            maxX = max(maxX, position.x)
            maxY = max(maxY, position.y)
            maxZ = max(maxZ, position.z)
        }
        Vec3f(minX, minY, minZ) to Vec3f(maxX, maxY, maxZ)
    }

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
    private var skinTangentBuffer = -1
    private var jointMatrixBuffer = -1

    private var morphPosBuffer = -1
    private var morphPosTexture = -1
    private var morphNorBuffer = -1
    private var morphNorTexture = -1
    private var morphTanBuffer = -1
    private var morphTanTexture = -1

    // Кэш для RenderType
    private var cachedRenderType: RenderType? = null

    fun setupPipeline(
        pipeline: RenderPipeline,
        skinGetter: SkinGetter,
        matrixGetter: MatrixGetter,
        visibilityGetter: VisibilityGetter,
    ) {
        if (useBatching) {
            initBatching(
                pipeline,
                matrixGetter,
                positions ?: return,
                texCoords ?: return,
                normals ?: return,
                visibilityGetter
            )
        } else {
            initSkinning(pipeline, skinGetter, visibilityGetter)
            initVAO(pipeline, matrixGetter, visibilityGetter)
        }
    }

    fun init() {
        if (useBatching) return

        if (normals == null && positions != null) {
            recalculateNormals()
        }
        if (tangents == null && positions != null && texCoords != null && normals != null) {
            recalculateTangents()
        }

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

    private fun recalculateNormals() {
        val pos = positions ?: return
        val count = pos.size
        val accumNormals = Array(count) { MutableVec3f() }

        val p1 = MutableVec3f()
        val p2 = MutableVec3f()
        val p3 = MutableVec3f()
        val e1 = MutableVec3f()
        val e2 = MutableVec3f()
        val nrm = MutableVec3f()

        val getIndex = { i: Int -> indices?.get(i) ?: i }
        val loops = indices?.size ?: count

        for (i in 0 until loops step 3) {
            val i1 = getIndex(i)
            val i2 = getIndex(i + 1)
            val i3 = getIndex(i + 2)

            if (i1 >= count || i2 >= count || i3 >= count) continue

            p1.set(pos[i1])
            p2.set(pos[i2])
            p3.set(pos[i3])

            p2.subtract(p1, e1).norm()
            p3.subtract(p1, e2).norm()

            e1.cross(e2, nrm).norm()

            p2.subtract(p1, e1)
            p3.subtract(p1, e2)
            val area = 0.5f * e1.cross(e2, MutableVec3f()).length()

            nrm.mul(area)

            if (!nrm.x.isNaN() && !nrm.y.isNaN() && !nrm.z.isNaN()) {
                accumNormals[i1].add(nrm)
                accumNormals[i2].add(nrm)
                accumNormals[i3].add(nrm)
            }
        }

        normals = Array(count) { i ->
            val n = accumNormals[i]
            if (n.sqrLength() > 0.000001f) n.norm() else n.set(0f, 1f, 0f)
            Vec3f(n.x, n.y, n.z)
        }
    }

    private fun recalculateTangents() {
        val pos = positions ?: return
        val uvs = texCoords ?: return
        val norm = normals ?: return
        val count = pos.size

        val accumTangents = Array(count) { MutableVec3f() }

        val p1 = MutableVec3f(); val p2 = MutableVec3f(); val p3 = MutableVec3f()
        val uv1 = MutableVec2f(); val uv2 = MutableVec2f(); val uv3 = MutableVec2f()
        val e1 = MutableVec3f(); val e2 = MutableVec3f()
        val tan = MutableVec3f()

        val getIndex = { i: Int -> indices?.get(i) ?: i }
        val loops = indices?.size ?: count

        for (i in 0 until loops step 3) {
            val i1 = getIndex(i)
            val i2 = getIndex(i + 1)
            val i3 = getIndex(i + 2)

            if (i1 >= count || i2 >= count || i3 >= count) continue

            p1.set(pos[i1]); p2.set(pos[i2]); p3.set(pos[i3])
            uv1.set(uvs[i1]); uv2.set(uvs[i2]); uv3.set(uvs[i3])

            p2.subtract(p1, e1).norm()
            p3.subtract(p1, e2).norm()

            val du1 = uv2.x - uv1.x
            val dv1 = uv2.y - uv1.y
            val du2 = uv3.x - uv1.x
            val dv2 = uv3.y - uv1.y

            val det = du1 * dv2 - du2 * dv1
            if (abs(det) < 1e-6f) continue

            val f = 1f / det

            if (!f.isNaN()) {
                tan.x = f * (dv2 * e1.x - dv1 * e2.x)
                tan.y = f * (dv2 * e1.y - dv1 * e2.y)
                tan.z = f * (dv2 * e1.z - dv1 * e2.z)

                accumTangents[i1].add(tan)
                accumTangents[i2].add(tan)
                accumTangents[i3].add(tan)
            }
        }

        tangents = Array(count) { i ->
            val t = accumTangents[i]
            if (t.sqrLength() > 0.000001f) t.norm() else t.set(1f, 0f, 0f)

            Vec4f(t.x, t.y, t.z, 1f)
        }
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
            }

            tangents?.let { tangents ->
                val buffer = BufferUtils.createFloatBuffer(tangents.size * 4)
                for (t in tangents) buffer.put(t.x).put(t.y).put(t.z).put(t.w)
                buffer.flip()
                morphCommands += { array ->
                    for (i in 0 until tangents.size * 4) {
                        val component = i % 4
                        var value = tangents[i / 4].get(component)

                        if (component < 3) {
                            array.forEachIndexed { j, percent ->
                                morphTargets[j][GltfMesh.Primitive.ATTRIBUTE_TANGENT]?.let {
                                    val morphIndex = (i / 4) * 3 + component
                                    if (morphIndex < it.size) {
                                        value += it[morphIndex] * percent
                                    }
                                }
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
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, tangentBuffer)
            GL33.glVertexAttribPointer(9, 4, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(9)
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
        var tanSize = -1L

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

        tangents?.let { tangents ->
            tanSize = tangents.size * 16L // vec4 * 4 bytes
            val buffer = BufferUtils.createFloatBuffer(tangents.size * 4)
            for (t in tangents) buffer.put(t.x).put(t.y).put(t.z).put(t.w)
            buffer.flip()
            skinTangentBuffer = GL33.glGenBuffers()
            GL33.glBindBuffer(GL33.GL_ARRAY_BUFFER, skinTangentBuffer)
            GL33.glBufferData(GL33.GL_ARRAY_BUFFER, buffer, GL33.GL_STATIC_DRAW)
            GL33.glVertexAttribPointer(4, 4, GL33.GL_FLOAT, false, 0, 0) // Attribute 4
            GL33.glEnableVertexAttribArray(4)
        }

        if (morphTargets.isNotEmpty() && positions != null) {
            val vertexCount = positions!!.size
            val morphCount = morphTargets.size
            val totalSize = vertexCount * morphCount * 4

            val posDeltaBuffer = BufferUtils.createFloatBuffer(totalSize)
            val norDeltaBuffer = BufferUtils.createFloatBuffer(totalSize)
            val tanDeltaBuffer = BufferUtils.createFloatBuffer(totalSize)

            for (targetMap in morphTargets) {
                val posDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_POSITION]
                val norDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_NORMAL]
                val tanDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_TANGENT]

                for (i in 0 until vertexCount) {
                    if (posDeltas != null) {
                        posDeltaBuffer.put(posDeltas[i * 3]).put(posDeltas[i * 3 + 1]).put(posDeltas[i * 3 + 2]).put(0f)
                    } else {
                        posDeltaBuffer.put(0f).put(0f).put(0f).put(0f)
                    }

                    if (norDeltas != null) {
                        norDeltaBuffer.put(norDeltas[i * 3]).put(norDeltas[i * 3 + 1]).put(norDeltas[i * 3 + 2]).put(0f)
                    } else {
                        norDeltaBuffer.put(0f).put(0f).put(0f).put(0f)
                    }

                    if (tanDeltas != null) tanDeltaBuffer.put(tanDeltas[i * 3]).put(tanDeltas[i * 3 + 1]).put(0f)
                        .put(tanDeltas[i * 3 + 2])
                    else tanDeltaBuffer.put(0f).put(0f).put(0f).put(0f)
                }
            }
            posDeltaBuffer.flip()
            norDeltaBuffer.flip()
            tanDeltaBuffer.flip()


            // Создаем TBO для позиций
            morphPosBuffer = GL15.glGenBuffers()
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, morphPosBuffer)
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, posDeltaBuffer, GL15.GL_STATIC_DRAW)

            morphPosTexture = GL11.glGenTextures()
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphPosTexture)
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, morphPosBuffer)

            // Создаем TBO для нормалей
            morphNorBuffer = GL15.glGenBuffers()
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, morphNorBuffer)
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, norDeltaBuffer, GL15.GL_STATIC_DRAW)

            morphNorTexture = GL11.glGenTextures()
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphNorTexture)
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, morphNorBuffer)

            morphTanBuffer = GL15.glGenBuffers()
            GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, morphTanBuffer)
            GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, tanDeltaBuffer, GL15.GL_STATIC_DRAW)

            morphTanTexture = GL11.glGenTextures()
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphTanTexture)
            GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, morphTanBuffer)
        }

        vertexBuffer = GL33.glGenBuffers()
        GL33.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, vertexBuffer)
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, posSize, GL33.GL_STATIC_DRAW)

        normalBuffer = GL33.glGenBuffers()
        GL33.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, normalBuffer)
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, norSize, GL33.GL_STATIC_DRAW)

        tangentBuffer = GL33.glGenBuffers()
        GL33.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, tangentBuffer)
        GL15.glBufferData(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, tanSize, GL33.GL_STATIC_DRAW)

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
        visibilityGetter: VisibilityGetter,
    ) {
        if (useBatching) return
        if (morphTargets.isNotEmpty()) {
            pipeline.addVAORenderable {
                if (!visibilityGetter()) return@addVAORenderable
                updateMorphTargets()
            }
        }
        pipeline.addVAORenderable {
            if (!visibilityGetter()) return@addVAORenderable
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
        visibilityGetter: VisibilityGetter,
    ) {
        val color = material.color

        if (indices != null) {
            pipeline.addBatchedRenderable {
                if (!visibilityGetter()) return@addBatchedRenderable
                val renderType = getRenderType()
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
                if (!visibilityGetter()) return@addBatchedRenderable
                val renderType = getRenderType()
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

    private fun initSkinning(pipeline: RenderPipeline, node: SkinGetter, visibilityGetter: VisibilityGetter) {
        if (hasSkinning) {
            pipeline.addSkinnable { if (visibilityGetter()) transformSkinning(node) }
        }
    }

    private fun transformSkinning(node: SkinGetter) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        GL33.glBindBuffer(GL33.GL_TEXTURE_BUFFER, jointMatrixBuffer)
        GL33.glBufferSubData(GL33.GL_TEXTURE_BUFFER, 0, computeMatrices(node))
        GL33.glBindTexture(GL33.GL_TEXTURE_BUFFER, glTexture)

        val shaderId = HollowModelManager.glProgramSkinning
        val locJoints = GL20.glGetUniformLocation(shaderId, "jointMatrices")
        GL20.glUniform1i(locJoints, 0)

        if (morphTargets.isNotEmpty()) {
            // Биндим TBO позиций (Texture Unit 1)
            GL13.glActiveTexture(GL13.GL_TEXTURE1)
            GL33.glBindTexture(GL33.GL_TEXTURE_BUFFER, morphPosTexture)
            val locMorphPos = GL20.glGetUniformLocation(shaderId, "morphDeltasPosition")
            GL20.glUniform1i(locMorphPos, 1)

            // Биндим TBO нормалей (Texture Unit 2)
            GL13.glActiveTexture(GL13.GL_TEXTURE2)
            GL33.glBindTexture(GL33.GL_TEXTURE_BUFFER, morphNorTexture)
            val locMorphNor = GL20.glGetUniformLocation(shaderId, "morphDeltasNormal")
            GL20.glUniform1i(locMorphNor, 2)

            // Биндим TBO тангенсов (Texture Unit 3)
            GL13.glActiveTexture(GL13.GL_TEXTURE3)
            GL33.glBindTexture(GL33.GL_TEXTURE_BUFFER, morphTanTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "morphDeltasTangent"), 3)

            val locCount = GL20.glGetUniformLocation(shaderId, "activeMorphCount")
            GL20.glUniform1i(locCount, morphTargets.size)

            val locVCount = GL20.glGetUniformLocation(shaderId, "vertexCount")
            GL20.glUniform1i(locVCount, positionsCount / 3)

            val locWeights = GL20.glGetUniformLocation(shaderId, "morphWeights")
            if (locWeights != -1 && weights.isNotEmpty()) {
                val shaderWeights = FloatArray(64) // Размер как в шейдере
                for (i in weights.indices) {
                    if (i < 64) shaderWeights[i] = weights[i]
                }
                GL20.glUniform1fv(locWeights, shaderWeights)
            }
        } else {
            // Если морфов нет, ставим count = 0, чтобы цикл в шейдере не крутился
            val locCount = GL20.glGetUniformLocation(shaderId, "activeMorphCount")
            GL20.glUniform1i(locCount, 0)
        }

        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, vertexBuffer)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, normalBuffer)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 2, tangentBuffer)

        GL30.glBeginTransformFeedback(GL11.GL_POINTS)
        GL30.glBindVertexArray(skinningVao)

        GL11.glDrawArrays(GL11.GL_POINTS, 0, positionsCount / 3)

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
            GL30.glDeleteBuffers(skinTangentBuffer)

            if (morphPosBuffer != -1) GL30.glDeleteBuffers(morphPosBuffer)
            if (morphNorBuffer != -1) GL30.glDeleteBuffers(morphNorBuffer)
            if (morphTanBuffer != -1) GL30.glDeleteBuffers(morphTanBuffer)
            if (morphPosTexture != -1) GL11.glDeleteTextures(morphPosTexture)
            if (morphNorTexture != -1) GL11.glDeleteTextures(morphNorTexture)
            if (morphTanTexture != -1) GL11.glDeleteTextures(morphTanTexture)
        }
    }
}

typealias MatrixGetter = () -> Mat4f
typealias SkinGetter = () -> Array<Mat4f>
typealias VisibilityGetter = () -> Boolean

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