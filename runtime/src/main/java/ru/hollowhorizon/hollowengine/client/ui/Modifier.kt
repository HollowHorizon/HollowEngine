package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.effects.UiTextEffect
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateDraw
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateInput
import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateLayout
import ru.hollowhorizon.hollowengine.client.ui.shape.Shape
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiKeyInput
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTyping

interface Modifier {
    infix fun then(other: Modifier): Modifier {
        return CompositeModifier(mutableListOf(this, other))
    }

    companion object : Modifier {
        override fun then(other: Modifier): Modifier = other
    }
}

/**
 * A modifier that contributes style property values. Application is declarative and
 * order-independent per property (last write wins), so `padding` and `margin` keep
 * their box-model meaning regardless of modifier order — unlike Compose's chained
 * modifier semantics.
 */
interface UiModifierPatchNode : Modifier {
    fun applyPatch(style: UiStylePatch)
}

interface PointerInputModifierNode : Modifier {
    fun onPointerEvent(event: UiEvent)
}

interface InputModifierNode : Modifier

enum class UiInvalidationPhase {
    Layout,
    Draw,
    Input
}

enum class UiStyleProperty {
    WIDTH,
    HEIGHT,
    TRANSITIONS
}

/**
 * Sets a single style property to a fixed value. The property itself carries all the
 * logic (defaults, merge, interpolation, invalidation phase), so this modifier is pure
 * data with value equality for free.
 */
data class StylePropModifier<T>(
    val prop: UiStyleProp<T>,
    val value: T,
    val explicit: Set<UiStyleProperty> = emptySet(),
) : UiModifierPatchNode {
    override fun applyPatch(style: UiStylePatch) {
        style[prop] = value
        if (explicit.isNotEmpty()) style.explicitProperties = style.explicitProperties.orEmpty() + explicit
    }
}

/**
 * Style contribution expressed as a patch-writer lambda; used where a single value
 * assignment is not enough (read-modify-write like input flags, HSS declarations).
 */
class StyleModifier(
    val properties: Set<UiStyleProperty> = emptySet(),
    val phases: Set<UiInvalidationPhase> = LayoutPhases,
    key: Any? = null,
    private val writer: (UiStylePatch) -> Unit,
) : UiModifierPatchNode {
    private val equalityKey = key ?: writer

    override fun applyPatch(style: UiStylePatch) {
        writer(style)
        if (properties.isNotEmpty()) style.explicitProperties = style.explicitProperties.orEmpty() + properties
    }

    override fun equals(other: Any?): Boolean {
        return other is StyleModifier &&
                properties == other.properties &&
                phases == other.phases &&
                equalityKey == other.equalityKey
    }

    override fun hashCode(): Int {
        var result = properties.hashCode()
        result = 31 * result + phases.hashCode()
        result = 31 * result + equalityKey.hashCode()
        return result
    }
}

data class CompositeModifier(private val values: MutableList<Modifier>) : Modifier {
    override fun then(other: Modifier): Modifier {
        if (other is CompositeModifier) {
            values.addAll(other.values)
        } else {
            values.add(other)
        }
        return this
    }

    fun flatten(): List<Modifier> = values.flatMap { modifier ->
        if (modifier is CompositeModifier) modifier.flatten() else listOf(modifier)
    }
}

data class StyleImportModifier(
    val reference: UiStylesheetReference,
) : Modifier

