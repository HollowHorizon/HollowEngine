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
        UiAttribute("aspect-ratio"),
        UiAttribute("padding"),
        UiAttribute("margin"),
        UiAttribute("gap"),
        UiAttribute("align", "start start"),
        UiAttribute("align-items", "start start"),
        UiAttribute("grow"),
        UiAttribute("position"),
        UiAttribute("background"),
        UiAttribute("background-image", "image(\"\")"),
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
        UiAttribute("text-wrap"),
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
        "aspect-ratio",
        "padding",
        "margin",
        "gap",
        "align",
        "align-items",
        "grow",
        "position",
        "background",
        "background-image",
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
        "text-wrap",
        "wrap",
        "transition",
    )

    val valueCompletions = mapOf(
        "layout" to listOf("row", "column", "grid", "stack", "free"),
        "size" to listOf("fill fill", "fit fit", "100% 100%", "80% 80%", "64px 64px"),
        "width" to listOf("fill", "fit", "100%", "80%", "64px"),
        "height" to listOf("fill", "fit", "100%", "80%", "64px"),
        "min-size" to listOf("0px 0px", "64px 64px"),
        "max-size" to listOf("100% 100%", "auto auto"),
        "aspect-ratio" to listOf("1", "16/9", "4/3"),
        "padding" to listOf("8px", "8px 12px", "4px 8px 4px 8px"),
        "margin" to listOf("8px", "8px 12px", "4px 8px 4px 8px"),
        "gap" to listOf("8px", "12px"),
        "align" to alignValues(),
        "align-items" to alignValues(),
        "background" to listOf("#FFFFFF", "transparent", "image(\"\")", "linear-gradient(180deg, #000000, #FFFFFF)"),
        "background-image" to listOf("image(\"\")", "url(\"\")"),
        "foreground" to colorValues(),
        "color" to colorValues(),
        "border" to listOf("1px #FFFFFF", "2px rgba(255, 255, 255, 0.5)"),
        "border-radius" to listOf("4px", "8px", "50%"),
        "opacity" to listOf("1", "0.5", "0"),
        "rotate" to listOf("0", "10", "0 0 10"),
        "scale" to listOf("1", "1.05", "1 1"),
        "perspective" to listOf("300px", "0"),
        "filter" to listOf("none", "blur(4px)", "grayscale(1)"),
        "backdrop-filter" to listOf("none", "blur(8px)", "grayscale(1)"),
        "backface-visibility" to listOf("visible", "hidden"),
        "hoverable" to booleanValues(),
        "clickable" to booleanValues(),
        "focusable" to booleanValues(),
        "draggable" to booleanValues(),
        "scrollable" to booleanValues(),
        "clip" to booleanValues(),
        "image-fit" to imageFitValues(),
        "fit" to imageFitValues(),
        "text-wrap" to listOf("wrap", "nowrap"),
        "wrap" to listOf("wrap", "nowrap"),
    )

    fun attributesFor(element: String): List<UiAttribute> {
        val specific = elementAttributes[element.lowercase()].orEmpty()
        return (specific + globalAttributes).distinctBy { it.name }
    }

    fun valuesFor(property: String): List<String> = valueCompletions[property.lowercase()].orEmpty()

    private fun alignValues(): List<String> = listOf("start start", "center center", "end end", "start center", "center start")

    private fun colorValues(): List<String> = listOf("#FFFFFF", "#000000", "transparent", "white", "black", "rgba(255, 255, 255, 1)")

    private fun booleanValues(): List<String> = listOf("true", "false")

    private fun imageFitValues(): List<String> = listOf("stretch", "contain", "cover", "none")
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
