package ru.hollowhorizon.hollowengine.client.ui.text

import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.platform.TextureUtil
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.utils.font.MsdfBakeSpec
import ru.hollowhorizon.hollowengine.client.utils.font.MsdfBakerVersion
import ru.hollowhorizon.hollowengine.client.utils.font.MsdfMeta
import ru.hollowhorizon.hollowengine.client.utils.font.bakeMsdfAtlas
import ru.hollowhorizon.hollowengine.client.utils.font.TtfFace
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.json.json
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.io.path.exists

/**
 * TrueType/OpenType fonts.
 */
object UiTtfFont {
    const val FamilyPrefix = "ttf"

    private val states = ConcurrentHashMap<String, TtfState>()
    private val baker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "HollowEngine MSDF font baker").apply { isDaemon = true }
    }

    fun isTtfFamily(family: String): Boolean = family.startsWith("$FamilyPrefix:")

    /** Metrics for measurement, or null while the face is still baking (or if it failed). */
    fun metrics(family: String): UiMsdfFontMetrics? = when (val state = request(family)) {
        is TtfState.Baked -> state.metrics
        is TtfState.Uploaded -> state.data.metrics
        else -> null
    }

    /** The render-side font; uploads the atlas on first use. Render thread only. */
    fun glyphFont(family: String): UiGlyphFont? {
        return when (val state = request(family)) {
            is TtfState.Uploaded -> state.data
            is TtfState.Baked -> upload(family, state)
            else -> null
        }
    }

    fun unloadAll() {
        states.values.forEach { state ->
            when (state) {
                is TtfState.Baked -> state.image.close()
                is TtfState.Uploaded -> GL11.glDeleteTextures(state.data.textureId)
                else -> Unit
            }
        }
        states.clear()
    }

    private fun request(family: String): TtfState? {
        states[family]?.let { return it }
        if (!isTtfFamily(family)) return null
        val request = UiTtfFontRequest.parse(family) ?: run {
            HollowEngine.LOGGER.warn("Malformed TrueType font family '{}'", family)
            states[family] = TtfState.Failed
            return TtfState.Failed
        }
        if (states.putIfAbsent(family, TtfState.Pending) != null) return states[family]
        baker.submit { states[family] = bake(family, request) }
        return TtfState.Pending
    }

    private fun bake(family: String, request: UiTtfFontRequest): TtfState = runCatching {
        val bytes = request.readBytes()
        val spec = request.bakeSpec()
        val key = cacheKey(request, bytes, spec)
        readCache(key)?.let { return@runCatching it }
        val started = System.nanoTime()
        val atlas = TtfFace.open(bytes).use { face -> bakeMsdfAtlas(face, spec) }
        val image = NativeImage(atlas.width, atlas.height, false)
        for (y in 0 until atlas.height) {
            for (x in 0 until atlas.width) {
                val offset = (y * atlas.width + x) * 3
                image.setPixelRGBA(x, y, packAbgr(atlas.pixels, offset))
            }
        }
        writeCache(key, atlas.meta, image)
        HollowEngine.LOGGER.info(
            "Baked {} into a {}x{} MSDF atlas ({} glyphs) in {} ms",
            family, atlas.width, atlas.height, atlas.meta.glyphs.size, (System.nanoTime() - started) / 1_000_000,
        )
        TtfState.Baked(atlas.meta, image)
    }.getOrElse { throwable ->
        HollowEngine.LOGGER.error("Could not bake TrueType font '$family'", throwable)
        TtfState.Failed
    }

    private fun upload(family: String, baked: TtfState.Baked): UiGlyphFont? {
        return runCatching {
            val image = baked.image
            val textureId = GL11.glGenTextures()
            RenderSystem.bindTexture(textureId)
            TextureUtil.prepareImage(textureId, image.width, image.height)
            image.upload(0, 0, 0, 0, 0, image.width, image.height, true, true, false, false)
            image.close()
            val data = UiMsdfFontData(baked.metrics, textureId)
            states[family] = TtfState.Uploaded(data)
            data
        }.onFailure {
            HollowEngine.LOGGER.error("Could not upload the MSDF atlas for '$family'", it)
            states[family] = TtfState.Failed
        }.getOrNull()
    }

    private fun packAbgr(pixels: ByteArray, offset: Int): Int {
        val red = pixels[offset].toInt() and 0xFF
        val green = pixels[offset + 1].toInt() and 0xFF
        val blue = pixels[offset + 2].toInt() and 0xFF
        return (0xFF shl 24) or (blue shl 16) or (green shl 8) or red
    }

    private fun cacheKey(request: UiTtfFontRequest, bytes: ByteArray, spec: MsdfBakeSpec): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(MsdfBakerVersion.toString().toByteArray())
        digest.update(request.source.toByteArray())
        digest.update(spec.pixelSize.toRawBits().toString().toByteArray())
        digest.update(spec.pixelRange.toRawBits().toString().toByteArray())
        digest.update(spec.codepoints.size.toString().toByteArray())
        digest.update(spec.codepoints.firstOrNull().toString().toByteArray())
        digest.update(spec.codepoints.lastOrNull().toString().toByteArray())
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun cacheDirectory(): Path =
        Minecraft.getInstance().gameDirectory.toPath().resolve("hollowengine/cache/fonts")

    private fun readCache(key: String): TtfState.Baked? = runCatching {
        val directory = cacheDirectory()
        val metaPath = directory.resolve("$key.json")
        val imagePath = directory.resolve("$key.png")
        if (!metaPath.exists() || !imagePath.exists()) return null
        val meta: MsdfMeta = JsonFormat.decodeFromString(Files.readString(metaPath))
        val image = Files.newInputStream(imagePath).use { NativeImage.read(it) }
        TtfState.Baked(meta, image)
    }.getOrNull()

    private fun writeCache(key: String, meta: MsdfMeta, image: NativeImage) {
        runCatching {
            val directory = cacheDirectory()
            Files.createDirectories(directory)
            Files.writeString(directory.resolve("$key.json"), json.encodeToString(MsdfMeta.serializer(), meta))
            image.writeToFile(directory.resolve("$key.png"))
        }.onFailure {
            HollowEngine.LOGGER.warn("Could not cache the baked MSDF atlas: {}", it.message)
        }
    }

    private sealed interface TtfState {
        data object Pending : TtfState
        data object Failed : TtfState

        /** Baked but not yet on the GPU; [image] is owned here and closed by the upload. */
        class Baked(meta: MsdfMeta, val image: NativeImage) : TtfState {
            val metrics = UiMsdfFontMetrics(meta, meta.glyphs.associateBy { it.unicode.toChar() })
        }

        class Uploaded(val data: UiMsdfFontData) : TtfState
    }
}

