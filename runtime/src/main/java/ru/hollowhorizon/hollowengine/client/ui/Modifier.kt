package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.effects.UiTextEffect
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateLayout
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.style.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.style.HssResourceLoader
import ru.hollowhorizon.hollowengine.client.ui.style.MinecraftHssResourceLoader
import ru.hollowhorizon.hollowengine.client.ui.style.MutableUiStyle
import ru.hollowhorizon.hollowengine.client.ui.style.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.style.UiBoundString
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterEffect
import ru.hollowhorizon.hollowengine.client.ui.style.UiGradientStop
import ru.hollowhorizon.hollowengine.client.ui.style.UiInputStyle
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.style.UiRadialGradient
import ru.hollowhorizon.hollowengine.client.ui.style.UiShadow
import ru.hollowhorizon.hollowengine.client.ui.style.UiStylesheetReference
import ru.hollowhorizon.hollowengine.client.ui.style.UiTextOverflow
import ru.hollowhorizon.hollowengine.client.ui.style.UiTransition
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiKeyInput
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTyping

interface Modifier {
    fun applyTo(style: MutableUiStyle)

    infix fun then(other: Modifier): Modifier {
        return CompositeModifier(mutableSetOf(this, other))
    }

    companion object : Modifier {
        override fun applyTo(style: MutableUiStyle) {}

        override fun then(other: Modifier): Modifier = other
    }
}

fun Modifier.style(location: String, loader: HssResourceLoader = MinecraftHssResourceLoader) = this then
        StyleImportModifier(UiStylesheetReference.Resource(location, loader))

fun Modifier.style(stylesheet: CompiledHss) = this then
        StyleImportModifier(UiStylesheetReference.Compiled(stylesheet))

fun Modifier.size(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) = this then
        StyleModifier(setOf(UiStyleProperty.WIDTH, UiStyleProperty.HEIGHT), modifierKey("size", width, height)) {
            it.size = UiSize(width, height)
        }

fun Modifier.minSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) = this then
        StyleModifier(key = modifierKey("min-size", width, height)) { it.minSize = UiSize(width, height) }

fun Modifier.maxSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) = this then
        StyleModifier(key = modifierKey("max-size", width, height)) { it.maxSize = UiSize(width, height) }

fun Modifier.aspectRatio(value: Float) = this then
        StyleModifier(key = modifierKey("aspect-ratio", value)) { it.aspectRatio = value }

fun Modifier.padding(value: UiLength) = this then
        StyleModifier(key = modifierKey("padding", value)) { it.padding = UiInsets.all(value) }

fun Modifier.padding(horizontal: UiLength, vertical: UiLength) = this then
        StyleModifier(key = modifierKey("padding-hv", horizontal, vertical)) {
            it.padding = UiInsets.hv(horizontal, vertical)
        }

fun Modifier.padding(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) = this then
        StyleModifier(key = modifierKey("padding-ltrb", left, top, right, bottom)) {
            it.padding = UiInsets(left, top, right, bottom)
        }

fun Modifier.margin(value: UiLength) = this then
        StyleModifier(key = modifierKey("margin", value)) { it.margin = UiInsets.all(value) }

fun Modifier.margin(horizontal: UiLength, vertical: UiLength) = this then
        StyleModifier(key = modifierKey("margin-hv", horizontal, vertical)) {
            it.margin = UiInsets.hv(horizontal, vertical)
        }

fun Modifier.margin(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) = this then
        StyleModifier(key = modifierKey("margin-ltrb", left, top, right, bottom)) {
            it.margin = UiInsets(left, top, right, bottom)
        }

fun Modifier.gap(value: UiLength) = this then StyleModifier(key = modifierKey("gap", value)) { it.gap = value }

fun Modifier.align(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) = this then StyleModifier(
    key = modifierKey("align", horizontal, vertical),
) {
    it.alignHorizontal = horizontal
    it.alignVertical = vertical
}

fun Modifier.alignItems(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) = this then StyleModifier(
    key = modifierKey("align-items", horizontal, vertical),
) {
    it.alignItemsHorizontal = horizontal
    it.alignItemsVertical = vertical
}

