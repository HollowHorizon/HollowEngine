package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.*
import ru.hollowhorizon.hollowengine.client.models.gltf.GltfMesh
import ru.hollowhorizon.hollowengine.client.models.internal.Primitive
import ru.hollowhorizon.hollowengine.client.models.internal.SkinGetter
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.models.internal.utils.VboWrapper
import ru.hollowhorizon.hollowengine.client.models.internal.utils.toFloatBuffer
import kotlin.math.min

class GpuDeformer(private val primitive: Primitive) {
    private var processingVao = -1

    private var srcPosBuffer: VboWrapper? = null
    private var srcNorBuffer: VboWrapper? = null
    private var srcTanBuffer: VboWrapper? = null

    private var srcJointsBuffer: VboWrapper? = null
    private var srcWeightsBuffer: VboWrapper? = null

    private var morphPosBuffer: VboWrapper? = null
    private var morphPosTexture = -1
    private var morphNorBuffer: VboWrapper? = null
    private var morphNorTexture = -1
    private var morphTanBuffer: VboWrapper? = null
    private var morphTanTexture = -1

    private var jointMatrixBuffer: VboWrapper? = null
    private var jointMatrixTexture = -1

    private var outPosBufferId = -1
    private var outNorBufferId = -1
    private var outTanBufferId = -1

    private var drawCount = 0

    fun init(dstPos: Int, dstNor: Int, dstTan: Int) {
        this.outPosBufferId = dstPos
        this.outNorBufferId = dstNor
        this.outTanBufferId = dstTan
        this.drawCount = primitive.positions?.size ?: 0

        processingVao = GL30.glGenVertexArrays()
        GL30.glBindVertexArray(processingVao)

        initSourceAttributes()

        if (primitive.morphTargets.isNotEmpty()) {
            initMorphTextures()
        }

        if (primitive.hasSkinning) {
            initJointMatrixTexture()
        }

        GL30.glBindVertexArray(0)
    }

