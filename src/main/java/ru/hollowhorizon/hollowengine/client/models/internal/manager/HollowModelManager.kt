package ru.hollowhorizon.hollowengine.client.models.internal.manager

import kotlinx.coroutines.runBlocking
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.AbstractTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.client.models.bedrock.BedrockModelLoader
import ru.hollowhorizon.hollowengine.client.models.fbx.FbxModelLoader
import ru.hollowhorizon.hollowengine.client.models.gltf.GltfModelLoader
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.obj.ObjModelLoader
import ru.hollowhorizon.hollowengine.client.textures.GlTexture
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.measureTime


object HollowModelManager : ResourceManagerReloadListener {
    lateinit var lightTexture: AbstractTexture
    private val models = HashMap<ResourceLocation, AnimatedModel>()
    var glProgramSkinning = -1

    private val loaders = mutableListOf<ModelLoader>().apply {
        RegisterModelLoaderEvent(this).post()
    }

    fun getOrCreate(location: ResourceLocation) = models.computeIfAbsent(location) { model ->
        runBlocking { loadModel(model) }?.apply { this.model.initGl() } ?: error("Failed to load $location!")
    }

    suspend fun loadModel(location: ResourceLocation): AnimatedModel? {
        val extension = location.path.substringAfterLast('.', "")
        val loader = loaders.find { extension in it.supportedFormats }
            ?: error("No suitable model loader found for format .$extension")

        try {
            return loader.load(location)
        } catch (e: Exception) {
            HollowCore.LOGGER.warn("Model $location failed to load!", e)
            return null
        }
    }

    private fun createSkinningProgramGL33() {
        val glShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER)
        GL20.glShaderSource(
            glShader,
            "hollowengine:shaders/core/gltf_skinning.vsh".rl.stream.readBytes().decodeToString()
        )
        GL20.glCompileShader(glShader)

        glProgramSkinning = GL20.glCreateProgram()
        GL20.glAttachShader(glProgramSkinning, glShader)
        GL20.glDeleteShader(glShader)
        GL30.glTransformFeedbackVaryings(
            glProgramSkinning, arrayOf<CharSequence>("outPosition", "outNormal"), GL30.GL_SEPARATE_ATTRIBS
        )
        GL20.glLinkProgram(glProgramSkinning)
    }

    override fun onResourceManagerReload(manager: ResourceManager) {
        models.values.forEach { it.destroy() }
        models.clear()

        runBlocking {
            val time = measureTime {
                val supportedFormats = loaders.flatMap { it.supportedFormats }.toSet()
                val loaded =
                    manager.listResources("models") { it.path.substringAfter('.') in supportedFormats }.keys
                        .filter { manager.getResource(it.withSuffix(".hemeta")).isPresent }
                        .mapNotNull { location ->
                            loadModel(location)?.let { location to it }
                        }.toMap()

                models.putAll(loaded)
            }

            HollowCore.LOGGER.info("Loaded ${models.size} models in $time")
        }

        models.forEach { it.value.model.initGl() }

    }

    fun initialize() {
        val textureManager = Minecraft.getInstance().textureManager

        lightTexture = textureManager.getTexture("dynamic/light_map_1".rl)

        val currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

        val defaultColorMap = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultColorMap)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, create(
                byteArrayOf(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1)
            )
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)

        val defaultNormalMap = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultNormalMap)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, create(
                byteArrayOf(-128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1)
            )
        )
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)

        val defaultSpecularMap = GL11.glGenTextures()
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultSpecularMap)
        GL11.glTexImage2D(
            GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, create(
                byteArrayOf(
                    0, 0, 0, 0, // Pixel 1: Black color, Max Roughness
                    0, 0, 0, 0, // Pixel 2
                    0, 0, 0, 0, // Pixel 3
                    0, 0, 0, 0  // Pixel 4
                )
            )
        )
        Minecraft.getInstance().player?.random

        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0)



        textureManager.register("${HollowCore.MODID}:default_color_map".rl, GlTexture(defaultColorMap))
        textureManager.register("${HollowCore.MODID}:default_normal_map".rl, GlTexture(0))
        textureManager.register("${HollowCore.MODID}:default_specular_map".rl, GlTexture(0))

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture)

        createSkinningProgramGL33()
    }

    val allModels get() = models.keys

}

interface ModelLoader {
    val supportedFormats: Set<String>

    suspend fun load(location: ResourceLocation): AnimatedModel
}

class RegisterModelLoaderEvent(private val loaders: MutableList<ModelLoader>) : ClientEvent {
    fun register(loader: ModelLoader) {
        loaders.add(loader)
    }

    fun unregister(loader: ModelLoader) = loaders.removeIf { it == loader }

    fun clear() {
        loaders.clear()
    }

    fun getLoaders(): List<ModelLoader> = loaders.toList()
}

@SubscribeEvent
fun registerModelLoaders(event: RegisterModelLoaderEvent) {
    event.register(GltfModelLoader)
    event.register(ObjModelLoader)
    event.register(FbxModelLoader)
    event.register(BedrockModelLoader)
}

fun create(data: ByteArray) = create(data, 0, data.size)

fun create(data: ByteArray?, offset: Int, length: Int): ByteBuffer {
    val byteBuffer = ByteBuffer.allocateDirect(length)
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
    byteBuffer.put(data, offset, length)
    byteBuffer.position(0)
    return byteBuffer
}