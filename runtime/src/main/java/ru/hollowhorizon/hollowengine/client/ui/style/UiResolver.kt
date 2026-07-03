package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import java.util.*

class UiModifierResolver(
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
    ): List<UiNode> {
        val stylesheetRevision = root.stylesheetRevision()
        val treeKey = TreeCacheKey(
            root = root,
            subtreeLayoutRevision = root.layoutState.subtreeLayoutRevision,
            subtreeDrawRevision = root.layoutState.subtreeDrawRevision,
            subtreeInputRevision = root.layoutState.subtreeInputRevision,
            stylesheetRevision = stylesheetRevision,
            animate = animate,
        )
        treeCache?.takeIf { it.key == treeKey && !it.requiresRefresh }?.let { return it.nodes }
        val nodes = ArrayList<UiNode>()
        var requiresRefresh = false
        resolveNodes(root, nowMillis, animate, nodes) { snapshot ->
            if (snapshot.requiresModifierRefresh()) requiresRefresh = true
        }
        return nodes.also {
            treeCache = TreeCacheEntry(
                key = treeKey.copy(
                    subtreeLayoutRevision = root.layoutState.subtreeLayoutRevision,
                    subtreeDrawRevision = root.layoutState.subtreeDrawRevision,
                    subtreeInputRevision = root.layoutState.subtreeInputRevision,
                    stylesheetRevision = stylesheetRevision,
                ),
                nodes = nodes,
                requiresRefresh = animate && (transitions.hasActiveTransitions() || requiresRefresh),
            )
        }
    }

    private fun resolveNodes(
        root: UiNode,
        nowMillis: Long,
        animate: Boolean,
        nodes: MutableList<UiNode>,
        visitSnapshot: (UiComputedStyle) -> Unit,
    ) {
        val stack = ArrayDeque<StyleResolveTask>()
        stack.add(StyleResolveTask(root, parent = null, scope = rootScope))
        while (stack.isNotEmpty()) {
            val task = stack.removeLast()
            val node = task.node
            val modifiers = node.modifiers.flattenModifiers()
            val scopedScope = scopedStyleScope(node, task.scope, modifiers)
            val resolved = resolveModifiers(node, scopedScope, modifiers)
            val computed = resolveBaseStyle(node, task.parent, scopedScope, modifiers, resolved)
            val transitioned = if (animate) transitions.apply(node, computed, nowMillis) else computed
            val finalStyle = if (animate) {
                animations.apply(node, transitioned, scopedScope.keyframes, nowMillis)
            } else {
                transitioned
            }
            nodes += node
            node.resolvedModifiers = resolved.flat
            node.resolvedSnapshot = finalStyle
            visitSnapshot(finalStyle)
            for (index in node.children.indices.reversed()) {
                stack.add(StyleResolveTask(node.children[index], finalStyle, scopedScope))
            }
        }
    }

    /**
     * Splits the styling that applies to [node] into two layers:
     *  - the base layer (theme defaults, non-state stylesheet rules, node modifiers,
     *    attribute modifiers) that cascades last-wins;
     *  - the currently-active state rules (`:hover`, `:selected`, custom), kept as separate
     *    patches so the resolver can stack their effects with each other before overlaying
     *    them onto the base.
     * [flat] preserves the original order for node.resolvedModifiers (event/draw dispatch).
     */
    private fun resolveModifiers(
        node: UiNode,
        scope: StyleScope,
        nodeModifiers: List<Modifier>,
    ): ResolvedModifiers {
        val baseRuleModifiers = ArrayList<Modifier>()
        baseRuleModifiers += ruleModifiers(theme?.rules.orEmpty(), node, StyleOrigin.THEME_DEFAULTS)
        baseRuleModifiers += ruleModifiers(stylesheet?.rules.orEmpty(), node, StyleOrigin.STYLESHEET)
        scope.stylesheets.forEach { scoped ->
            baseRuleModifiers += ruleModifiers(scoped.rules, node, StyleOrigin.STYLESHEET)
        }

        val stateRules = ArrayList<StyleRule>()
        stateRules += matchingRules(stylesheet?.rules.orEmpty(), node, StyleOrigin.STATE_STYLESHEET)
        scope.stylesheets.forEach { scoped ->
            stateRules += matchingRules(scoped.rules, node, StyleOrigin.STATE_STYLESHEET)
        }
        val orderedStateRules = stateRules.sortedWith(compareBy<StyleRule> { it.selector.specificity }.thenBy { it.order })

        val attributeModifiers = attributeModifiers(node)
        val baseModifiers = baseRuleModifiers + nodeModifiers + attributeModifiers

        // Flat list keeps the historical dispatch order (base rules, then state rules, then
        // node/attribute modifiers).
        val flat = ArrayList<Modifier>(baseRuleModifiers.size + stateRules.size + nodeModifiers.size + attributeModifiers.size)
        flat += baseRuleModifiers
        orderedStateRules.forEach { flat += it.patch.modifiers() }
        flat += nodeModifiers
        flat += attributeModifiers

        return ResolvedModifiers(
            flat = flat,
            baseModifiers = baseModifiers,
            stateRulePatches = orderedStateRules.map { it.patch.modifiers().toStylePatch() },
        )
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
        parent: UiComputedStyle?,
        scope: StyleScope,
        modifiers: List<Modifier>,
        resolved: ResolvedModifiers,
    ): UiComputedStyle {
        val key = StyleCacheKey(
            scopeId = scope.id,
            parent = parent,
            node = node.styleSnapshot(modifiers),
            resolvedModifiers = resolved.flat,
        )
        styleCache[node]?.takeIf { it.key == key }?.let {
            node.layoutState.updateResolvedLayoutFingerprint(it.snapshot.layoutFingerprint())
            return it.snapshot
        }
        val mutable = engineDefaults(node)
        mutable.merge(resolved.baseModifiers.toStylePatch())
        if (resolved.stateRulePatches.isNotEmpty()) {
            // Active states stack with each other, then overlay the base (base never stacks).
            val combinedStates = UiStylePatch()
            resolved.stateRulePatches.forEach { combinedStates.combineWith(it) }
            mutable.merge(combinedStates)
        }
        return mutable.resolve(parent).also { style ->
            node.layoutState.updateResolvedLayoutFingerprint(style.layoutFingerprint())
            styleCache[node] = StyleCacheEntry(key, style)
        }
    }

    private fun ruleModifiers(rules: List<StyleRule>, node: UiNode, origin: StyleOrigin): List<Modifier> {
        return matchingRules(rules, node, origin)
            .sortedWith(compareBy<StyleRule> { it.selector.specificity }.thenBy { it.order })
            .flatMap { it.patch.modifiers() }
    }

    private fun matchingRules(rules: List<StyleRule>, node: UiNode, origin: StyleOrigin): List<StyleRule> =
        rules.filter { it.origin == origin && it.matches(node) }

    private fun attributeModifiers(node: UiNode): List<Modifier> {
        return node.attributes.mapNotNull { (name, value) ->
            if (node.isWidgetConfigurationAttribute(name)) null else compileStyleModifier(name, value)
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

    private fun engineDefaults(node: UiNode): UiStylePatch {
        val style = UiStylePatch()
        style.transitions = DefaultTransformTransitions
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

private class ResolvedModifiers(
    val flat: List<Modifier>,
    val baseModifiers: List<Modifier>,
    val stateRulePatches: List<UiStylePatch>,
)

private data class StyleScope(
    val stylesheets: List<CompiledHss>,
    val keyframes: Map<String, UiKeyframes>,
    val id: Long,
)

private data class StyleResolveTask(
    val node: UiNode,
    val parent: UiComputedStyle?,
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
    val parent: UiComputedStyle?,
    val node: NodeStyleSnapshot,
    val resolvedModifiers: List<Modifier>,
)

private data class StyleCacheEntry(
    val key: StyleCacheKey,
    val snapshot: UiComputedStyle,
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
    val nodes: List<UiNode>,
    val requiresRefresh: Boolean,
)

private fun UiComputedStyle.requiresModifierRefresh(): Boolean {
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
