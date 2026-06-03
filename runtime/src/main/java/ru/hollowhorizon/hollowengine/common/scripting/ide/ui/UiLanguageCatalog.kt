package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

object UiLanguageCatalog {
    val elementTypes = listOf("box", "text", "image", "item", "entity", "canvas")

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
        UiAttribute("minSize"),
        UiAttribute("maxSize"),
        UiAttribute("aspectRatio"),
        UiAttribute("padding"),
        UiAttribute("margin"),
        UiAttribute("gap"),
        UiAttribute("align", "start start"),
        UiAttribute("alignItems", "start start"),
        UiAttribute("grow"),
        UiAttribute("position"),
        UiAttribute("background"),
        UiAttribute("backgroundImage", "image(\"\")"),
        UiAttribute("imageSlice"),
        UiAttribute("foreground"),
        UiAttribute("color"),
        UiAttribute("border"),
        UiAttribute("borderRadius"),
        UiAttribute("shadow"),
        UiAttribute("boxShadow"),
        UiAttribute("opacity"),
        UiAttribute("tint"),
        UiAttribute("translate"),
        UiAttribute("rotate"),
        UiAttribute("scale"),
        UiAttribute("pivot", "center"),
        UiAttribute("perspective"),
        UiAttribute("filter"),
        UiAttribute("backdropFilter"),
        UiAttribute("backfaceVisibility"),
        UiAttribute("hoverable", "true"),
        UiAttribute("clickable", "true"),
        UiAttribute("focusable", "true"),
        UiAttribute("draggable", "true"),
        UiAttribute("scrollable", "true"),
        UiAttribute("clip", "true"),
        UiAttribute("layer"),
        UiAttribute("textWrap"),
        UiAttribute("textAlign"),
        UiAttribute("fontSize"),
        UiAttribute("typing"),
        UiAttribute("transition"),
        UiAttribute("onClick", "{event:\"\"}"),
        UiAttribute("onDrag", "{event:\"\"}"),
    )

    val elementAttributes = mapOf(
        "import" to listOf(UiAttribute("element"), UiAttribute("named")),
        "text" to listOf(UiAttribute("text")),
        "image" to listOf(UiAttribute("source"), UiAttribute("src"), UiAttribute("image"), UiAttribute("imageFit")),
        "item" to listOf(UiAttribute("item"), UiAttribute("value")),
        "entity" to listOf(UiAttribute("entity"), UiAttribute("value")),
        "canvas" to listOf(UiAttribute("renderer")),
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
        "tint",
        "translate",
        "rotate",
        "scale",
        "pivot",
        "transform-origin",
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
        "image-slice",
        "slice",
        "scrollbar",
        "scrollbar-width",
        "scrollbar-thickness",
        "scrollbar-margin",
        "scrollbar-min-thumb",
        "scrollbar-track",
        "scrollbar-thumb",
        "scrollbar-track-border",
        "scrollbar-thumb-border",
        "scrollbar-track-radius",
        "scrollbar-thumb-radius",
        "scrollbar-track-fit",
        "scrollbar-thumb-fit",
        "scrollbar-track-slice",
        "scrollbar-thumb-slice",
        "text-wrap",
        "text-align",
        "font-size",
        "typing",
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
        "tint" to listOf("#FFFFFF", "rgba(255, 255, 255, 0.75)", "transparent"),
        "rotate" to listOf("0", "10", "0 0 10"),
        "scale" to listOf("1", "1.05", "1 1"),
        "pivot" to pivotValues(),
        "transform-origin" to pivotValues(),
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
        "image-slice" to sliceValues(),
        "slice" to sliceValues(),
        "scrollbar" to listOf("7px"),
        "scrollbar-width" to listOf("7px", "10px", "12px"),
        "scrollbar-thickness" to listOf("7px", "10px", "12px"),
        "scrollbar-margin" to listOf("2px", "3px", "4px"),
        "scrollbar-min-thumb" to listOf("16px", "18px", "24px"),
        "scrollbar-track" to listOf("#00000066", "image(\"\")", "linear-gradient(180deg, #20242c, #111319)"),
        "scrollbar-thumb" to listOf("#C8D6E6E6", "image(\"\")", "linear-gradient(180deg, #FFFFFF, #7FA8FF)"),
        "scrollbar-track-border" to listOf("1px #FFFFFF22"),
        "scrollbar-thumb-border" to listOf("1px #FFFFFF55"),
        "scrollbar-track-radius" to listOf("3px", "5px", "50%"),
        "scrollbar-thumb-radius" to listOf("3px", "5px", "50%"),
        "scrollbar-track-fit" to imageFitValues(),
        "scrollbar-thumb-fit" to imageFitValues(),
        "scrollbar-track-slice" to sliceValues(),
        "scrollbar-thumb-slice" to sliceValues(),
        "text-wrap" to listOf("wrap", "nowrap"),
        "text-align" to listOf("left", "center", "right", "justify"),
        "font-size" to listOf("10px", "12px", "16px", "24px"),
        "typing" to listOf("auto linear", "auto ease-out", "5s ease-in", "none"),
        "wrap" to listOf("wrap", "nowrap"),
    )

    fun attributesFor(element: String): List<UiAttribute> {
        val specific = elementAttributes[element.lowercase()].orEmpty()
        return (specific + globalAttributes).distinctBy { it.name }
    }

    fun valuesFor(property: String): List<String> = valueCompletions[property.toStylePropertyName()].orEmpty()

    private fun String.toStylePropertyName(): String {
        val result = StringBuilder()
        forEachIndexed { index, char ->
            if (char.isUpperCase()) {
                if (index > 0) result.append('-')
                result.append(char.lowercaseChar())
            } else {
                result.append(char.lowercaseChar())
            }
        }
        return result.toString()
    }

    private fun alignValues(): List<String> = listOf("start start", "center center", "end end", "start center", "center start")

    private fun colorValues(): List<String> = listOf("#FFFFFF", "#000000", "transparent", "white", "black", "rgba(255, 255, 255, 1)")

    private fun booleanValues(): List<String> = listOf("true", "false")

    private fun imageFitValues(): List<String> = listOf(
        "stretch",
        "contain",
        "cover",
        "none",
        "9-slice 4px",
        "3-slice-vertical 4px",
        "3-slice-horizontal 4px",
    )

    private fun sliceValues(): List<String> = listOf("4px", "4px 8px", "4px 8px 4px 8px")

    private fun pivotValues(): List<String> = listOf(
        "center",
        "top-left",
        "top-center",
        "top-right",
        "center-left",
        "center-right",
        "bottom-left",
        "bottom-center",
        "bottom-right",
        "0px 0px 0px",
    )
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
