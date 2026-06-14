package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.resources.ResourceLocation
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

data class UiSvgPathDocument(
    val path: UiPath,
    val viewBox: UiRect,
)

data class SvgResourceShape(
    val location: ResourceLocation,
) : Shape {
    override fun createPath(size: UiShapeSize): UiPath {
        val document = UiSvgResourceLoader.load(location)
        return SvgPathShape(document.path, document.viewBox).createPath(size)
    }
}

object UiSvgResourceLoader {
    private val cache = mutableMapOf<ResourceLocation, CachedSvgDocument>()

    fun load(location: ResourceLocation): UiSvgPathDocument {
        val version = HollowUiResourceAccess.version(location)
        val cached = cache[location]
        if (cached != null && cached.version == version) return cached.document

        val document = SvgFileParser.parse(HollowUiResourceAccess.readText(location))
        cache[location] = CachedSvgDocument(version, document)
        return document
    }

    private data class CachedSvgDocument(
        val version: Long,
        val document: UiSvgPathDocument,
    )
}

object SvgFileParser {
    private val separator = Regex("[,\\s]+")
    private val ignoredSubtrees = setOf(
        "defs",
        "desc",
        "metadata",
        "style",
        "title",
        "lineargradient",
        "radialgradient",
        "stop",
        "clippath",
        "mask",
    )
    private val unsupportedElements = setOf(
        "foreignobject",
        "image",
        "text",
        "use",
    )

    fun parse(source: String): UiSvgPathDocument {
        val root = parseRoot(source)
        require(root.svgName() == "svg") { "Expected <svg> root, got <${root.tagName}>" }

        val paths = mutableListOf<UiPath>()
        collectPaths(root, hasTransform = false, paths)
        require(paths.isNotEmpty()) { "SVG file does not contain path data" }

        return UiSvgPathDocument(
            path = UiPath(paths.flatMap { it.commands }),
            viewBox = parseViewBox(root),
        )
    }

    private fun parseRoot(source: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            disableFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            disableFeature("http://xml.org/sax/features/external-general-entities", false)
            disableFeature("http://xml.org/sax/features/external-parameter-entities", false)
            disableFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        return factory.newDocumentBuilder()
            .parse(InputSource(StringReader(source)))
            .documentElement
    }

    private fun DocumentBuilderFactory.disableFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    private fun collectPaths(element: Element, hasTransform: Boolean, paths: MutableList<UiPath>) {
        val name = element.svgName()
        if (name in ignoredSubtrees) return
        require(name !in unsupportedElements) {
            "Unsupported SVG element <$name>. Convert it to <path> before using it as Shape."
        }

        val transformed = hasTransform || element.hasNonEmptyAttribute("transform")
        when (name) {
            "path" -> {
                require(!transformed) { "SVG path transforms are not supported. Apply transforms before export." }
                val data = element.getAttribute("d").trim()
                require(data.isNotEmpty()) { "SVG <path> element requires non-empty d attribute" }
                paths += SvgPathParser.parse(data)
                return
            }

            "rect", "circle", "ellipse", "line", "polyline", "polygon" -> {
                require(!transformed) { "SVG primitive transforms are not supported. Apply transforms before export." }
                paths += primitivePath(name, element)
                return
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectPaths(child as Element, transformed, paths)
            }
            child = child.nextSibling
        }
    }

    private fun primitivePath(name: String, element: Element): UiPath {
        return when (name) {
            "rect" -> rectPath(element)
            "circle" -> circlePath(element)
            "ellipse" -> ellipsePath(element)
            "line" -> linePath(element)
            "polyline" -> pointsPath(element, close = false)
            "polygon" -> pointsPath(element, close = true)
            else -> UiPath.Empty
        }
    }

