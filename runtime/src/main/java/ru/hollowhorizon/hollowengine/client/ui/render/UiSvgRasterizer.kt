package ru.hollowhorizon.hollowengine.client.ui.render

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.parser.LoaderContext
import com.github.weisj.jsvg.parser.SVGLoader
import com.github.weisj.jsvg.view.ViewBox
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import java.awt.Component
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

internal object UiSvgRasterizer {
    private val loader = SVGLoader()
    private val documents = ConcurrentHashMap<SvgDocumentKey, ParsedSvgDocument>()

    fun intrinsicSize(location: ResourceLocation, revision: Long): Pair<Float, Float> {
        return document(location, revision).intrinsicSize
    }

    fun rasterize(location: ResourceLocation, revision: Long, width: Int, height: Int): NativeImage {
        val document = document(location, revision).document
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            document.render(null as Component?, graphics, ViewBox(width.toFloat(), height.toFloat()))
        } finally {
            graphics.dispose()
        }

        val output = ByteArrayOutputStream(width * height * 4)
        ImageIO.write(image, "png", output)
        return NativeImage.read(ByteArrayInputStream(output.toByteArray()))
    }

    private fun document(location: ResourceLocation, revision: Long): ParsedSvgDocument {
        return documents.computeIfAbsent(SvgDocumentKey(location, revision)) {
            val source = HollowUiResourceAccess.readText(location)
            val input = ByteArrayInputStream(source.toByteArray(Charsets.UTF_8))
            val document = requireNotNull(loader.load(input, location.toSvgUri(), LoaderContext.createDefault())) {
                "Unable to parse SVG $location"
            }
            ParsedSvgDocument(document, document.intrinsicSize())
        }
    }
}

private data class SvgDocumentKey(
    val location: ResourceLocation,
    val revision: Long,
)

private data class ParsedSvgDocument(
    val document: SVGDocument,
    val intrinsicSize: Pair<Float, Float>,
)

private fun SVGDocument.intrinsicSize(): Pair<Float, Float> {
    val size = size()
    val viewBox = viewBox()
    val width = size.width.takeIf { it > 0f } ?: viewBox.width.takeIf { it > 0f } ?: 1f
    val height = size.height.takeIf { it > 0f } ?: viewBox.height.takeIf { it > 0f } ?: 1f
    return width to height
}

private fun ResourceLocation.toSvgUri(): URI {
    return URI.create("hollowengine-svg://${namespace}/${path}")
}
