package ru.hollowhorizon.hollowengine.client.ui

sealed interface Modifier {
    fun applyTo(style: MutableUiStyle)

    companion object {
        fun layout(type: LayoutType) = StyleModifier { it.layout = type }

        fun size(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier { it.size = UiSize(width, height) }

        fun minSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier { it.minSize = UiSize(width, height) }

        fun maxSize(width: UiLength = UiLength.Auto, height: UiLength = UiLength.Auto) =
            StyleModifier { it.maxSize = UiSize(width, height) }

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

        fun align(self: UiAlign = UiAlign.AUTO, children: UiAlign = UiAlign.AUTO, content: UiAlign = UiAlign.AUTO) = StyleModifier {
            it.alignSelf = self
            it.alignItems = children
            it.justifyContent = content
        }

        fun alignChildren(items: UiAlign = UiAlign.AUTO, content: UiAlign = UiAlign.AUTO) = StyleModifier {
            it.alignItems = items
            it.justifyContent = content
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

        fun clip(enabled: Boolean = true) = StyleModifier { it.clip = enabled }

        fun layer(value: Int) = StyleModifier { it.layer = value }

        fun transition(vararg transitions: UiTransition) = StyleModifier { it.transitions = transitions.toList() }

        fun then(vararg modifiers: Modifier) = CompositeModifier(modifiers.toList())
    }
}

class StyleModifier(private val writer: (MutableUiStyle) -> Unit) : Modifier {
    override fun applyTo(style: MutableUiStyle) = writer(style)
}

data class CompositeModifier(private val values: List<Modifier>) : Modifier {
    override fun applyTo(style: MutableUiStyle) {
        values.forEach { it.applyTo(style) }
    }
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

fun String.bound() = UiBoundString(this)
