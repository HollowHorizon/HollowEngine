package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.effects.UiTextEffect
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiClientScript

sealed interface Modifier {
    fun applyTo(style: MutableUiStyle)

    companion object {
        fun style(location: String, loader: HssResourceLoader = MinecraftHssResourceLoader) =
            StyleImportModifier(UiStylesheetReference.Resource(location, loader))

        fun style(stylesheet: CompiledHss) =
            StyleImportModifier(UiStylesheetReference.Compiled(stylesheet))

        fun size(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier(setOf(UiStyleProperty.WIDTH, UiStyleProperty.HEIGHT), modifierKey("size", width, height)) {
                it.size = UiSize(width, height)
            }

        fun minSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier(key = modifierKey("min-size", width, height)) { it.minSize = UiSize(width, height) }

        fun maxSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier(key = modifierKey("max-size", width, height)) { it.maxSize = UiSize(width, height) }

        fun aspectRatio(value: Float) = StyleModifier(key = modifierKey("aspect-ratio", value)) { it.aspectRatio = value }

        fun padding(value: UiLength) = StyleModifier(key = modifierKey("padding", value)) { it.padding = UiInsets.all(value) }

        fun padding(horizontal: UiLength, vertical: UiLength) =
            StyleModifier(key = modifierKey("padding-hv", horizontal, vertical)) {
                it.padding = UiInsets.hv(horizontal, vertical)
            }

        fun padding(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) =
            StyleModifier(key = modifierKey("padding-ltrb", left, top, right, bottom)) {
                it.padding = UiInsets(left, top, right, bottom)
            }

        fun margin(value: UiLength) = StyleModifier(key = modifierKey("margin", value)) { it.margin = UiInsets.all(value) }

        fun margin(horizontal: UiLength, vertical: UiLength) =
            StyleModifier(key = modifierKey("margin-hv", horizontal, vertical)) {
                it.margin = UiInsets.hv(horizontal, vertical)
            }

        fun margin(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) =
            StyleModifier(key = modifierKey("margin-ltrb", left, top, right, bottom)) {
                it.margin = UiInsets(left, top, right, bottom)
            }

        fun gap(value: UiLength) = StyleModifier(key = modifierKey("gap", value)) { it.gap = value }

        fun align(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) = StyleModifier(
            key = modifierKey("align", horizontal, vertical),
        ) {
            it.alignHorizontal = horizontal
            it.alignVertical = vertical
        }

        fun alignItems(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) = StyleModifier(
            key = modifierKey("align-items", horizontal, vertical),
        ) {
            it.alignItemsHorizontal = horizontal
            it.alignItemsVertical = vertical
        }

        fun alignChildren(items: UiAlign = UiAlign.AUTO, content: UiAlign = UiAlign.AUTO) = StyleModifier(
            key = modifierKey("align-children", items, content),
        ) {
            it.alignItemsHorizontal = items
            it.alignItemsVertical = content
        }

        fun grow(value: Float = 1f) = StyleModifier(key = modifierKey("grow", value)) { it.grow = value }

        fun position(x: UiLength, y: UiLength, z: Float = 0f) = PositionModifier(x, y, z)

        fun background(color: UiColor) =
            StyleModifier(key = modifierKey("background-color", color)) { it.background = UiPaint.Color(color) }

        fun background(angleDegrees: Float, stops: List<UiGradientStop>) =
            StyleModifier(key = modifierKey("background-gradient", angleDegrees, stops)) {
                it.background = UiPaint.LinearGradient(angleDegrees, stops)
            }

        fun background(gradient: UiRadialGradient) =
            StyleModifier(key = modifierKey("background-radial-gradient", gradient)) {
                it.background = UiPaint.RadialGradient(gradient)
            }

        fun background(paint: UiPaint) =
            StyleModifier(key = modifierKey("background-paint", paint)) { it.background = paint }

        fun background(source: UiBoundString) =
            StyleModifier(key = modifierKey("background-image", source)) { it.background = UiPaint.Image(source) }

        fun foreground(color: UiColor) = StyleModifier(key = modifierKey("foreground", color)) { it.foreground = color }

        fun image(source: UiBoundString) = StyleModifier(key = modifierKey("image", source)) { it.image = source }

        fun shader(name: UiBoundString) = StyleModifier(key = modifierKey("shader", name)) { it.shader = name }

        fun border(width: UiLength, color: UiColor, radius: Float = 0f) =
            StyleModifier(key = modifierKey("border", width, color, radius)) {
                it.border = UiBorder(UiInsets.all(width), color, radius)
            }

        fun shadow(vararg shadows: UiShadow) = StyleModifier(key = modifierKey("shadow", shadows.toList())) {
            it.shadows = shadows.toList()
        }

        fun opacity(value: Float) = StyleModifier(key = modifierKey("opacity", value)) { it.opacity = value }

        fun tint(color: UiColor) = StyleModifier(key = modifierKey("tint", color)) { it.tint = color }

        fun translate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = TransformPatch(
            modifierKey("translate", x, y, z),
        ) { current ->
            current.copy(translate = UiVec3(x, y, z))
        }

        fun rotate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = TransformPatch(
            modifierKey("rotate", x, y, z),
        ) { current ->
            current.copy(rotate = UiVec3(x, y, z))
        }

        fun scale(x: Float, y: Float = x, z: Float = 1f) = TransformPatch(
            modifierKey("scale", x, y, z),
        ) { current ->
            current.copy(scale = UiVec3(x, y, z))
        }

        fun perspective(value: Float) =
            TransformPatch(modifierKey("perspective", value)) { current -> current.copy(perspective = value) }

        fun pivot(value: UiTransformPivot) =
            TransformPatch(modifierKey("pivot", value)) { current -> current.copy(pivot = value) }

        fun pivot(x: UiLength, y: UiLength, z: UiLength = 0.px) =
            TransformPatch(modifierKey("pivot-lengths", x, y, z)) { current ->
                current.copy(pivot = UiTransformPivot(x, y, z))
            }

        fun filter(vararg effects: UiFilterEffect) = StyleModifier(key = modifierKey("filter", effects.toList())) {
            it.filter = UiFilterChain(effects.toList())
        }

        fun backdropFilter(vararg effects: UiFilterEffect) =
            StyleModifier(key = modifierKey("backdrop-filter", effects.toList())) {
                it.backdropFilter = UiFilterChain(effects.toList())
            }

        fun backfaceVisibility(value: UiBackfaceVisibility) =
            StyleModifier(key = modifierKey("backface-visibility", value)) { it.backfaceVisibility = value }

        fun input(
            hoverable: Boolean = false,
            clickable: Boolean = false,
            focusable: Boolean = false,
            draggable: Boolean = false,
            scrollable: Boolean = false,
        ) = StyleModifier(key = modifierKey("input", hoverable, clickable, focusable, draggable, scrollable)) {
            it.input = (it.input ?: UiInputStyle()).merge(
                UiInputStyle(
                    hoverable = hoverable,
                    clickable = clickable,
                    focusable = focusable,
                    draggable = draggable,
                    scrollable = scrollable,
                )
            )
        }

        fun cursor(shape: UiCursorShape) = StyleModifier(key = modifierKey("cursor", shape)) { it.cursor = shape }

        fun clip(enabled: Boolean = true) = StyleModifier(key = modifierKey("clip", enabled)) { it.clip = enabled }

        fun clip(shape: Shape) = StyleModifier(key = modifierKey("clip-shape", shape)) {
            it.clip = true
            it.clipShape = shape
        }

        fun shape(shape: Shape) = StyleModifier(key = modifierKey("shape", shape)) { it.shape = shape }

        fun shape(shape: Shape, fill: UiPaint?, stroke: UiPaint? = null, strokeWidth: UiLength = 0.px) =
            StyleModifier(key = modifierKey("shape-paint", shape, fill, stroke, strokeWidth)) {
                it.shape = shape
                it.shapeFill = fill
                it.shapeStroke = stroke
                it.shapeStrokeWidth = strokeWidth
            }

        fun shapeFill(paint: UiPaint) = StyleModifier(key = modifierKey("shape-fill", paint)) { it.shapeFill = paint }

        fun shapeFill(color: UiColor) = shapeFill(UiPaint.Color(color))

        fun shapeStroke(paint: UiPaint, width: UiLength = 1.px) =
            StyleModifier(key = modifierKey("shape-stroke", paint, width)) {
                it.shapeStroke = paint
                it.shapeStrokeWidth = width
            }

        fun shapeStroke(color: UiColor, width: UiLength = 1.px) = shapeStroke(UiPaint.Color(color), width)

        fun layer(value: Int) = StyleModifier(key = modifierKey("layer", value)) { it.layer = value }

        fun textWrap(enabled: Boolean = true) =
            StyleModifier(key = modifierKey("text-wrap", enabled)) { it.textWrap = enabled }

        fun textAlign(value: UiTextAlign) =
            StyleModifier(key = modifierKey("text-align", value)) { it.textAlign = value }

        fun lineSpacing(value: Float) = StyleModifier(key = modifierKey("line-spacing", value)) {
            it.lineSpacing = value.coerceAtLeast(0f)
        }

        fun spaceWidth(value: Float?) = StyleModifier(key = modifierKey("space-width", value)) {
            it.spaceWidth = value?.coerceAtLeast(0f)
        }

        fun fontSize(value: Float) = StyleModifier(key = modifierKey("font-size", value)) {
            it.fontSize = value.coerceAtLeast(0.0001f)
        }

        fun fontFamily(name: String) = StyleModifier(key = modifierKey("font-family", name)) { it.fontFamily = name }

        fun textEffects(vararg effects: UiTextEffect) = StyleModifier(key = modifierKey("text-effects", effects.toList())) {
            it.textEffects = effects.toList()
        }

        fun typing(value: UiTyping?) = StyleModifier(key = modifierKey("typing", value)) { it.typing = value }

        fun transition(vararg transitions: UiTransition) =
            StyleModifier(key = modifierKey("transition", transitions.toList())) { it.transitions = transitions.toList() }

        fun onInit(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.INIT, handler)

        fun onUpdate(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.UPDATE, handler)

        fun onClose(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.CLOSE, handler)

        fun onEnter(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.ENTER, handler)

        fun onExit(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.EXIT, handler)

        fun onHover(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.HOVER, handler)

        fun onPress(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.PRESS, handler)

        fun onClick(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.CLICK, handler)

        fun onRelease(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.RELEASE, handler)

        fun onDrag(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.DRAG, handler)

        fun onScroll(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.SCROLL, handler)

        fun onCharTyped(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.CHAR_TYPED, handler)

        fun onKeyPressed(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.KEY_PRESSED, handler)

        fun onKeyInput(handler: (UiKeyInput) -> Boolean) = KeyInputModifier(handler)

        fun onFocus(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.FOCUS, handler)

        fun onUnfocus(handler: (UiEvent) -> Unit) = EventModifier(UiEventKind.UNFOCUS, handler)

        fun emitOn(kind: UiEventKind, template: UiEventPayloadTemplate, sink: UiEventSink) =
            EventModifier(kind) { sink.emit(template.resolve(it)) }

        fun emitOnClick(template: UiEventPayloadTemplate, sink: UiEventSink) =
            emitOn(UiEventKind.CLICK, template, sink)

        fun emitOnDrag(template: UiEventPayloadTemplate, sink: UiEventSink) =
            emitOn(UiEventKind.DRAG, template, sink)

        fun eventScript(kind: UiEventKind, source: String, sink: UiEventSink) =
            ScriptEventModifier(kind, source, sink)

        fun then(vararg modifiers: Modifier) = CompositeModifier(modifiers.toMutableList())
    }
}

enum class UiStyleProperty {
    WIDTH,
    HEIGHT,
    TRANSITIONS
}

class StyleModifier(
    val properties: Set<UiStyleProperty> = emptySet(),
    key: Any? = null,
    private val writer: (MutableUiStyle) -> Unit,
) : Modifier {
    private val equalityKey = key ?: writer

    override fun applyTo(style: MutableUiStyle) {
        writer(style)
        if (properties.isNotEmpty()) style.explicitProperties = style.explicitProperties.orEmpty() + properties
    }

    override fun equals(other: Any?): Boolean {
        return other is StyleModifier &&
                properties == other.properties &&
                equalityKey == other.equalityKey
    }

    override fun hashCode(): Int {
        var result = properties.hashCode()
        result = 31 * result + equalityKey.hashCode()
        return result
    }
}

data class CompositeModifier(private val values: MutableList<Modifier>) : Modifier {
    fun then(modifier: Modifier): CompositeModifier {
        values.add(modifier)
        return this
    }

