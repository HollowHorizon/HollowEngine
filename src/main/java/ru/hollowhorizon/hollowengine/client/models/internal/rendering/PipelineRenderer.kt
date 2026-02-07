package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.math.MutableMat3f
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.ShaderInstance
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.client.models.internal.*
import ru.hollowhorizon.hollowengine.client.models.internal.utils.VboWrapper
import ru.hollowhorizon.hollowengine.client.models.internal.utils.toFloatBuffer
import ru.hollowhorizon.hollowengine.client.utils.areShadersEnabled
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix3f
import ru.hollowhorizon.hollowengine.client.utils.math.asMatrix4f
import ru.hollowhorizon.hollowengine.client.utils.toTexture

class PipelineRenderer(private val primitive: Primitive) : MeshRenderer {
    private var vao = -1

    // Обертки для VBO, которые будут использоваться при отрисовке (DrawArrays/Elements)
    private var posBuffer: VboWrapper? = null
    private var norBuffer: VboWrapper? = null
    private var tanBuffer: VboWrapper? = null
    private var uvBuffer: VboWrapper? = null
    private var indexBuffer: VboWrapper? = null

    // Система деформации (Скиннинг/Морфинг)
    private var deformer: GpuDeformer? = null

    // Флаг: требует ли модель предварительной обработки на GPU
    private val isDynamic = primitive.hasSkinning || primitive.morphTargets.isNotEmpty()

    override fun init() {
        vao = GL33.glGenVertexArrays()
        GL33.glBindVertexArray(vao)

        // 1. Инициализация геометрических буферов (позиции, нормали, тангенсы)
        if (isDynamic) {
            // Если модель динамическая, создаем пустые буферы и инициализируем деформер
            initDynamicBuffers()

            deformer = GpuDeformer(primitive)
            // Передаем ID наших буферов деформеру, чтобы он знал, куда писать результат Transform Feedback
            deformer?.init(
                dstPos = posBuffer!!.id,
                dstNor = norBuffer!!.id,
                dstTan = tanBuffer!!.id
            )
        } else {
            // Если модель статическая, просто загружаем данные
            initStaticBuffers()
        }

        // 2. Инициализация общих буферов (UV координаты и индексы), они не меняются при деформации
        initCommonBuffers()

        // Снимаем бинд VAO
        GL33.glBindVertexArray(0)

        // Снимаем бинды буферов, чтобы случайно не испортить их извне
        posBuffer?.unbind()
        indexBuffer?.unbind()
    }