fun Modifier.alignChildren(items: UiAlign = UiAlign.AUTO, content: UiAlign = UiAlign.AUTO) = this then StyleModifier(
    key = modifierKey("align-children", items, content),
) {
    it.alignItemsHorizontal = items
    it.alignItemsVertical = content
}

fun Modifier.grow(value: Float = 1f) = this then StyleModifier(key = modifierKey("grow", value)) { it.grow = value }

fun Modifier.position(x: UiLength, y: UiLength, z: Float = 0f) = this then PositionModifier(x, y, z)

fun Modifier.background(color: UiColor) = this then
        StyleModifier(key = modifierKey("background-color", color)) { it.background = UiPaint.Color(color) }

fun Modifier.background(angleDegrees: Float, stops: List<UiGradientStop>) = this then
        StyleModifier(key = modifierKey("background-gradient", angleDegrees, stops)) {
            it.background = UiPaint.LinearGradient(angleDegrees, stops)
        }

fun Modifier.background(gradient: UiRadialGradient) = this then
        StyleModifier(key = modifierKey("background-radial-gradient", gradient)) {
            it.background = UiPaint.RadialGradient(gradient)
        }

fun Modifier.background(paint: UiPaint) = this then
        StyleModifier(key = modifierKey("background-paint", paint)) { it.background = paint }

fun Modifier.background(source: UiBoundString) = this then
        StyleModifier(key = modifierKey("background-image", source)) { it.background = UiPaint.Image(source) }

fun Modifier.foreground(color: UiColor) = this then
        StyleModifier(key = modifierKey("foreground", color)) { it.foreground = color }

fun Modifier.image(source: UiBoundString) =
    this then StyleModifier(key = modifierKey("image", source)) { it.image = source }

fun Modifier.shader(name: UiBoundString) =
    this then StyleModifier(key = modifierKey("shader", name)) { it.shader = name }

fun Modifier.border(width: UiLength, color: UiColor, radius: Float = 0f) = this then
        StyleModifier(key = modifierKey("border", width, color, radius)) {
            it.border = UiBorder(UiInsets.all(width), color, radius)
        }

fun Modifier.shadow(vararg shadows: UiShadow) = this then StyleModifier(key = modifierKey("shadow", shadows.toList())) {
    it.shadows = shadows.toList()
}

fun Modifier.opacity(value: Float) = this then StyleModifier(key = modifierKey("opacity", value)) { it.opacity = value }

fun Modifier.tint(color: UiColor) = this then StyleModifier(key = modifierKey("tint", color)) { it.tint = color }

fun Modifier.translate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = this then TransformPatch(
    modifierKey("translate", x, y, z),
) { current ->
    current.copy(translate = UiVec3(x, y, z))
}

fun Modifier.rotate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = this then TransformPatch(
    modifierKey("rotate", x, y, z),
) { current ->
    current.copy(rotate = UiVec3(x, y, z))
}

fun Modifier.scale(x: Float, y: Float = x, z: Float = 1f) = this then TransformPatch(
    modifierKey("scale", x, y, z),
) { current ->
    current.copy(scale = UiVec3(x, y, z))
}

fun Modifier.perspective(value: Float) = this then
        TransformPatch(modifierKey("perspective", value)) { current -> current.copy(perspective = value) }

fun Modifier.pivot(value: UiTransformPivot) = this then
        TransformPatch(modifierKey("pivot", value)) { current -> current.copy(pivot = value) }

fun Modifier.pivot(x: UiLength, y: UiLength, z: UiLength = 0.px) = this then
        TransformPatch(modifierKey("pivot-lengths", x, y, z)) { current ->
            current.copy(pivot = UiTransformPivot(x, y, z))
        }

fun Modifier.filter(vararg effects: UiFilterEffect) =
    this then StyleModifier(key = modifierKey("filter", effects.toList())) {
        it.filter = UiFilterChain(effects.toList())
    }

