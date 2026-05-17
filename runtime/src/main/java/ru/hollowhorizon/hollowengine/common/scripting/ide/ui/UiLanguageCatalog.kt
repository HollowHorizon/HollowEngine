package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

object UiLanguageCatalog {
    val elementTypes = listOf("box", "text", "image", "item", "entity", "canvas", "button")

    val states = listOf("hover", "active", "focus", "disabled", "selected", "dragging")

    val globalAttributes = listOf(
        UiAttribute("id"),
        UiAttribute("tags"),
        UiAttribute("class"),
        UiAttribute("style"),
        UiAttribute("layout"),
        UiAttribute("size"),
        UiAttribute("width"),
        UiAttribute("height"),
        UiAttribute("min-size"),
        UiAttribute("max-size"),
        UiAttribute("padding"),
        UiAttribute("margin"),
        UiAttribute("gap"),
        UiAttribute("align"),
        UiAttribute("align-self"),
        UiAttribute("justify"),
        UiAttribute("justify-self"),
        UiAttribute("grow"),
        UiAttribute("position"),
        UiAttribute("background"),
        UiAttribute("foreground"),
        UiAttribute("color"),
        UiAttribute("border"),
        UiAttribute("border-radius"),
        UiAttribute("shadow"),
        UiAttribute("box-shadow"),
        UiAttribute("opacity"),
        UiAttribute("translate"),
        UiAttribute("rotate"),
        UiAttribute("scale"),
        UiAttribute("perspective"),
        UiAttribute("filter"),
        UiAttribute("backdrop-filter"),
        UiAttribute("backface-visibility"),
        UiAttribute("hoverable", "true"),
        UiAttribute("clickable", "true"),
        UiAttribute("focusable", "true"),
        UiAttribute("draggable", "true"),
        UiAttribute("scrollable", "true"),
        UiAttribute("clip", "true"),
        UiAttribute("layer"),
        UiAttribute("transition"),
        UiAttribute("onClick", "{event:\"\"}"),
        UiAttribute("onDrag", "{event:\"\"}"),
    )

    val elementAttributes = mapOf(
        "import" to listOf(UiAttribute("element"), UiAttribute("named")),
        "text" to listOf(UiAttribute("value")),
        "image" to listOf(UiAttribute("source"), UiAttribute("src"), UiAttribute("image"), UiAttribute("image-fit")),
        "item" to listOf(UiAttribute("item"), UiAttribute("value")),
        "entity" to listOf(UiAttribute("entity"), UiAttribute("value")),
        "canvas" to listOf(UiAttribute("renderer")),
        "button" to listOf(UiAttribute("value")),
    )

    val hssProperties = listOf(
        "layout",
        "size",
        "width",
        "height",
        "min-size",
        "max-size",
        "padding",
        "margin",
        "gap",
        "align",
        "align-self",
        "justify-self",
        "justify",
        "grow",
        "position",
        "background",
        "foreground",
        "color",
        "image",
        "shader",
        "border",
        "border-radius",
        "shadow",
        "box-shadow",
        "opacity",
        "translate",
        "rotate",
        "scale",
        "perspective",
        "filter",
        "backdrop-filter",
        "backface-visibility",
        "hoverable",
        "clickable",
        "focusable",
        "draggable",
        "scrollable",
        "clip",
        "layer",
        "image-fit",
        "fit",
        "transition",
    )

    fun attributesFor(element: String): List<UiAttribute> {
        val specific = elementAttributes[element.lowercase()].orEmpty()
        return (specific + globalAttributes).distinctBy { it.name }
    }
}

data class UiAttribute(
    val name: String,
    val defaultValue: String? = "",
) {
    val insertion: String
        get() = if (defaultValue == null) name else "$name=\"$defaultValue\""

    val caretMove: Int
        get() = if (defaultValue == null) 0 else -1
}