    private fun initStaticBuffers() {
        // --- Positions ---
        primitive.positions?.let { positions ->
            posBuffer = VboWrapper.createArrayBuffer().apply {
                val data = positions.toFloatBuffer(3) { v, b -> b.put(v.x).put(v.y).put(v.z) }
                uploadData(data)
                // Настраиваем атрибуты VAO
                GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(0)
            }
        }

        // --- Normals ---
        primitive.normals?.let { normals ->
            norBuffer = VboWrapper.createArrayBuffer().apply {
                val data = normals.toFloatBuffer(3) { v, b -> b.put(v.x).put(v.y).put(v.z) }
                uploadData(data)
                GL33.glVertexAttribPointer(5, 3, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(5)
            }
        }

        // --- Tangents ---
        primitive.tangents?.let { tangents ->
            tanBuffer = VboWrapper.createArrayBuffer().apply {
                val data = tangents.toFloatBuffer(4) { v, b -> b.put(v.x).put(v.y).put(v.z).put(v.w) }
                uploadData(data)
                GL33.glVertexAttribPointer(9, 4, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(9)
            }
        }
    }

    private fun initDynamicBuffers() {
        val vertexCount = primitive.positionsCount / 3

        // Выделяем память под TRANSFORM_FEEDBACK вывод.
        // GL_DYNAMIC_COPY намекает драйверу, что данные будут часто меняться и использоваться для отрисовки.

        // Positions (vec3)
        posBuffer = VboWrapper.createArrayBuffer().apply {
            allocate(vertexCount * 3 * 4L, GL33.GL_DYNAMIC_COPY)
            GL33.glVertexAttribPointer(0, 3, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(0)
        }

        // Normals (vec3)
        norBuffer = VboWrapper.createArrayBuffer().apply {
            allocate(vertexCount * 3 * 4L, GL33.GL_DYNAMIC_COPY)
            GL33.glVertexAttribPointer(5, 3, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(5)
        }

        // Tangents (vec4 - сохраняем W компоненту для handedness)
        tanBuffer = VboWrapper.createArrayBuffer().apply {
            allocate(vertexCount * 4 * 4L, GL33.GL_DYNAMIC_COPY)
            GL33.glVertexAttribPointer(9, 4, GL33.GL_FLOAT, false, 0, 0)
            GL33.glEnableVertexAttribArray(9)
        }
    }

    private fun initCommonBuffers() {
        // --- UV Coordinates ---
        primitive.texCoords?.let { uvs ->
            uvBuffer = VboWrapper.createArrayBuffer().apply {
                val data = uvs.toFloatBuffer(2) { v, b -> b.put(v.x).put(v.y) }
                uploadData(data)
                GL33.glVertexAttribPointer(2, 2, GL33.GL_FLOAT, false, 0, 0)
                GL33.glEnableVertexAttribArray(2)
            }
        }

        // --- Indices ---
        primitive.indices?.let { indices ->
            indexBuffer = VboWrapper.createElementBuffer().apply {
                val buffer = BufferUtils.createIntBuffer(indices.size)
                buffer.put(indices)
                buffer.flip()
                uploadData(buffer)
            }
            // Element Buffer привязывается к VAO
        }
    }

    override fun setupPipeline(
        pipeline: RenderPipeline,
        skinGetter: SkinGetter,
        matrixGetter: MatrixGetter,
        visibilityGetter: VisibilityGetter
    ) {
        // Шаг 1: Если модель динамическая, добавляем задачу на вычисление геометрии (Transform Feedback)
        if (isDynamic && deformer != null) {
            pipeline.addSkinnable {
                if (visibilityGetter()) {
                    deformer!!.compute(skinGetter)
                }
            }
        }

        // Шаг 2: Добавляем задачу на саму отрисовку
        pipeline.addVAORenderable {
            if (!visibilityGetter()) return@addVAORenderable
            renderVAO(matrixGetter)
        }
    }

    private fun RenderContext.renderVAO(node: MatrixGetter) {
        val shader = RenderSystem.getShader() ?: return
        val matrix = node()

        applyMaterial(shader, primitive.material)

        RenderSystem.glBindVertexArray(::vao)
        // Иногда Blaze3D теряет бинд индексного буфера, лучше перестраховаться
        indexBuffer?.bind()

        // Расчет ModelView матрицы
        val modelView = Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.last().pose())
        modelView.mul(matrix.asMatrix4f())
        shader.MODEL_VIEW_MATRIX?.set(modelView)
        shader.MODEL_VIEW_MATRIX?.upload()

        // Расчет Normal Matrix
        shader.getUniform("NormalMat")?.let {
            val normal = Matrix3f(stack.last().normal())
            normal.mul(matrix.getUpperLeft(MutableMat3f()).asMatrix3f())
            it.set(normal)
            it.upload()
        }

        // Отрисовка
        val count = primitive.indices?.size ?: (primitive.positionsCount / 3)
        if (indexBuffer != null) {
            GL33.glDrawElements(GL33.GL_TRIANGLES, count, GL33.GL_UNSIGNED_INT, 0L)
        } else {
            GL33.glDrawArrays(GL33.GL_TRIANGLES, 0, count)
        }

        GL33.glBindVertexArray(0)
    }

    // Метод applyMaterial опущен, так как он не менялся функционально,
    // но он должен быть таким же, как в оригинале (биндинг текстур и установка цветов).
    private fun applyMaterial(shader: ShaderInstance, material: Material) {
        // Реализация аналогична оригинальному методу
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
    }

    override fun destroy() {
        GL33.glDeleteVertexArrays(vao)
        posBuffer?.delete()
        norBuffer?.delete()
        tanBuffer?.delete()
        uvBuffer?.delete()
        indexBuffer?.delete()

        deformer?.destroy()
    }
}