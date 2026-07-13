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
    return filter.elementChildren().mapNotNull(::parseSvgFilterEffect)
}

private fun parseSvgFilterEffect(element: Element): UiSvgFilterEffect? {
    return when (element.svgName()) {
        "fegaussianblur" -> {
            val deviation = parseSvgNumbers(element.getAttribute("stdDeviation"))
                .maxOrNull()
                ?.coerceAtLeast(0f)
                ?: 0f
            UiSvgFilterEffect.GaussianBlur(deviation)
        }

        "fedropshadow" -> {
            val deviation = element.svgLength("stdDeviation")?.coerceAtLeast(0f) ?: 0f
            val opacity = element.svgLength("flood-opacity")?.coerceIn(0f, 1f) ?: 1f
            val color = parseSvgColor(
                element.getAttribute("flood-color").ifBlank { "black" },
            )?.let { it.copy(alpha = it.alpha * opacity) } ?: UiColor.Black.copy(alpha = opacity)
            UiSvgFilterEffect.DropShadow(
                offsetX = element.svgLength("dx") ?: 2f,
                offsetY = element.svgLength("dy") ?: 2f,
                standardDeviation = deviation,
                color = color,
            )
        }

        else -> null
    }
}

private fun Element.elementChildren(): List<Element> {
    val result = mutableListOf<Element>()
    var child = firstChild
    while (child != null) {
        if (child.nodeType == Node.ELEMENT_NODE) result += child as Element
        child = child.nextSibling
    }
    return result
}