    override fun applyTo(style: MutableUiStyle) {
        values.forEach { it.applyTo(style) }
    }

    fun flatten(): List<Modifier> = values.flatMap { modifier ->
        if (modifier is CompositeModifier) modifier.flatten() else listOf(modifier)
    }
}

data class StyleImportModifier(
    val reference: UiStylesheetReference,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) = Unit
}

data class EventModifier(
    val kind: UiEventKind,
    val handler: (UiEvent) -> Unit,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        val input = style.input ?: UiInputStyle()
        style.input = when (kind) {
            UiEventKind.ENTER,
            UiEventKind.EXIT,
            UiEventKind.HOVER -> input.copy(hoverable = true)

            UiEventKind.PRESS,
            UiEventKind.CLICK,
            UiEventKind.RELEASE -> input.copy(clickable = true, hoverable = true)

            UiEventKind.DRAG -> input.copy(draggable = true, hoverable = true)

            UiEventKind.SCROLL -> input.copy(scrollable = true, hoverable = true)

            UiEventKind.CHAR_TYPED,
            UiEventKind.KEY_PRESSED,
            UiEventKind.FOCUS,
            UiEventKind.UNFOCUS -> input.copy(focusable = true, hoverable = true)

            UiEventKind.INIT,
            UiEventKind.UPDATE,
            UiEventKind.CLOSE -> input
        }
    }
}