fun Modifier.backdropFilter(vararg effects: UiFilterEffect) = this then
        StyleModifier(key = modifierKey("backdrop-filter", effects.toList())) {
            it.backdropFilter = UiFilterChain(effects.toList())
        }

fun Modifier.backfaceVisibility(value: UiBackfaceVisibility) = this then
        StyleModifier(key = modifierKey("backface-visibility", value)) { it.backfaceVisibility = value }

fun Modifier.input(
    hoverable: Boolean = false,
    clickable: Boolean = false,
    focusable: Boolean = false,
    draggable: Boolean = false,
    scrollable: Boolean = false,
) = this then StyleModifier(key = modifierKey("input", hoverable, clickable, focusable, draggable, scrollable)) {
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

fun Modifier.cursor(shape: UiCursorShape) =
    this then StyleModifier(key = modifierKey("cursor", shape)) { it.cursor = shape }

fun Modifier.clip(enabled: Boolean = true) =
    this then StyleModifier(key = modifierKey("clip", enabled)) { it.clip = enabled }

fun Modifier.clip(shape: Shape) = this then StyleModifier(key = modifierKey("clip-shape", shape)) {
    it.clip = true
    it.clipShape = shape
}

fun Modifier.shape(shape: Shape) = this then StyleModifier(key = modifierKey("shape", shape)) { it.shape = shape }

fun Modifier.shape(shape: Shape, fill: UiPaint?, stroke: UiPaint? = null, strokeWidth: UiLength = 0.px) = this then
        StyleModifier(key = modifierKey("shape-paint", shape, fill, stroke, strokeWidth)) {
            it.shape = shape
            it.shapeFill = fill
            it.shapeStroke = stroke
            it.shapeStrokeWidth = strokeWidth
        }

fun Modifier.shapeFill(paint: UiPaint) =
    this then StyleModifier(key = modifierKey("shape-fill", paint)) { it.shapeFill = paint }

fun Modifier.shapeFill(color: UiColor) = shapeFill(UiPaint.Color(color))

fun Modifier.shapeStroke(paint: UiPaint, width: UiLength = 1.px) = this then
        StyleModifier(key = modifierKey("shape-stroke", paint, width)) {
            it.shapeStroke = paint
            it.shapeStrokeWidth = width
        }

fun Modifier.shapeStroke(color: UiColor, width: UiLength = 1.px) = this then shapeStroke(UiPaint.Color(color), width)

fun Modifier.layer(value: Int) = this then StyleModifier(key = modifierKey("layer", value)) { it.layer = value }

fun Modifier.textWrap(enabled: Boolean = true) = this then
        StyleModifier(key = modifierKey("text-wrap", enabled)) { it.textWrap = enabled }

fun Modifier.textOverflow(value: UiTextOverflow) = this then
        StyleModifier(key = modifierKey("text-overflow", value)) { it.textOverflow = value }

fun Modifier.textAlign(value: UiTextAlign) = this then
        StyleModifier(key = modifierKey("text-align", value)) { it.textAlign = value }

fun Modifier.lineSpacing(value: Float) = this then StyleModifier(key = modifierKey("line-spacing", value)) {
    it.lineSpacing = value.coerceAtLeast(0f)
}

fun Modifier.spaceWidth(value: Float?) = this then StyleModifier(key = modifierKey("space-width", value)) {
    it.spaceWidth = value?.coerceAtLeast(0f)
}

fun Modifier.fontSize(value: Float) = this then StyleModifier(key = modifierKey("font-size", value)) {
    it.fontSize = value.coerceAtLeast(0.0001f)
}

fun Modifier.fontFamily(name: String) =
    this then StyleModifier(key = modifierKey("font-family", name)) { it.fontFamily = name }

fun Modifier.textEffects(vararg effects: UiTextEffect) = this then
        StyleModifier(key = modifierKey("text-effects", effects.toList())) {
            it.textEffects = effects.toList()
        }

fun Modifier.typing(value: UiTyping?) =
    this then StyleModifier(key = modifierKey("typing", value)) { it.typing = value }

fun Modifier.transition(vararg transitions: UiTransition) = this then
        StyleModifier(key = modifierKey("transition", transitions.toList())) {
            it.transitions = transitions.toList()
        }

fun Modifier.onInit(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.INIT, handler)

fun Modifier.onUpdate(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.UPDATE, handler)

fun Modifier.onClose(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.CLOSE, handler)

fun Modifier.onEnter(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.ENTER, handler)

fun Modifier.onExit(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.EXIT, handler)

fun Modifier.onHover(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.HOVER, handler)

fun Modifier.onPress(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.PRESS, handler)

fun Modifier.onClick(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.CLICK, handler)

fun Modifier.onRelease(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.RELEASE, handler)

fun Modifier.onDrag(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.DRAG, handler)

fun Modifier.onScroll(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.SCROLL, handler)

fun Modifier.onCharTyped(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.CHAR_TYPED, handler)

fun Modifier.onKeyPressed(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.KEY_PRESSED, handler)

fun Modifier.onKeyInput(handler: (UiKeyInput) -> Boolean) = this then KeyInputModifier(handler)

fun Modifier.onFocus(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.FOCUS, handler)

fun Modifier.onUnfocus(handler: (UiEvent) -> Unit) = this then EventModifier(UiEventKind.UNFOCUS, handler)

fun Modifier.eventScript(kind: UiEventKind, source: String, sink: UiEventSink) = this then
        ScriptEventModifier(kind, source, sink)

fun Modifier.state(vararg states: UiState) = this then StateModifier(states.toSet())

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

data class CompositeModifier(private val values: MutableSet<Modifier>) : Modifier {
    override fun then(other: Modifier): Modifier {
        if (other is CompositeModifier) {
            values.addAll(other.values)
        } else {
            values.add(other)
        }
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
            UiEventKind.HOVER,
                -> input.copy(hoverable = true)

            UiEventKind.PRESS,
            UiEventKind.CLICK,
            UiEventKind.RELEASE,
                -> input.copy(clickable = true, hoverable = true)

            UiEventKind.DRAG -> input.copy(draggable = true, hoverable = true)

            UiEventKind.SCROLL -> input.copy(scrollable = true, hoverable = true)

            UiEventKind.CHAR_TYPED,
            UiEventKind.KEY_PRESSED,
            UiEventKind.FOCUS,
            UiEventKind.UNFOCUS,
                -> input.copy(focusable = true, hoverable = true)

            UiEventKind.INIT,
            UiEventKind.UPDATE,
            UiEventKind.CLOSE,
                -> input
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

data class StateModifier(
    val states: Set<UiState>,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) = Unit
}

internal data class RuntimeStateModifier(
    val states: Set<UiState>,
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

internal fun UiNode.effectiveStates(): Set<UiState> {
    val flattened = modifiers.flattenModifiers()
    val modifierStates = flattened
        .filterIsInstance<StateModifier>()
        .flatMapTo(linkedSetOf()) { it.states }
    val runtimeStates = flattened
        .filterIsInstance<RuntimeStateModifier>()
        .flatMapTo(linkedSetOf()) { it.states }
    if (modifierStates.isEmpty() && runtimeStates.isEmpty()) return states.toSet()
    return states + modifierStates + runtimeStates
}

internal fun UiNode.setRuntimeStates(next: Set<UiState>) {
    val index = modifiers.indexOfFirst { it is RuntimeStateModifier }
    val current = (index.takeIf { it >= 0 }?.let { modifiers[it] } as? RuntimeStateModifier)?.states.orEmpty()
    if (current == next) return
    if (next.isEmpty()) {
        if (index >= 0) modifiers.removeAt(index)
    } else if (index >= 0) {
        modifiers[index] = RuntimeStateModifier(next)
    } else {
        modifiers += RuntimeStateModifier(next)
    }
    invalidateLayout()
}

fun String.bound() = UiBoundString(this)

private data class ModifierKey(
    val name: String,
    val values: List<Any?>,
)

private fun modifierKey(name: String, vararg values: Any?) = ModifierKey(name, values.toList())