    private fun initSourceAttributes() {
        // В шейдерах мы используем фиксированные location:
        // layout (location = 0) in vec4 joint;
        // layout (location = 1) in vec4 weight;
        // layout (location = 2) in vec3 position;
        // layout (location = 3) in vec3 normal;
        // layout (location = 4) in vec4 tangent;

        // --- Position (Loc 2) ---
        primitive.positions?.let { positions ->
            srcPosBuffer = VboWrapper.createArrayBuffer().apply {
                val data = positions.toFloatBuffer(3) { v, b -> b.put(v.x).put(v.y).put(v.z) }
                uploadData(data)
                GL33.glVertexAttribPointer(2, 3, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(2)
            }
        }

        // --- Normal (Loc 3) ---
        primitive.normals?.let { normals ->
            srcNorBuffer = VboWrapper.createArrayBuffer().apply {
                val data = normals.toFloatBuffer(3) { v, b -> b.put(v.x).put(v.y).put(v.z) }
                uploadData(data)
                GL33.glVertexAttribPointer(3, 3, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(3)
            }
        }

        // --- Tangent (Loc 4) ---
        primitive.tangents?.let { tangents ->
            srcTanBuffer = VboWrapper.createArrayBuffer().apply {
                val data = tangents.toFloatBuffer(4) { v, b -> b.put(v.x).put(v.y).put(v.z).put(v.w) }
                uploadData(data)
                GL33.glVertexAttribPointer(4, 4, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(4)
            }
        }

        // --- Joints & Weights (Loc 0 & 1) ---
        if (primitive.hasSkinning) {
            primitive.joints?.let { joints ->
                srcJointsBuffer = VboWrapper.createArrayBuffer().apply {
                    val buffer = BufferUtils.createIntBuffer(joints.size * 4)
                    joints.forEach { j -> buffer.put(j.x).put(j.y).put(j.z).put(j.w) }
                    buffer.flip()
                    uploadData(buffer)
                    GL33.glVertexAttribPointer(0, 4, GL33.GL_INT, false, 0, 0) // Важно: IPointer для int
                    GL33.glEnableVertexAttribArray(0)
                }
            }

            primitive.jointWeights?.let { weights ->
                srcWeightsBuffer = VboWrapper.createArrayBuffer().apply {
                    val data = weights.toFloatBuffer(4) { v, b -> b.put(v.x).put(v.y).put(v.z).put(v.w) }
                    uploadData(data)
                    GL33.glVertexAttribPointer(1, 4, GL33.GL_FLOAT, false, 0, 0)
                    GL33.glEnableVertexAttribArray(1)
                }
            }
        }
    }

    private fun initMorphTextures() {
        val morphCount = primitive.morphTargets.size
        // Размер буфера: Кол-во вершин * Кол-во таргетов * Компоненты (3 для Vec3)
        // Но TBO возвращает Vec4, поэтому лучше выравнивать, хотя мы используем rgb/xyz
        val totalElements = drawCount * morphCount

        val posBuffer = BufferUtils.createFloatBuffer(totalElements * 4) // X Y Z Padding
        val norBuffer = BufferUtils.createFloatBuffer(totalElements * 4) // X Y Z Padding
        val tanBuffer = BufferUtils.createFloatBuffer(totalElements * 4) // X Y Z Padding (Tangent usually 3 here)

        // Заполняем буферы. Порядок: Target 1 [All Verts], Target 2 [All Verts]...
        // Это соответствует формуле в шейдере: bufferIndex = (targetIndex * vertexCount) + vertexID
        for (targetMap in primitive.morphTargets) {
            val posDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_POSITION]
            val norDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_NORMAL]
            val tanDeltas = targetMap[GltfMesh.Primitive.ATTRIBUTE_TANGENT]

            for (i in 0 until drawCount) {
                // Position Delta
                if (posDeltas != null) {
                    posBuffer.put(posDeltas[i * 3]).put(posDeltas[i * 3 + 1]).put(posDeltas[i * 3 + 2]).put(0f)
                } else {
                    posBuffer.put(0f).put(0f).put(0f).put(0f)
                }

                // Normal Delta
                if (norDeltas != null) {
                    norBuffer.put(norDeltas[i * 3]).put(norDeltas[i * 3 + 1]).put(norDeltas[i * 3 + 2]).put(0f)
                } else {
                    norBuffer.put(0f).put(0f).put(0f).put(0f)
                }

                // Tangent Delta
                if (tanDeltas != null) {
                    // Тангенс в glTF тоже 3 компонента дельты
                    tanBuffer.put(tanDeltas[i * 3]).put(tanDeltas[i * 3 + 1]).put(tanDeltas[i * 3 + 2]).put(0f)
                } else {
                    tanBuffer.put(0f).put(0f).put(0f).put(0f)
                }
            }
        }

        posBuffer.flip(); norBuffer.flip(); tanBuffer.flip()

        // Создаем TBO для Позиций
        morphPosBuffer = VboWrapper.createTextureBuffer().apply {
            uploadData(posBuffer)
            morphPosTexture = createTextureFromBuffer(this.id)
        }

        // Создаем TBO для Нормалей
        morphNorBuffer = VboWrapper.createTextureBuffer().apply {
            uploadData(norBuffer)
            morphNorTexture = createTextureFromBuffer(this.id)
        }

        // Создаем TBO для Тангенсов
        morphTanBuffer = VboWrapper.createTextureBuffer().apply {
            uploadData(tanBuffer)
            morphTanTexture = createTextureFromBuffer(this.id)
        }
    }

    private fun initJointMatrixTexture() {
        // Буфер для матриц костей. Размер: кол-во костей * 16 float (mat4) * 4 bytes
        jointMatrixBuffer = VboWrapper.createTextureBuffer().apply {
            // Выделяем память заранее
            allocate(primitive.jointCount * 64L, GL33.GL_DYNAMIC_DRAW)
            jointMatrixTexture = createTextureFromBuffer(this.id)
        }
    }

    private fun createTextureFromBuffer(bufferId: Int): Int {
        val textureId = GL11.glGenTextures()
        GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, textureId)
        // GL_RGBA32F позволяет читать 4 float за раз через texelFetch
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, bufferId)
        return textureId
    }

    fun compute(node: SkinGetter) {
        val shaderId: Int

        // Выбираем шейдер и обновляем униформы
        if (primitive.hasSkinning) {
            shaderId = HollowModelManager.glProgramSkinning
            GL20.glUseProgram(shaderId)

            // Обновляем матрицы костей
            updateJointMatrices(node)

            // Биндим TBO матриц костей в слот 0
            GL13.glActiveTexture(GL13.GL_TEXTURE0)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, jointMatrixTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "jointMatrices"), 0)
        } else {
            // Используем шейдер чистого морфинга
            shaderId = HollowModelManager.glProgramMorphing
            GL20.glUseProgram(shaderId)
        }

        // Настраиваем униформы для морфинга
        setupMorphUniforms(shaderId)

        // Настраиваем Transform Feedback
        // Мы говорим OpenGL, в какие буферы писать вывод шейдера
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, outPosBufferId)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, outNorBufferId)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 2, outTanBufferId)

        // Запускаем процесс
        GL30.glBeginTransformFeedback(GL11.GL_POINTS)
        GL30.glBindVertexArray(processingVao)

        // Отключаем растеризацию, так как нам не нужны пиксели, только данные в буферах
        GL11.glEnable(GL30.GL_RASTERIZER_DISCARD)

        // Запускаем "отрисовку" для всех вершин
        GL11.glDrawArrays(GL11.GL_POINTS, 0, drawCount)

        // Завершаем
        GL11.glDisable(GL30.GL_RASTERIZER_DISCARD)
        GL30.glEndTransformFeedback()

        // Очистка
        GL30.glBindVertexArray(0)
        GL20.glUseProgram(0)
        // Отвязываем буферы TF
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0, 0)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 1, 0)
        GL30.glBindBufferBase(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 2, 0)
    }

    private fun updateJointMatrices(node: SkinGetter) {
        val matrices = node()
        // Создаем временный буфер для передачи данных
        val buffer = BufferUtils.createFloatBuffer(matrices.size * 16)
        for (m in matrices) {
            buffer.put(m.m00).put(m.m01).put(m.m02).put(m.m03)
            buffer.put(m.m10).put(m.m11).put(m.m12).put(m.m13)
            buffer.put(m.m20).put(m.m21).put(m.m22).put(m.m23)
            buffer.put(m.m30).put(m.m31).put(m.m32).put(m.m33)
        }
        buffer.flip()

        // Обновляем данные в TBO буфере
        jointMatrixBuffer?.bind()
        GL33.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0, buffer)
        jointMatrixBuffer?.unbind()
    }

    private fun setupMorphUniforms(shaderId: Int) {
        if (primitive.morphTargets.isNotEmpty()) {
            // Bind Morph Positions (Unit 1)
            GL13.glActiveTexture(GL13.GL_TEXTURE1)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphPosTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "morphDeltasPosition"), 1)

            // Bind Morph Normals (Unit 2)
            GL13.glActiveTexture(GL13.GL_TEXTURE2)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphNorTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "morphDeltasNormal"), 2)

            // Bind Morph Tangents (Unit 3)
            GL13.glActiveTexture(GL13.GL_TEXTURE3)
            GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, morphTanTexture)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "morphDeltasTangent"), 3)

            // Uniforms
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "activeMorphCount"), primitive.morphTargets.size)
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "vertexCount"), drawCount)

            // Weights
            val locWeights = GL20.glGetUniformLocation(shaderId, "morphWeights")
            if (locWeights != -1 && primitive.weights.isNotEmpty()) {
                val weightArray = FloatArray(64) // Соответствует uniform float morphWeights[64]
                val copyLength = min(primitive.weights.size, 64)
                System.arraycopy(primitive.weights, 0, weightArray, 0, copyLength)
                GL20.glUniform1fv(locWeights, weightArray)
            }
        } else {
            GL20.glUniform1i(GL20.glGetUniformLocation(shaderId, "activeMorphCount"), 0)
        }
    }

    fun destroy() {
        GL30.glDeleteVertexArrays(processingVao)

        srcPosBuffer?.delete()
        srcNorBuffer?.delete()
        srcTanBuffer?.delete()
        srcJointsBuffer?.delete()
        srcWeightsBuffer?.delete()

        morphPosBuffer?.delete()
        morphNorBuffer?.delete()
        morphTanBuffer?.delete()
        jointMatrixBuffer?.delete()

        if (morphPosTexture != -1) GL11.glDeleteTextures(morphPosTexture)
        if (morphNorTexture != -1) GL11.glDeleteTextures(morphNorTexture)
        if (morphTanTexture != -1) GL11.glDeleteTextures(morphTanTexture)
        if (jointMatrixTexture != -1) GL11.glDeleteTextures(jointMatrixTexture)
    }
}