package ru.hollowhorizon.hollowengine.client.models.gltf

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.utils.math.Vec2f
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@Serializable
data class GltfTexture(
    val sampler: Int = -1,
    val source: Int = 0,
    val name: String? = null,
) {
    @Transient
    lateinit var imageRef: GltfImage

    @Transient
    var samplerRef: GltfSampler? = null

    @Transient
    private lateinit var createdTex: DynamicTexture
    @Transient
    private var isRegistered = false

    fun makeTexture(location: ResourceLocation): ResourceLocation {
        val uri = imageRef.uri
        val name = if (uri != null && !uri.startsWith("data:", true)) {
            uri
        } else {
            val folderPath = location.path.substringBefore(".")
            "${location.namespace}:$folderPath/unnamed_texture_$source"
        }

        if (!this::createdTex.isInitialized) {
            if (uri != null && imageRef.bufferViewRef == null) {
                fun retrieveFile(path: String): InputStream {
                    if (path.startsWith("data:application/octet-stream;base64,")) {
                        return Base64.getDecoder().wrap(path.substring(37).byteInputStream())
                    }
                    if (path.startsWith("data:image/png;base64,")) {
                        return Base64.getDecoder().wrap(path.substring(22).byteInputStream())
                    }

                    return path.rl.stream
                }

                createdTex = DynamicTexture(NativeImage.read(retrieveFile(uri)))
            } else {
                createdTex = DynamicTexture(
                    NativeImage.read(
                        ByteArrayInputStream(
                            imageRef.bufferViewRef!!.getData().toArray()
                        )
                    )
                )
            }
        }

        val textureId = name.lowercase().rl
        if (!isRegistered) {
            isRegistered = true
            if (RenderSystem.isOnRenderThreadOrInit()) {
                Minecraft.getInstance().textureManager.register(textureId, createdTex)
            } else {
                RenderSystem.recordRenderCall {
                    Minecraft.getInstance().textureManager.register(textureId, createdTex)
                }
            }
        }

        return textureId
    }

    @Serializable
    data class Info(
        val index: Int,
        val strength: Float = 1f,
        val texCoord: Int = 0,
        val scale: Float = 1f,
        val extensions: Extensions? = null,
    ) {
        val transformTexCoord: Int
            get() = extensions?.textureTransform?.texCoord?.takeIf { it >= 0 } ?: texCoord

        val transform: TextureTransform? get() = extensions?.textureTransform?.takeUnless { it.isIdentity }

        fun getTexture(gltfFile: GltfFile, location: ResourceLocation): ResourceLocation {
            return gltfFile.textures[index].makeTexture(location)
        }
    }

    @Serializable
    data class Extensions(
        @SerialName("KHR_texture_transform") val textureTransform: TextureTransform? = null,
    )

    /**
     * `KHR_texture_transform`: an affine transform of the UVs, written as translate * rotate * scale.
     */
    @Serializable
    data class TextureTransform(
        val offset: List<Float> = listOf(0f, 0f),
        val rotation: Float = 0f,
        val scale: List<Float> = listOf(1f, 1f),
        val texCoord: Int = -1,
    ) {
        private val offsetX get() = offset.getOrElse(0) { 0f }
        private val offsetY get() = offset.getOrElse(1) { 0f }
        private val scaleX get() = scale.getOrElse(0) { 1f }
        private val scaleY get() = scale.getOrElse(1) { 1f }

        val isIdentity: Boolean
            get() = offsetX == 0f && offsetY == 0f && rotation == 0f && scaleX == 1f && scaleY == 1f

        fun apply(u: Float, v: Float): Vec2f {
            val cos = cos(rotation)
            val sin = sin(rotation)
            return Vec2f(
                offsetX + cos * scaleX * u + sin * scaleY * v,
                offsetY - sin * scaleX * u + cos * scaleY * v,
            )
        }
    }
}

