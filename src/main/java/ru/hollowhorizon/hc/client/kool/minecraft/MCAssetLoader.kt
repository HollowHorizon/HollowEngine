package ru.hollowhorizon.hc.client.kool.minecraft

import de.fabmax.kool.*
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.audio.AudioClipImpl
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.platform.HttpCache
import de.fabmax.kool.platform.imageAtlasTextureData
import de.fabmax.kool.util.Uint8BufferImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.stream
import ru.hollowhorizon.hc.common.utils.rl
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.util.*

/**
 * Resource Location adapter for Kool Asset Loader.
 * Allows to use resource paths like resource packs: `minecraft:textures/block/dirt.png`
 */
object MCAssetLoader : AssetLoader() {
    private fun resource(path: String) = if (path.contains(":")) path.rl
    else "hollowcore:$path".rl

    override suspend fun loadBlob(ref: AssetRef.Blob): LoadedAsset.Blob {
        val result = withContext(Dispatchers.IO) {
            try {
                val data = openStream(ref).use { Uint8BufferImpl(it.readBytes()) }
                Result.success(data)
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }
        return LoadedAsset.Blob(ref, result)
    }

    override suspend fun loadAudio(ref: AssetRef.Audio): LoadedAsset.Audio {
        val blob = loadBlob(AssetRef.Blob(ref.path))
        return LoadedAsset.Audio(ref, blob.result.map {
            AudioClipImpl(it.toArray(), ref.path.substringAfterLast('.').lowercase())
        })
    }

    override suspend fun loadBufferedImage2d(ref: AssetRef.BufferedImage2d): LoadedAsset.BufferedImage2d {
        val data: Result<BufferedImageData2d> = withContext(Dispatchers.IO) {
            loadTexture(ref, ref.format, ref.resolveSize)
        }
        return LoadedAsset.BufferedImage2d(ref, data)
    }

    override suspend fun loadImage2d(ref: AssetRef.Image2d): LoadedAsset.Image2d {
        val refCopy = AssetRef.BufferedImage2d(ref.path, ref.format, ref.resolveSize)
        return LoadedAsset.Image2d(ref, loadBufferedImage2d(refCopy).result)
    }

    override suspend fun loadImageAtlas(ref: AssetRef.ImageAtlas): LoadedAsset.ImageAtlas {
        val refCopy = AssetRef.BufferedImage2d(ref.path, ref.format, ref.resolveSize)
        val result = loadBufferedImage2d(refCopy).result.mapCatching {
            imageAtlasTextureData(it, ref.tilesX, ref.tilesY)
        }
        return LoadedAsset.ImageAtlas(ref, result)
    }

    private fun loadTexture(assetRef: AssetRef, format: TexFormat, resolveSize: Vec2i?): Result<BufferedImageData2d> {
        return try {
            openStream(assetRef).use {
                Result.success(
                    PlatformAssetsImpl.readImageData(
                        it,
                        MimeType.forFileName(assetRef.path),
                        format,
                        resolveSize
                    )
                )
            }
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun openStream(assetRef: AssetRef): InputStream =
        if (assetRef.isHttp) openHttpStream(assetRef) else openLocalStream(assetRef)

    private fun openLocalStream(assetRef: AssetRef) = resource(assetRef.path).stream

    private fun openHttpStream(assetRef: AssetRef) =
        if (assetRef.path.startsWith("data:", true)) {
            ByteArrayInputStream(dataUriToByteArray(assetRef.path))
        } else {
            HttpCache.loadHttpResource(assetRef.path)?.let { f -> FileInputStream(f) }
                ?: throw FileNotFoundException("Failed loading HTTP asset: ${assetRef.path}")
        }


    private fun dataUriToByteArray(dataUri: String): ByteArray {
        val dataIdx = dataUri.indexOf(";base64,") + 8
        return Base64.getDecoder().decode(dataUri.substring(dataIdx))
    }
}