/** A parsed `ttf:...` family: where the face comes from and how to bake it. */
internal class UiTtfFontRequest(
    val source: String,
    private val location: ResourceLocation?,
    private val filePath: String?,
    private val pixelSize: Float,
    private val pixelRange: Float,
    private val codepoints: IntArray,
) {
    fun bakeSpec() = MsdfBakeSpec(pixelSize, pixelRange, codepoints)

    fun readBytes(): ByteArray = when {
        location != null -> Minecraft.getInstance().resourceManager.getResourceOrThrow(location)
            .open().use { it.readBytes() }

        filePath != null -> Files.readAllBytes(
            Minecraft.getInstance().gameDirectory.toPath().resolve(filePath).normalize()
        )

        else -> error("no font source")
    }

    companion object {
        private const val DefaultPixelSize = 48f
        private const val DefaultPixelRange = 2f
        private const val DefaultCharset = "latin+latin-ext+cyrillic+punctuation"
        private const val FilePrefix = "file:"

        fun parse(family: String): UiTtfFontRequest? {
            val body = family.removePrefix("${UiTtfFont.FamilyPrefix}:")
            val source = body.substringBefore('?').trim()
            if (source.isEmpty()) return null
            val options = body.substringAfter('?', "").split('&')
                .mapNotNull { option ->
                    val name = option.substringBefore('=').trim().lowercase()
                    if (name.isEmpty()) null else name to option.substringAfter('=', "").trim()
                }
                .toMap()

            val isFile = source.startsWith(FilePrefix)
            val location = if (isFile) null else ResourceLocation.tryParse(source) ?: return null
            return UiTtfFontRequest(
                source = source,
                location = location,
                filePath = source.removePrefix(FilePrefix).takeIf { isFile },
                pixelSize = options["size"]?.toFloatOrNull()?.coerceIn(8f, 256f) ?: DefaultPixelSize,
                pixelRange = options["range"]?.toFloatOrNull()?.coerceIn(1f, 32f) ?: DefaultPixelRange,
                codepoints = parseCharset(options["charset"] ?: DefaultCharset),
            )
        }

        /**
         * `+`-separated preset names and explicit `U+XXXX` / `U+XXXX-U+YYYY` ranges. `+` rather than
         * `,` keeps the whole family usable as a single HSS argument.
         */
        private fun parseCharset(specification: String): IntArray {
            val codepoints = sortedSetOf<Int>()
            for (token in specification.split('+', ' ').map { it.trim().lowercase() }) {
                if (token.isEmpty()) continue
                when (token) {
                    "ascii" -> codepoints.addRange(0x20, 0x7E)
                    "latin" -> {
                        codepoints.addRange(0x20, 0x7E)
                        codepoints.addRange(0xA0, 0xFF)
                    }

                    "latin-ext" -> codepoints.addRange(0x100, 0x17F)
                    "cyrillic" -> {
                        codepoints.addRange(0x400, 0x45F)
                        codepoints.addRange(0x490, 0x4FF)
                    }

                    "greek" -> codepoints.addRange(0x370, 0x3FF)
                    "punctuation" -> {
                        codepoints.addRange(0x2010, 0x2027)
                        codepoints.addRange(0x2030, 0x205E)
                        codepoints += listOf(0x20AC, 0x2116, 0x2122, 0x2190, 0x2192, 0xFFFD)
                    }

                    else -> parseCodepointToken(token)?.let { (first, last) -> codepoints.addRange(first, last) }
                }
            }
            if (codepoints.isEmpty()) codepoints.addRange(0x20, 0x7E)
            return codepoints.toIntArray()
        }

        /** `u+41`, or `u+400-u+4ff` for a range. Malformed tokens are dropped rather than fatal. */
        private fun parseCodepointToken(token: String): Pair<Int, Int>? {
            val parts = token.split('-')
            val first = parts.getOrNull(0)?.removePrefix("u+")?.toIntOrNull(16) ?: return null
            val last = parts.getOrNull(1)?.removePrefix("u+")?.toIntOrNull(16) ?: first
            if (last < first || last > Char.MAX_VALUE.code) return null
            return first to last
        }

        private fun MutableSet<Int>.addRange(first: Int, last: Int) {
            for (codepoint in first..last) add(codepoint)
        }
    }
}
