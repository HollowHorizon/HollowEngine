package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.layoutFingerprint
import ru.hollowhorizon.hollowengine.client.ui.widgets.CheckboxNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.SliderNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCheckboxStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiSliderStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextFieldStyle
import java.util.*

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
    private val styleCache = WeakHashMap<UiNode, StyleCacheEntry>()
    private val scopeCache = WeakHashMap<UiNode, ScopeCacheEntry>()
    private var treeCache: TreeCacheEntry? = null
    private var nextScopeId = 1L
    private val rootScope = StyleScope(
        stylesheets = emptyList(),
        keyframes = buildMap {
            theme?.keyframes?.let(::putAll)
            stylesheet?.keyframes?.let(::putAll)
        },
        id = 0L,
    )

    fun resolve(
        root: UiNode,
        nowMillis: Long = 0L,
        animate: Boolean = true,
    ): ResolvedUiTree {
        val stylesheetRevision = root.stylesheetRevision()
        val treeKey = TreeCacheKey(
            root = root,
            subtreeLayoutRevision = root.layoutState.subtreeLayoutRevision,
            subtreeDrawRevision = root.layoutState.subtreeDrawRevision,
            subtreeInputRevision = root.layoutState.subtreeInputRevision,
            stylesheetRevision = stylesheetRevision,
            animate = animate,
        )
        treeCache?.takeIf { it.key == treeKey && !it.requiresRefresh }?.let { return it.tree }
        val styles = linkedMapOf<UiNode, ComputedStyle>()
        resolveNodes(root, nowMillis, animate, styles)
        return ResolvedUiTree(root, styles).also { tree ->
            treeCache = TreeCacheEntry(
                key = treeKey.copy(
                    subtreeLayoutRevision = root.layoutState.subtreeLayoutRevision,
                    subtreeDrawRevision = root.layoutState.subtreeDrawRevision,
                    subtreeInputRevision = root.layoutState.subtreeInputRevision,
                    stylesheetRevision = stylesheetRevision,
                ),
                tree = tree,
                requiresRefresh = animate && (transitions.hasActiveTransitions() || styles.values.any { it.requiresStyleRefresh() }),
            )
        }
    }

    private fun resolveNodes(
        root: UiNode,
        nowMillis: Long,
        animate: Boolean,
        styles: MutableMap<UiNode, ComputedStyle>,
    ) {
        val stack = ArrayDeque<StyleResolveTask>()
        stack.add(StyleResolveTask(root, parent = null, scope = rootScope))
        while (stack.isNotEmpty()) {
            val task = stack.removeLast()
            val node = task.node
            val modifiers = node.modifiers.flattenModifiers()
            val scopedScope = scopedStyleScope(node, task.scope, modifiers)
            val computed = resolveBaseStyle(node, task.parent, scopedScope, modifiers)
            val transitioned = if (animate) transitions.apply(node, computed, nowMillis) else computed
            val finalStyle = if (animate) {
                animations.apply(node, transitioned, scopedScope.keyframes, nowMillis)
            } else {
                transitioned
            }
            styles[node] = finalStyle
            for (index in node.children.indices.reversed()) {
                stack.add(StyleResolveTask(node.children[index], finalStyle, scopedScope))
            }
        }
    }

    private fun scopedStyleScope(
        node: UiNode,
        inheritedScope: StyleScope,
        modifiers: List<Modifier>,
    ): StyleScope {
        val imports = modifiers.filterIsInstance<StyleImportModifier>()
        if (imports.isEmpty()) return inheritedScope
        val snapshots = imports.map { StyleImportSnapshot(it.reference, it.reference.revision()) }
        val key = ScopeCacheKey(inheritedScope.id, snapshots)
        scopeCache[node]?.takeIf { it.key == key }?.let { return it.scope }
        val stylesheets = ArrayList<CompiledHss>(inheritedScope.stylesheets.size + imports.size)
        stylesheets += inheritedScope.stylesheets
        val keyframes = LinkedHashMap(inheritedScope.keyframes)
        imports.forEach { modifier ->
            val stylesheet = modifier.reference.resolve()
            stylesheets += stylesheet
            keyframes.putAll(stylesheet.keyframes)
        }
        return StyleScope(stylesheets, keyframes, nextScopeId++).also { scope ->
            scopeCache[node] = ScopeCacheEntry(key, scope)
        }
    }

    private fun resolveBaseStyle(
        node: UiNode,
        parent: ComputedStyle?,
        scope: StyleScope,
        modifiers: List<Modifier>,
    ): ComputedStyle {
        val key = StyleCacheKey(
            scopeId = scope.id,
            parent = parent,
            node = node.styleSnapshot(modifiers),
        )
        styleCache[node]?.takeIf { it.key == key }?.let {
            node.layoutState.updateResolvedLayoutFingerprint(it.style.layoutFingerprint())
            return it.style
        }
        val mutable = engineDefaults(node)
        applyRules(theme?.rules.orEmpty(), node, mutable, StyleOrigin.THEME_DEFAULTS)
        applyRules(stylesheet?.rules.orEmpty(), node, mutable, StyleOrigin.STYLESHEET)
        scope.stylesheets.forEach { scoped ->
            applyRules(scoped.rules, node, mutable, StyleOrigin.STYLESHEET)
        }
        applyRules(stylesheet?.rules.orEmpty(), node, mutable, StyleOrigin.STATE_STYLESHEET)
        scope.stylesheets.forEach { scoped ->
            applyRules(scoped.rules, node, mutable, StyleOrigin.STATE_STYLESHEET)
        }
        mutable.merge(modifiers.style())
        applyAttributeStyles(node, mutable)
        return mutable.toComputed(parent).also { style ->
            node.layoutState.updateResolvedLayoutFingerprint(style.layoutFingerprint())
            styleCache[node] = StyleCacheEntry(key, style)
        }
    }

    private fun applyRules(
        rules: List<StyleRule>,
        node: UiNode,
        target: MutableUiStyle,
        origin: StyleOrigin,
    ) {
        rules.asSequence().filter { it.origin == origin && it.matches(node) }
            .sortedWith(compareBy<StyleRule> { it.selector.specificity }.thenBy { it.order })
            .forEach { it.patch.apply(target) }
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
            UiTextType -> {
                style.foreground = UiColor.White
                style.size = UiSize(UiLength.Auto, UiLength.Auto)
                style.minSize = UiSize(0.px, 0.px)
            }

            UiImageType,
            UiItemType,
            UiEntityType,
                -> {
                style.size = UiSize(16.px, 16.px)
            }

            UiSliderType -> {
                style.size = UiSize(120.px, 16.px)
                style.input = UiInputStyle(hoverable = true, clickable = true, draggable = true, focusable = true)
                style.slider = UiSliderStyle()
            }

            UiCheckboxType -> {
                style.size = UiSize(16.px, 16.px)
                style.input = UiInputStyle(hoverable = true, clickable = true, focusable = true)
                style.checkbox = UiCheckboxStyle()
            }

            UiTextFieldType -> {
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

private data class StyleScope(
    val stylesheets: List<CompiledHss>,
    val keyframes: Map<String, UiKeyframes>,
    val id: Long,
)

private data class StyleResolveTask(
    val node: UiNode,
    val parent: ComputedStyle?,
    val scope: StyleScope,
)

private data class StyleImportSnapshot(
    val reference: UiStylesheetReference,
    val revision: Long,
)

private data class ScopeCacheKey(
    val inheritedScopeId: Long,
    val imports: List<StyleImportSnapshot>,
)

private data class ScopeCacheEntry(
    val key: ScopeCacheKey,
    val scope: StyleScope,
)

private data class NodeStyleSnapshot(
    val nodeClass: Class<out UiNode>,
    val type: String,
    val id: String?,
    val tags: Set<String>,
    val states: Set<UiState>,
    val attributes: Map<String, String>,
    val modifiers: List<Modifier>,
)

private data class StyleCacheKey(
    val scopeId: Long,
    val parent: ComputedStyle?,
    val node: NodeStyleSnapshot,
)

private data class StyleCacheEntry(
    val key: StyleCacheKey,
    val style: ComputedStyle,
)

private data class TreeCacheKey(
    val root: UiNode,
    val subtreeLayoutRevision: Long,
    val subtreeDrawRevision: Long,
    val subtreeInputRevision: Long,
    val stylesheetRevision: Long,
    val animate: Boolean,
)

private data class TreeCacheEntry(
    val key: TreeCacheKey,
    val tree: ResolvedUiTree,
    val requiresRefresh: Boolean,
)

private fun ComputedStyle.requiresStyleRefresh(): Boolean {
    return animations.any { animation -> animation.totalDurationMillis()?.let { it > 0L } ?: true }
}

private fun UiNode.styleSnapshot(modifiers: List<Modifier>) = NodeStyleSnapshot(
    nodeClass = javaClass,
    type = type,
    id = id,
    tags = tags.toSet(),
    states = effectiveStates(),
    attributes = attributes.toMap(),
    modifiers = modifiers,
)

private fun List<Modifier>.style(): MutableUiStyle {
    val style = MutableUiStyle()
    forEach { it.applyTo(style) }
    return style
}
