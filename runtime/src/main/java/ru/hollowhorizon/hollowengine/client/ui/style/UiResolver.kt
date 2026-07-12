package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollbarThumbType
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollbarType
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import java.util.*

class UiModifierResolver(
    private val theme: CompiledHss? = null,
    private val stylesheet: CompiledHss? = null,
    private val transitions: UiTransitionState = UiTransitionState(),
    private val animations: UiAnimationState = UiAnimationState(),
) {
    private val styleCache = WeakHashMap<UiNode, StyleCacheEntry>()
    private val modifierCache = WeakHashMap<UiNode, ModifierCacheEntry>()
    private val scopeCache = WeakHashMap<UiNode, ScopeCacheEntry>()
    private val resolveStack = StyleResolveStack()
    private val stylesheetStack = ArrayDeque<UiNode>()
    private val stylesheetReferences = ArrayList<UiStylesheetReference>()
    private var stylesheetRoot: UiNode? = null
    private var stylesheetTreeDrawRevision = Long.MIN_VALUE
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
        val stylesheetRevision = stylesheetRevision(root)
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
        val stack = resolveStack
        stack.clear()
        stack.push(root, parent = null, scope = rootScope, ancestorRevision = 0L)
        while (stack.isNotEmpty()) {
            stack.pop()
            val node = stack.currentNode
            val parent = stack.currentParent
            val inheritedScope = stack.currentScope
            val ancestorRevision = stack.currentAncestorRevision
            val layoutState = node.layoutState
            val modifiers = flattenedModifiers(node, layoutState.drawRevision)
            val scopedScope = scopedStyleScope(node, inheritedScope, modifiers)
            val resolved = resolveBaseStyle(
                node,
                parent,
                scopedScope,
                modifiers,
                ancestorRevision,
                layoutState.drawRevision,
            )
            val transitioned = if (animate) transitions.apply(node, resolved.snapshot, nowMillis) else resolved.snapshot
            val finalStyle = if (animate) {
                animations.apply(node, transitioned, scopedScope.keyframes, nowMillis)
            } else {
                transitioned
            }
            nodes += node
            node.resolvedModifiers = resolved.modifiers.flat
            node.resolvedSnapshot = finalStyle
            // Fingerprint the transitioned + animated style, not the base: an animation on a
            // layout prop (size/padding/transform) must bump the layout revision so the frame rebuilds
            // it, while a draw-only animation (colour/opacity) leaves the fingerprint and the layout untouched.
            node.layoutState.updateResolvedLayoutFingerprint(finalStyle.layoutFingerprint())
            visitSnapshot(finalStyle)
            val descendantRevision = maxOf(
                ancestorRevision,
                layoutState.drawRevision,
            )
            for (index in node.children.indices.reversed()) {
                stack.push(node.children[index], finalStyle, scopedScope, descendantRevision)
            }
        }
    }

    private fun flattenedModifiers(
        node: UiNode,
        drawRevision: Long,
    ): List<Modifier> {
        // Modifier/tags/state/attribute collections invalidate drawRevision. layoutRevision also
        // changes for every frame of a layout animation, so using it here would defeat this cache.
        modifierCache[node]?.takeIf { it.drawRevision == drawRevision }?.let { return it.modifiers }
        return node.modifiers.flattenModifiers().also { modifiers ->
            modifierCache[node] = ModifierCacheEntry(drawRevision, modifiers)
        }
    }

    private fun stylesheetRevision(root: UiNode): Long {
        val drawRevision = root.layoutState.subtreeDrawRevision
        // Imports only need rediscovery after a source-style change; resource revisions themselves
        // are still sampled every frame so a resource reload invalidates the tree immediately.
        if (stylesheetRoot !== root || stylesheetTreeDrawRevision != drawRevision) {
            stylesheetRoot = root
            stylesheetTreeDrawRevision = drawRevision
            stylesheetReferences.clear()
            stylesheetStack.clear()
            stylesheetStack.add(root)
            while (stylesheetStack.isNotEmpty()) {
                val node = stylesheetStack.removeLast()
                for (modifier in flattenedModifiers(node, node.layoutState.drawRevision)) {
                    if (modifier is StyleImportModifier) stylesheetReferences += modifier.reference
                }
                for (child in node.children) stylesheetStack.add(child)
            }
        }

        var revision = 1L
        for (reference in stylesheetReferences) revision = revision * 31L + reference.revision()
        return revision
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
        baseRuleModifiers += ruleModifiers(theme, node, StyleOrigin.THEME_DEFAULTS)
        baseRuleModifiers += ruleModifiers(stylesheet, node, StyleOrigin.STYLESHEET)
        scope.stylesheets.forEach { scoped ->
            baseRuleModifiers += ruleModifiers(scoped, node, StyleOrigin.STYLESHEET)
        }

        val stateRules = ArrayList<StyleRule>()
        stylesheet?.let { stateRules += it.matching(node, StyleOrigin.STATE_STYLESHEET) }
        scope.stylesheets.forEach { scoped ->
            stateRules += scoped.matching(node, StyleOrigin.STATE_STYLESHEET)
        }
        val orderedStateRules =
            stateRules.sortedWith(compareBy<StyleRule> { it.selector.specificity }.thenBy { it.order })

        val attributeModifiers = attributeModifiers(node)
        val baseModifiers = baseRuleModifiers + nodeModifiers + attributeModifiers

        // Flat list keeps the historical dispatch order (base rules, then state rules, then
        // node/attribute modifiers).
        val flat =
            ArrayList<Modifier>(baseRuleModifiers.size + stateRules.size + nodeModifiers.size + attributeModifiers.size)
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
        var imports: ArrayList<StyleImportModifier>? = null
        for (modifier in modifiers) {
            if (modifier !is StyleImportModifier) continue
            if (imports == null) imports = ArrayList()
            imports += modifier
        }
        val resolvedImports = imports ?: return inheritedScope
        scopeCache[node]?.takeIf { it.matches(inheritedScope.id, resolvedImports) }?.let { return it.scope }
        val snapshots = resolvedImports.map { StyleImportSnapshot(it.reference, it.reference.revision()) }
        val key = ScopeCacheKey(inheritedScope.id, snapshots)
        val stylesheets = ArrayList<CompiledHss>(inheritedScope.stylesheets.size + resolvedImports.size)
        stylesheets += inheritedScope.stylesheets
        val keyframes = LinkedHashMap(inheritedScope.keyframes)
        resolvedImports.forEach { modifier ->
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
        ancestorRevision: Long,
        drawRevision: Long,
    ): ResolvedNodeStyle {
        styleCache[node]?.takeIf {
            it.key.matches(
                scope.id,
                parent,
                ancestorRevision,
                drawRevision,
            )
        }?.let {
            return it.resolved
        }
        val key = StyleCacheKey(
            scopeId = scope.id,
            parent = parent,
            ancestorRevision = ancestorRevision,
            drawRevision = drawRevision,
        )
        val resolved = resolveModifiers(node, scope, modifiers)
        val mutable = engineDefaults(node)
        mutable.merge(resolved.baseModifiers.toStylePatch())
        if (resolved.stateRulePatches.isNotEmpty()) {
            // Active states stack with each other, then overlay the base (base never stacks).
            val combinedStates = UiStylePatch()
            resolved.stateRulePatches.forEach { combinedStates.combineWith(it) }
            mutable.merge(combinedStates)
        }
        return ResolvedNodeStyle(resolved, mutable.resolve(parent)).also { style ->
            styleCache[node] = StyleCacheEntry(key, style)
        }
    }

    private fun ruleModifiers(hss: CompiledHss?, node: UiNode, origin: StyleOrigin): List<Modifier> {
        hss ?: return emptyList()
        return hss.matching(node, origin)
            .sortedWith(compareBy<StyleRule> { it.selector.specificity }.thenBy { it.order })
            .flatMap { it.patch.modifiers() }
    }

    private fun attributeModifiers(node: UiNode): List<Modifier> {
        return node.attributes.mapNotNull { (name, value) -> compileStyleModifier(name, value) }
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

            UiScrollbarType -> {
                style.background = UiPaint.Color(UiColor(0f, 0f, 0f, 0.42f))
                style.borderRadius = 3.5f
            }

            UiScrollbarThumbType -> {
                style.background = UiPaint.Color(UiColor(0.78f, 0.84f, 0.94f, 0.9f))
                style.borderRadius = 3.5f
                style.clickable = true
                style.draggable = true
                style.hoverable = true
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

private class ResolvedNodeStyle(
    val modifiers: ResolvedModifiers,
    val snapshot: UiComputedStyle,
)

private data class StyleScope(
    val stylesheets: List<CompiledHss>,
    val keyframes: Map<String, UiKeyframes>,
    val id: Long,
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
) {
    fun matches(inheritedScopeId: Long, imports: List<StyleImportModifier>): Boolean {
        if (key.inheritedScopeId != inheritedScopeId || key.imports.size != imports.size) return false
        for (index in imports.indices) {
            val snapshot = key.imports[index]
            val reference = imports[index].reference
            if (snapshot.reference != reference || snapshot.revision != reference.revision()) return false
        }
        return true
    }
}

private class StyleCacheKey(
    val scopeId: Long,
    val parent: UiComputedStyle?,
    val ancestorRevision: Long,
    val drawRevision: Long,
) {
    fun matches(
        scopeId: Long,
        parent: UiComputedStyle?,
        ancestorRevision: Long,
        drawRevision: Long,
    ): Boolean {
        return this.scopeId == scopeId &&
                this.parent === parent &&
                this.ancestorRevision == ancestorRevision &&
                this.drawRevision == drawRevision
    }
}

private data class StyleCacheEntry(
    val key: StyleCacheKey,
    val resolved: ResolvedNodeStyle,
)

private class ModifierCacheEntry(
    val drawRevision: Long,
    val modifiers: List<Modifier>,
)

private class StyleResolveStack(initialCapacity: Int = 64) {
    private var nodes = arrayOfNulls<UiNode>(initialCapacity)
    private var parents = arrayOfNulls<UiComputedStyle>(initialCapacity)
    private var scopes = arrayOfNulls<StyleScope>(initialCapacity)
    private var ancestorRevisions = LongArray(initialCapacity)
    private var size = 0

    lateinit var currentNode: UiNode
        private set
    var currentParent: UiComputedStyle? = null
        private set
    lateinit var currentScope: StyleScope
        private set
    var currentAncestorRevision: Long = 0L
        private set

    fun isNotEmpty(): Boolean = size > 0

    fun clear() {
        for (index in 0 until size) {
            nodes[index] = null
            parents[index] = null
            scopes[index] = null
        }
        size = 0
    }

    fun push(node: UiNode, parent: UiComputedStyle?, scope: StyleScope, ancestorRevision: Long) {
        ensureCapacity(size + 1)
        nodes[size] = node
        parents[size] = parent
        scopes[size] = scope
        ancestorRevisions[size] = ancestorRevision
        size++
    }

    fun pop() {
        val index = --size
        currentNode = nodes[index]!!
        currentParent = parents[index]
        currentScope = scopes[index]!!
        currentAncestorRevision = ancestorRevisions[index]
        nodes[index] = null
        parents[index] = null
        scopes[index] = null
    }

    private fun ensureCapacity(required: Int) {
        if (required <= nodes.size) return
        val next = maxOf(required, nodes.size * 2)
        nodes = nodes.copyOf(next)
        parents = parents.copyOf(next)
        scopes = scopes.copyOf(next)
        ancestorRevisions = ancestorRevisions.copyOf(next)
    }
}

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