data class KeyInputModifier(
    val handler: (UiKeyInput) -> Boolean,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        val input = style.input ?: UiInputStyle()
        style.input = input.copy(focusable = true, hoverable = true)
    }
}

data class ScriptEventModifier(
    val kind: UiEventKind,
    val source: String,
    val sink: UiEventSink,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        EventModifier(kind) {}.applyTo(style)
    }
}

data class UiClientScriptModifier(
    val scripts: List<UiClientScript>,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) = Unit
}

data class PositionModifier(
    val x: UiLength,
    val y: UiLength,
    val z: Float = 0f,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        style.position = UiPosition(x, y, z)
    }
}

class TransformPatch(
    key: Any? = null,
    private val patch: (UiTransform) -> UiTransform,
) : Modifier {
    private val equalityKey = key ?: patch

    override fun applyTo(style: MutableUiStyle) {
        style.transform = patch(style.transform ?: UiTransform())
    }

    override fun equals(other: Any?): Boolean {
        return other is TransformPatch && equalityKey == other.equalityKey
    }

    override fun hashCode(): Int = equalityKey.hashCode()
}

fun MutableList<Modifier>.style(): MutableUiStyle {
    val style = MutableUiStyle()
    forEach { it.applyTo(style) }
    return style
}

fun Iterable<Modifier>.flattenModifiers(): List<Modifier> = flatMap { modifier ->
    if (modifier is CompositeModifier) modifier.flatten() else listOf(modifier)
}

fun String.bound() = UiBoundString(this)

private data class ModifierKey(
    val name: String,
    val values: List<Any?>,
)

private fun modifierKey(name: String, vararg values: Any?) = ModifierKey(name, values.toList())
