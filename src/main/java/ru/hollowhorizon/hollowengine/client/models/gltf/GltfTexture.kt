package ru.hollowhorizon.hollowengine.client.models.gltf

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*

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

    suspend fun makeTexture(location: ResourceLocation): ResourceLocation {
        val uri = imageRef.uri
        val name = if (uri != null && !uri.startsWith("data:", true)) {
            uri
        } else {
            val folderPath = location.path.substringBefore(".")
            "${location.namespace}:$folderPath/unnamed_texture_$source"
        }

        if (!this::createdTex.isInitialized) {
            withContext(Dispatchers.IO) {
                RenderSystem.recordRenderCall {
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

                    Minecraft.getInstance().textureManager.register(name.lowercase().rl, createdTex)
                }
            }
        }
        return name.lowercase().rl
    }

    @Serializable
    data class Info(
        val index: Int,
        val strength: Float = 1f,
        val texCoord: Int = 0,
        val scale: Float = 1f,
    ) {
        suspend fun getTexture(gltfFile: GltfFile, location: ResourceLocation): ResourceLocation {
            return gltfFile.textures[index].makeTexture(location)
        }
    }
}

