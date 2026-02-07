package ru.hollowhorizon.hollowengine.client.models.internal.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.models.bedrock.BedrockModelLoader
import ru.hollowhorizon.hollowengine.client.models.fbx.FbxModelLoader
import ru.hollowhorizon.hollowengine.client.models.gltf.GltfModelLoader
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.obj.ObjModelLoader
import ru.hollowhorizon.hollowengine.client.textures.GlTexture
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap


object HollowModelManager : ResourceManagerReloadListener {
    lateinit var lightTexture: AbstractTexture
    private val models = ConcurrentHashMap<ResourceLocation, MutableStateFlow<AnimatedModel>>()
    var glProgramSkinning = -1
    var glProgramMorphing = -1

    private val loaders = mutableListOf<ModelLoader>().apply {
        RegisterModelLoaderEvent(this).post()
    }

    fun getOrCreate(location: ResourceLocation): StateFlow<AnimatedModel> {
        return models.computeIfAbsent(location) {
            val flow = MutableStateFlow(AnimatedModel.EMPTY)

            Minecraft.getInstance().coroutineScope.launch {
                try {
                    val model = loadModel(location) // Ваша suspend функция
                    flow.value = model
                } catch (e: Exception) {
                    HollowEngine.LOGGER.error("Can't load model $location", e)
                }
            }

            flow
        }
    }

    suspend fun loadModel(location: ResourceLocation): AnimatedModel {
        val extension = location.path.substringAfter('.', "")

        val loader = loaders.find { extension in it.supportedFormats }
            ?: error("No suitable model loader found for format .$extension")

        return loader.load(location)
    }

    private fun createSkinningProgramGL33() {
        var glShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER)
        GL20.glShaderSource(
            glShader,
            "hollowengine:shaders/core/gltf_skinning.vsh".rl.stream.readBytes().decodeToString()
        )
        GL20.glCompileShader(glShader)

        glProgramSkinning = GL20.glCreateProgram()
        GL20.glAttachShader(glProgramSkinning, glShader)
        GL20.glDeleteShader(glShader)
        GL30.glTransformFeedbackVaryings(
            glProgramSkinning, arrayOf<CharSequence>("outPosition", "outNormal", "outTangent"), GL30.GL_SEPARATE_ATTRIBS
        )
        GL20.glLinkProgram(glProgramSkinning)


        glShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER)
        GL20.glShaderSource(
            glShader,
            "hollowengine:shaders/core/gltf_morphing.vsh".rl.stream.readBytes().decodeToString()
        )
        GL20.glCompileShader(glShader)

        glProgramMorphing = GL20.glCreateProgram()
        GL20.glAttachShader(glProgramMorphing, glShader)
        GL20.glDeleteShader(glShader)
        GL30.glTransformFeedbackVaryings(
            glProgramMorphing, arrayOf<CharSequence>("outPosition", "outNormal", "outTangent"), GL30.GL_SEPARATE_ATTRIBS
        )
        GL20.glLinkProgram(glProgramMorphing)
    }

    override fun onResourceManagerReload(manager: ResourceManager) {
        models.values.forEach { it.value.destroy() }
        models.clear()

        val supportedFormats = loaders.flatMap { it.supportedFormats }.toSet()

        manager.listResources("models") { it.path.substringAfter('.') in supportedFormats }.keys
            .filter { manager.getResource(it.withSuffix(".hemeta")).isPresent }
            .forEach { location ->
                val flow = models.computeIfAbsent(location) { MutableStateFlow(AnimatedModel.EMPTY) }
                Minecraft.getInstance().coroutineScope.launch {
                    flow.emit(loadModel(location))
                }
            }
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
        textureManager.register("${HollowCore.MODID}:default_normal_map".rl, GlTexture(defaultNormalMap))
        textureManager.register("${HollowCore.MODID}:default_specular_map".rl, GlTexture(defaultSpecularMap))

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture)

        createSkinningProgramGL33()
    }

    fun supports(location: ResourceLocation): Boolean {
        val extension = location.path.substringAfter('.', "")

        return loaders.any { extension in it.supportedFormats }
    }

    val allModels get() = models.keys

}

interface ModelLoader {
    val supportedFormats: Set<String>

    suspend fun load(location: ResourceLocation, side: ModelSide = ModelSide.CLIENT): AnimatedModel
}

enum class ModelSide {
    CLIENT, SERVER
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