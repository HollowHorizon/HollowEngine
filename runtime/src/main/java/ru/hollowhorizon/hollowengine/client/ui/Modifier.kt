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
            StyleModifier(setOf(UiStyleProperty.WIDTH, UiStyleProperty.HEIGHT)) { it.size = UiSize(width, height) }

        fun minSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier { it.minSize = UiSize(width, height) }

        fun maxSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier { it.maxSize = UiSize(width, height) }

        fun aspectRatio(value: Float) = StyleModifier { it.aspectRatio = value }

        fun padding(value: UiLength) = StyleModifier { it.padding = UiInsets.all(value) }

        fun padding(horizontal: UiLength, vertical: UiLength) =
            StyleModifier { it.padding = UiInsets.hv(horizontal, vertical) }

        fun padding(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) =
            StyleModifier { it.padding = UiInsets(left, top, right, bottom) }

        fun margin(value: UiLength) = StyleModifier { it.margin = UiInsets.all(value) }

        fun margin(horizontal: UiLength, vertical: UiLength) =
            StyleModifier { it.margin = UiInsets.hv(horizontal, vertical) }

        fun margin(left: UiLength, top: UiLength, right: UiLength, bottom: UiLength) =
            StyleModifier { it.margin = UiInsets(left, top, right, bottom) }

        fun gap(value: UiLength) = StyleModifier { it.gap = value }

        fun align(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) = StyleModifier {
            it.alignHorizontal = horizontal
            it.alignVertical = vertical
        }

        fun alignItems(horizontal: UiAlign = UiAlign.AUTO, vertical: UiAlign = UiAlign.AUTO) = StyleModifier {
            it.alignItemsHorizontal = horizontal
            it.alignItemsVertical = vertical
        }

        fun alignChildren(items: UiAlign = UiAlign.AUTO, content: UiAlign = UiAlign.AUTO) = StyleModifier {
            it.alignItemsHorizontal = items
            it.alignItemsVertical = content
        }

        fun grow(value: Float = 1f) = StyleModifier { it.grow = value }

        fun position(x: UiLength, y: UiLength, z: Float = 0f) = PositionModifier(x, y, z)

        fun background(color: UiColor) = StyleModifier { it.background = UiPaint.Color(color) }

        fun background(angleDegrees: Float, stops: List<UiGradientStop>) =
            StyleModifier { it.background = UiPaint.LinearGradient(angleDegrees, stops) }

        fun background(source: UiBoundString) = StyleModifier { it.background = UiPaint.Image(source) }

        fun foreground(color: UiColor) = StyleModifier { it.foreground = color }

        fun image(source: UiBoundString) = StyleModifier { it.image = source }

        fun shader(name: UiBoundString) = StyleModifier { it.shader = name }

        fun border(width: UiLength, color: UiColor, radius: Float = 0f) =
            StyleModifier { it.border = UiBorder(UiInsets.all(width), color, radius) }

        fun shadow(vararg shadows: UiShadow) = StyleModifier { it.shadows = shadows.toList() }

        fun opacity(value: Float) = StyleModifier { it.opacity = value }

        fun tint(color: UiColor) = StyleModifier { it.tint = color }

        fun translate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = TransformPatch { current ->
            current.copy(translate = UiVec3(x, y, z))
        }

        fun rotate(x: Float = 0f, y: Float = 0f, z: Float = 0f) = TransformPatch { current ->
            current.copy(rotate = UiVec3(x, y, z))
        }

        fun scale(x: Float, y: Float = x, z: Float = 1f) = TransformPatch { current ->
            current.copy(scale = UiVec3(x, y, z))
        }

        fun perspective(value: Float) = TransformPatch { current -> current.copy(perspective = value) }

        fun pivot(value: UiTransformPivot) = TransformPatch { current -> current.copy(pivot = value) }

        fun pivot(x: UiLength, y: UiLength, z: UiLength = 0.px) =
            TransformPatch { current -> current.copy(pivot = UiTransformPivot(x, y, z)) }

        fun filter(vararg effects: UiFilterEffect) = StyleModifier { it.filter = UiFilterChain(effects.toList()) }

        fun backdropFilter(vararg effects: UiFilterEffect) =
            StyleModifier { it.backdropFilter = UiFilterChain(effects.toList()) }

        fun backfaceVisibility(value: UiBackfaceVisibility) = StyleModifier { it.backfaceVisibility = value }

        fun input(
            hoverable: Boolean = false,
            clickable: Boolean = false,
            focusable: Boolean = false,
            draggable: Boolean = false,
            scrollable: Boolean = false,
        ) = StyleModifier {
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

        fun cursor(shape: UiCursorShape) = StyleModifier { it.cursor = shape }

        fun clip(enabled: Boolean = true) = StyleModifier { it.clip = enabled }

        fun layer(value: Int) = StyleModifier { it.layer = value }

        fun textWrap(enabled: Boolean = true) = StyleModifier { it.textWrap = enabled }

        fun textAlign(value: UiTextAlign) = StyleModifier { it.textAlign = value }

        fun fontSize(value: Float) = StyleModifier { it.fontSize = value.coerceAtLeast(0.0001f) }

        fun fontFamily(name: String) = StyleModifier { it.fontFamily = name }

        fun textEffects(vararg effects: UiTextEffect) = StyleModifier { it.textEffects = effects.toList() }

        fun typing(value: UiTyping?) = StyleModifier { it.typing = value }

        fun transition(vararg transitions: UiTransition) = StyleModifier { it.transitions = transitions.toList() }

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

        fun then(vararg modifiers: Modifier) = CompositeModifier(modifiers.toList())
    }
}

enum class UiStyleProperty {
    WIDTH,
    HEIGHT,
    TRANSITIONS
}

class StyleModifier(
    val properties: Set<UiStyleProperty> = emptySet(),
    private val writer: (MutableUiStyle) -> Unit,
) : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        writer(style)
        if (properties.isNotEmpty()) style.explicitProperties = style.explicitProperties.orEmpty() + properties
    }
}

data class CompositeModifier(private val values: List<Modifier>) : Modifier {
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

data class TransformPatch(private val patch: (UiTransform) -> UiTransform) : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        style.transform = patch(style.transform ?: UiTransform())
    }
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
