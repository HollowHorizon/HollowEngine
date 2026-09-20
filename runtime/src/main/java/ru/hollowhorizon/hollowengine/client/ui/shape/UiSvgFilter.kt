package ru.hollowhorizon.hollowengine.client.ui.shape

import org.w3c.dom.Element
import org.w3c.dom.Node
import ru.hollowhorizon.hollowengine.client.ui.UiColor

internal fun parseSvgFilterEffects(
    filterValue: String?,
    elementById: (String) -> Element?,
): List<UiSvgFilterEffect> {
    val reference = parseUrlReference(filterValue) ?: return emptyList()
    val id = reference.substringAfterLast('#').takeIf(String::isNotEmpty) ?: return emptyList()
    val filter = elementById(id) ?: return emptyList()
    return readFilterChain(filter.elementChildren())
}

/**
 * Reads a filter's primitives as the one or two effects the engine can draw.
 */
private fun readFilterChain(primitives: List<Element>): List<UiSvgFilterEffect> {
    val effects = mutableListOf<UiSvgFilterEffect>()
    var run: FilterRun? = null

    fun flush() {
        run?.toEffect()?.let(effects::add)
        run = null
    }

    primitives.forEach { element ->
        val input = element.getAttribute("in").trim()
        if (input.equals("SourceGraphic", ignoreCase = true)) flush()
        if (input.equals("SourceAlpha", ignoreCase = true)) {
            flush()
            run = FilterRun(onAlpha = true)
        }

        when (element.svgName()) {
            "fedropshadow" -> {
                flush()
                effects += element.readDropShadow()
            }

            "feoffset" -> {
                val current = run ?: FilterRun(onAlpha = false).also { run = it }
                current.offsetX = element.svgLength("dx") ?: 0f
                current.offsetY = element.svgLength("dy") ?: 0f
            }

            "fegaussianblur" -> {
                val current = run ?: FilterRun(onAlpha = false).also { run = it }
                current.deviation = parseSvgNumbers(element.getAttribute("stdDeviation"))
                    .maxOrNull()?.coerceAtLeast(0f) ?: 0f
            }

            "fecolormatrix" -> run?.takeIf { it.onAlpha }?.let { current ->
                element.readColorMatrix()?.let { current.color = it }
            }

            "feblend", "fecomposite", "femerge" -> flush()

            else -> Unit
        }
    }
    flush()
    return effects
}

private class FilterRun(val onAlpha: Boolean) {
    var offsetX = 0f
    var offsetY = 0f
    var deviation = 0f
    var color: UiColor? = null

    fun toEffect(): UiSvgFilterEffect? = when {
        onAlpha -> UiSvgFilterEffect.DropShadow(
            offsetX = offsetX,
            offsetY = offsetY,
            standardDeviation = deviation,
            color = color ?: UiColor.Black,
        ).takeIf { deviation > 0f || offsetX != 0f || offsetY != 0f }

        deviation > 0f -> UiSvgFilterEffect.GaussianBlur(deviation)
        else -> null
    }
}

private fun Element.readDropShadow(): UiSvgFilterEffect.DropShadow {
    val deviation = svgLength("stdDeviation")?.coerceAtLeast(0f) ?: 0f
    val opacity = svgLength("flood-opacity")?.coerceIn(0f, 1f) ?: 1f
    val color = parseSvgColor(getAttribute("flood-color").ifBlank { "black" })
        ?.let { it.copy(alpha = it.alpha * opacity) }
        ?: UiColor.Black.copy(alpha = opacity)
    return UiSvgFilterEffect.DropShadow(
        offsetX = svgLength("dx") ?: 2f,
        offsetY = svgLength("dy") ?: 2f,
        standardDeviation = deviation,
        color = color,
    )
}

/**
 * The flat color a 4x5 color matrix paints its input. Only the constant column and the alpha
 * coefficient are read, which is all a tint of a silhouette uses.
 */
private fun Element.readColorMatrix(): UiColor? {
    if (!getAttribute("type").trim().let { it.isEmpty() || it.equals("matrix", ignoreCase = true) }) return null
    val values = parseSvgNumbers(getAttribute("values"))
    if (values.size < 20) return null
    val alpha = values[18]
    if (alpha <= 0f) return null
    return UiColor(values[4], values[9], values[14], alpha.coerceIn(0f, 1f))
}

internal fun Element.elementChildren(): List<Element> {
    val result = mutableListOf<Element>()
    var child = firstChild
    while (child != null) {
        if (child.nodeType == Node.ELEMENT_NODE) result += child as Element
        child = child.nextSibling
    }
    return result
}