data class EventModifier(
    val kind: UiEventKind,
    val handler: (UiEvent) -> Unit,
) : PointerInputModifierNode, UiModifierPatchNode {
    override fun applyPatch(style: UiStylePatch) {
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

    override fun onPointerEvent(event: UiEvent) {
        if (event.kind == kind) handler(event)
    }
}

data class KeyInputModifier(
    val handler: (UiKeyInput) -> Boolean,
) : InputModifierNode, UiModifierPatchNode {
    override fun applyPatch(style: UiStylePatch) {
        val input = style.input ?: UiInputStyle()
        style.input = input.copy(focusable = true, hoverable = true)
    }
}

data class ScriptEventModifier(
    val kind: UiEventKind,
    val source: String,
    val sink: UiEventSink,
) : UiModifierPatchNode {
    override fun applyPatch(style: UiStylePatch) {
        EventModifier(kind) {}.applyPatch(style)
    }
}

data class StateModifier(
    val states: Set<UiState>,
) : Modifier

internal data class RuntimeStateModifier(
    val states: Set<UiState>,
) : Modifier

// -- Style DSL --------------------------------------------------------------------------

private fun <T> Modifier.prop(prop: UiStyleProp<T>, value: T): Modifier =
    this then StylePropModifier(prop, value)

fun Modifier.style(location: String, loader: HssResourceLoader = MinecraftHssResourceLoader) = this then
        StyleImportModifier(UiStylesheetReference.Resource(location, loader))

fun Modifier.style(stylesheet: CompiledHss) = this then
        StyleImportModifier(UiStylesheetReference.Compiled(stylesheet))

fun Modifier.size(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
    this then StylePropModifier(UiProps.Width, width, setOf(UiStyleProperty.WIDTH)) then
            StylePropModifier(UiProps.Height, height, setOf(UiStyleProperty.HEIGHT))

fun Modifier.minSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
    prop(UiProps.MinWidth, width).prop(UiProps.MinHeight, height)

fun Modifier.maxSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
    prop(UiProps.MaxWidth, width).prop(UiProps.MaxHeight, height)

fun Modifier.aspectRatio(value: Float) = prop(UiProps.AspectRatio, value)

fun Modifier.padding(value: UiLength) = prop(UiProps.Padding, UiInsets.all(value))

fun Modifier.padding(horizontal: UiLength, vertical: UiLength) =
    prop(UiProps.Padding, UiInsets.hv(horizontal, vertical))

fun Modifier.padding(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) =
    prop(UiProps.Padding, UiInsets(left, top, right, bottom))

fun Modifier.margin(value: UiLength) = prop(UiProps.Margin, UiInsets.all(value))

fun Modifier.margin(horizontal: UiLength, vertical: UiLength) =
    prop(UiProps.Margin, UiInsets.hv(horizontal, vertical))

fun Modifier.margin(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) =
    prop(UiProps.Margin, UiInsets(left, top, right, bottom))

fun Modifier.gap(value: UiLength) = prop(UiProps.Gap, value)

fun Modifier.align(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) =
    prop(UiProps.AlignHorizontal, horizontal).prop(UiProps.AlignVertical, vertical)

fun Modifier.alignItems(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) =
    prop(UiProps.AlignItemsHorizontal, horizontal).prop(UiProps.AlignItemsVertical, vertical)

fun Modifier.alignChildren(items: UiAlign = UiAlign.AUTO, content: UiAlign = UiAlign.AUTO) =
    prop(UiProps.AlignItemsHorizontal, items).prop(UiProps.AlignItemsVertical, content)

fun Modifier.grow(value: Float = 1f) = prop(UiProps.Grow, value)

fun Modifier.position(x: UiLength, y: UiLength, z: Float = 0f) = prop(UiProps.Position, UiPosition(x, y, z))

fun Modifier.background(color: UiColor) = prop(UiProps.Background, UiPaint.Color(color))

fun Modifier.background(angleDegrees: Float, stops: List<UiGradientStop>) =
    prop(UiProps.Background, UiPaint.LinearGradient(angleDegrees, stops))

fun Modifier.background(gradient: UiRadialGradient) = prop(UiProps.Background, UiPaint.RadialGradient(gradient))

fun Modifier.background(paint: UiPaint) = prop(UiProps.Background, paint)

fun Modifier.background(source: UiBoundString) = prop(UiProps.Background, UiPaint.Image(source))

fun Modifier.foreground(color: UiColor) = prop(UiProps.Foreground, color)

fun Modifier.image(source: UiBoundString) = prop(UiProps.Image, source)

fun Modifier.shader(name: UiBoundString) = prop(UiProps.Shader, name)

fun Modifier.border(width: UiLength, color: UiColor, radius: Float = 0f) =
    prop(UiProps.Border, UiBorder(UiInsets.all(width), color, radius))

fun Modifier.shadow(vararg shadows: UiShadow) = prop(UiProps.Shadows, shadows.toList())

fun Modifier.opacity(value: Float) = prop(UiProps.Opacity, value)

fun Modifier.tint(color: UiColor) = prop(UiProps.Tint, color)

fun Modifier.translate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = prop(UiProps.Translate, UiVec3(x, y, z))

fun Modifier.rotate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = prop(UiProps.Rotate, UiVec3(x, y, z))

fun Modifier.scale(x: Float, y: Float = x, z: Float = 1f) = prop(UiProps.Scale, UiVec3(x, y, z))

fun Modifier.perspective(value: Float) = prop(UiProps.Perspective, value)

fun Modifier.pivot(value: UiTransformPivot) = prop(UiProps.Pivot, value)

fun Modifier.pivot(x: UiLength, y: UiLength, z: UiLength = 0.px) = prop(UiProps.Pivot, UiTransformPivot(x, y, z))

fun Modifier.filter(vararg effects: UiFilterEffect) = prop(UiProps.Filter, UiFilterChain(effects.toList()))

fun Modifier.backdropFilter(vararg effects: UiFilterEffect) =
    prop(UiProps.BackdropFilter, UiFilterChain(effects.toList()))

fun Modifier.backfaceVisibility(value: UiBackfaceVisibility) = prop(UiProps.BackfaceVisibility, value)

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

fun Modifier.cursor(shape: UiCursorShape) = prop(UiProps.Cursor, shape)

fun Modifier.clip(enabled: Boolean = true) = prop(UiProps.Clip, enabled)

fun Modifier.clip(shape: Shape) = prop(UiProps.Clip, true).prop(UiProps.ClipShape, shape)

fun Modifier.shape(shape: Shape) = prop(UiProps.NodeShape, shape)

fun Modifier.shape(shape: Shape, fill: UiPaint?, stroke: UiPaint? = null, strokeWidth: UiLength = 0.px) =
    prop(UiProps.NodeShape, shape)
        .prop(UiProps.ShapeFill, fill)
        .prop(UiProps.ShapeStroke, stroke)
        .prop(UiProps.ShapeStrokeWidth, strokeWidth)

fun Modifier.shapeFill(paint: UiPaint) = prop(UiProps.ShapeFill, paint)

fun Modifier.shapeFill(color: UiColor) = shapeFill(UiPaint.Color(color))

fun Modifier.shapeStroke(paint: UiPaint, width: UiLength = 1.px) =
    prop(UiProps.ShapeStroke, paint).prop(UiProps.ShapeStrokeWidth, width)

fun Modifier.shapeStroke(color: UiColor, width: UiLength = 1.px) = shapeStroke(UiPaint.Color(color), width)

fun Modifier.layer(value: Int) = prop(UiProps.Layer, value)

fun Modifier.textWrap(enabled: Boolean = true) = prop(UiProps.TextWrap, enabled)

fun Modifier.textOverflow(value: UiTextOverflow) = prop(UiProps.TextOverflow, value)

fun Modifier.textAlign(value: UiTextAlign) = prop(UiProps.TextAlign, value)

fun Modifier.lineSpacing(value: Float) = prop(UiProps.LineSpacing, value.coerceAtLeast(0f))

fun Modifier.spaceWidth(value: Float?) = prop(UiProps.SpaceWidth, value?.coerceAtLeast(0f))

fun Modifier.fontSize(value: Float) = prop(UiProps.FontSize, value.coerceAtLeast(0.0001f))

fun Modifier.fontFamily(name: String) = prop(UiProps.FontFamily, name)

fun Modifier.textEffects(vararg effects: UiTextEffect) = prop(UiProps.TextEffects, effects.toList())

fun Modifier.typing(value: UiTyping?) = prop(UiProps.Typing, value)

fun Modifier.transition(vararg transitions: UiTransition) = prop(UiProps.Transitions, transitions.toList())

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

// -- Modifier utilities ------------------------------------------------------------------

fun Iterable<Modifier>.toStylePatch(): UiStylePatch {
    val style = UiStylePatch()
    forEach { (it as? UiModifierPatchNode)?.applyPatch(style) }
    return style
}

fun Iterable<Modifier>.flattenModifiers(): List<Modifier> = flatMap { modifier ->
    if (modifier is CompositeModifier) modifier.flatten() else listOf(modifier)
}

internal fun UiNode.invalidateModifierChange() {
    val phases = modifiers.flattenModifiers().invalidationPhases()
    if (phases.isEmpty() || UiInvalidationPhase.Layout in phases) {
        invalidateLayout()
        return
    }
    if (UiInvalidationPhase.Draw in phases) invalidateDraw()
    if (UiInvalidationPhase.Input in phases) invalidateInput()
}

private fun List<Modifier>.invalidationPhases(): Set<UiInvalidationPhase> {
    return flatMapTo(linkedSetOf()) { modifier ->
        when (modifier) {
            is StylePropModifier<*> -> modifier.prop.phases
            is StyleModifier -> modifier.phases
            is PointerInputModifierNode,
            is InputModifierNode -> InputPhases
            else -> LayoutPhases
        }
    }
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

private val LayoutPhases = setOf(UiInvalidationPhase.Layout)
private val InputPhases = setOf(UiInvalidationPhase.Input)
