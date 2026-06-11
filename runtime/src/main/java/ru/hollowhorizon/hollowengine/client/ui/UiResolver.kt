package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.StyleRule
import ru.hollowhorizon.hollowengine.client.ui.hss.compileStyleModifier

data class ResolvedUiTree(
    val root: UiNode,
    val styles: Map<UiNode, ComputedStyle>,
) {
    operator fun get(node: UiNode): ComputedStyle = styles.getValue(node)
}

class UiStyleResolver(
    private val theme: CompiledHss? = null,
    private val stylesheet: CompiledHss? = null,
    private val transitions: UiTransitionState = UiTransitionState(),
    private val animations: UiAnimationState = UiAnimationState(),
) {
    fun resolve(
        root: UiNode,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
        animate: Boolean = true,
    ): ResolvedUiTree {
        val styles = linkedMapOf<UiNode, ComputedStyle>()
        resolveNode(root, null, emptyList(), bindings, nowMillis, animate, styles)
        return ResolvedUiTree(root, styles)
    }

    private fun resolveNode(
        node: UiNode,
        parent: ComputedStyle?,
        inheritedStylesheets: List<CompiledHss>,
        bindings: UiBindingContext,
        nowMillis: Long,
        animate: Boolean,
        styles: MutableMap<UiNode, ComputedStyle>,
    ) {
        val scopedStylesheets = inheritedStylesheets + node.modifiers.flattenModifiers()
            .filterIsInstance<StyleImportModifier>()
            .map { it.reference.resolve() }
        val mutable = engineDefaults(node)
        applyRules(theme?.rules.orEmpty(), node, bindings, mutable, StyleOrigin.THEME_DEFAULTS)
        applyRules(stylesheet?.rules.orEmpty(), node, bindings, mutable, StyleOrigin.STYLESHEET)
        scopedStylesheets.forEach { scoped ->
            applyRules(scoped.rules, node, bindings, mutable, StyleOrigin.STYLESHEET)
        }
        applyRules(stylesheet?.rules.orEmpty(), node, bindings, mutable, StyleOrigin.STATE_STYLESHEET)
        scopedStylesheets.forEach { scoped ->
            applyRules(scoped.rules, node, bindings, mutable, StyleOrigin.STATE_STYLESHEET)
        }
        mutable.merge(node.modifiers.style())
        applyAttributeStyles(node, mutable)
        val computed = mutable.toComputed(parent)
        val keyframes = buildMap {
            theme?.keyframes?.let(::putAll)
            stylesheet?.keyframes?.let(::putAll)
            scopedStylesheets.forEach { putAll(it.keyframes) }
        }
        val transitioned = if (animate) transitions.apply(node, computed, nowMillis) else computed
        val finalStyle = if (animate) animations.apply(node, transitioned, keyframes, nowMillis) else transitioned
        styles[node] = finalStyle
        node.children.forEach { child ->
            resolveNode(child, finalStyle, scopedStylesheets, bindings, nowMillis, animate, styles)
        }
    }

    private fun applyRules(
        rules: List<StyleRule>,
        node: UiNode,
        bindings: UiBindingContext,
        target: MutableUiStyle,
        origin: StyleOrigin,
    ) {
        rules.asSequence().filter { it.origin == origin && it.matches(node) }
            .sortedWith(compareBy<StyleRule> { it.selector.specificity }.thenBy { it.order })
            .forEach { it.patch.apply(target, bindings) }
    }

    private fun applyAttributeStyles(node: UiNode, target: MutableUiStyle) {
        node.attributes.forEach { (name, value) ->
            if (node.isWidgetConfigurationAttribute(name)) return@forEach
            compileStyleModifier(name, value)?.applyTo(target)
        }
    }

    private fun UiNode.isWidgetConfigurationAttribute(name: String): Boolean {
        val normalized = name.replace('_', '-')
        return when (this) {
            is SliderNode -> normalized in setOf("value", "min", "max", "step")
            is CheckboxNode -> normalized in setOf("value", "checked", "variant", "style", "type")
            is TextFieldNode -> normalized in setOf(
                "value",
                "text",
                "mode",
                "multiline",
                "multi-line",
                "filter",
                "input-filter",
                "multi-caret",
                "multicaret",
                "placeholder",
                "hint",
            )
            else -> false
        }
    }

    private fun engineDefaults(node: UiNode): MutableUiStyle {
        val style = MutableUiStyle(transitions = DefaultTransformTransitions)
        when (node.type) {
            UiNodeType.TEXT.typeName -> {
                style.foreground = UiColor.White
                style.size = UiSize(UiLength.Auto, UiLength.Auto)
                style.minSize = UiSize(0.px, 0.px)
            }

            UiNodeType.IMAGE.typeName,
            UiNodeType.ITEM.typeName,
            UiNodeType.ENTITY.typeName,
            UiNodeType.CANVAS.typeName,
                -> {
                style.size = UiSize(16.px, 16.px)
            }

            UiNodeType.SLIDER.typeName -> {
                style.size = UiSize(120.px, 16.px)
                style.input = UiInputStyle(hoverable = true, clickable = true, draggable = true, focusable = true)
                style.slider = UiSliderStyle()
            }

            UiNodeType.CHECKBOX.typeName -> {
                style.size = UiSize(16.px, 16.px)
                style.input = UiInputStyle(hoverable = true, clickable = true, focusable = true)
                style.checkbox = UiCheckboxStyle()
            }

            UiNodeType.TEXT_FIELD.typeName -> {
                style.size = UiSize(UiLength.Auto, UiLength.Auto)
                style.minSize = UiSize(0.px, 18.px)
                style.padding = UiInsets.hv(4.px, 3.px)
                style.foreground = UiColor.White
                style.input = UiInputStyle(hoverable = true, clickable = true, focusable = true)
                style.textField = UiTextFieldStyle()
            }
        }
        return style
    }

    companion object {
        private val DefaultTransformTransitions = listOf(
            UiTransition("translate", 200L, TransitionEasing.EASE_OUT),
            UiTransition("rotate", 200L, TransitionEasing.EASE_OUT),
            UiTransition("scale", 200L, TransitionEasing.EASE_OUT),
            UiTransition("perspective", 200L, TransitionEasing.EASE_OUT),
        )
    }
}