    private fun rectPath(element: Element): UiPath {
        val x = element.svgLength("x") ?: 0f
        val y = element.svgLength("y") ?: 0f
        val width = element.svgLength("width") ?: 0f
        val height = element.svgLength("height") ?: 0f
        val rx = (element.svgLength("rx") ?: element.svgLength("ry") ?: 0f).coerceIn(0f, width * 0.5f)
        val ry = (element.svgLength("ry") ?: element.svgLength("rx") ?: 0f).coerceIn(0f, height * 0.5f)
        return path {
            if (rx <= 0f || ry <= 0f) {
                moveTo(x, y)
                lineTo(x + width, y)
                lineTo(x + width, y + height)
                lineTo(x, y + height)
                close()
            } else {
                moveTo(x + rx, y)
                lineTo(x + width - rx, y)
                ellipticalArcTo(rx, ry, 0f, largeArc = false, sweep = true, x + width, y + ry)
                lineTo(x + width, y + height - ry)
                ellipticalArcTo(rx, ry, 0f, largeArc = false, sweep = true, x + width - rx, y + height)
                lineTo(x + rx, y + height)
                ellipticalArcTo(rx, ry, 0f, largeArc = false, sweep = true, x, y + height - ry)
                lineTo(x, y + ry)
                ellipticalArcTo(rx, ry, 0f, largeArc = false, sweep = true, x + rx, y)
                close()
            }
        }
    }

    private fun circlePath(element: Element): UiPath {
        val centerX = element.svgLength("cx") ?: 0f
        val centerY = element.svgLength("cy") ?: 0f
        val radius = element.svgLength("r") ?: 0f
        return ellipsePath(centerX, centerY, radius, radius)
    }

    private fun ellipsePath(element: Element): UiPath {
        val centerX = element.svgLength("cx") ?: 0f
        val centerY = element.svgLength("cy") ?: 0f
        val radiusX = element.svgLength("rx") ?: 0f
        val radiusY = element.svgLength("ry") ?: 0f
        return ellipsePath(centerX, centerY, radiusX, radiusY)
    }

    private fun ellipsePath(centerX: Float, centerY: Float, radiusX: Float, radiusY: Float): UiPath {
        return path {
            moveTo(centerX + radiusX, centerY)
            ellipticalArcTo(radiusX, radiusY, 0f, largeArc = false, sweep = true, centerX - radiusX, centerY)
            ellipticalArcTo(radiusX, radiusY, 0f, largeArc = false, sweep = true, centerX + radiusX, centerY)
            close()
        }
    }

    private fun linePath(element: Element): UiPath {
        val x1 = element.svgLength("x1") ?: 0f
        val y1 = element.svgLength("y1") ?: 0f
        val x2 = element.svgLength("x2") ?: 0f
        val y2 = element.svgLength("y2") ?: 0f
        return path {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
    }

    private fun pointsPath(element: Element, close: Boolean): UiPath {
        val points = element.getAttribute("points")
            .split(separator)
            .filter(String::isNotEmpty)
            .map { it.toFloat() }
        require(points.size >= 4 && points.size % 2 == 0) { "SVG points attribute expects x/y pairs" }
        return path {
            moveTo(points[0], points[1])
            var index = 2
            while (index < points.size) {
                lineTo(points[index], points[index + 1])
                index += 2
            }
            if (close) close()
        }
    }

    private fun parseViewBox(root: Element): UiRect {
        val viewBox = root.getAttribute("viewBox").trim()
        if (viewBox.isNotEmpty()) {
            val numbers = viewBox.split(separator)
                .filter(String::isNotEmpty)
                .map { it.toFloat() }
            require(numbers.size == 4) { "SVG viewBox expects four numbers" }
            return UiRect(numbers[0], numbers[1], numbers[2], numbers[3])
        }

        val width = root.svgLength("width")
        val height = root.svgLength("height")
        require(width != null && height != null) { "SVG file requires viewBox or numeric width and height" }
        return UiRect(0f, 0f, width, height)
    }

    private fun Element.svgLength(attribute: String): Float? {
        val value = getAttribute(attribute).trim()
        if (value.isEmpty()) return null
        return value.removeSuffix("px").toFloatOrNull()
    }

    private fun Element.hasNonEmptyAttribute(name: String): Boolean {
        return hasAttribute(name) && getAttribute(name).isNotBlank()
    }

    private fun Element.svgName(): String {
        return (localName ?: tagName).substringAfter(':').lowercase()
    }
}

fun svgResource(location: ResourceLocation): Shape = SvgResourceShape(location)

fun svgResource(location: String): Shape = SvgResourceShape(parseSvgResourceLocation(location))

private fun parseSvgResourceLocation(location: String): ResourceLocation {
    val trimmed = location.trim()
    return ResourceLocation.parse(if (trimmed.contains(":")) trimmed else "hollowengine:$trimmed")
}
