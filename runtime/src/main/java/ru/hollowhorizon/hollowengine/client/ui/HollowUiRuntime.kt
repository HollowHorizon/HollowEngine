package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss

data class HollowUiFrame(
    val resolved: ResolvedUiTree,
    val layout: UiLayoutResult,
    val commands: List<UiRenderCommand>,
) {
    fun hitTest(x: Float, y: Float): UiHit? = UiHitTester().hitTest(resolved, layout, x, y)
}

class HollowUiRuntime(
    theme: CompiledHss? = null,
    stylesheet: CompiledHss? = null,
    private val scrollState: UiScrollState = UiScrollState(),
) {
    private val transitionState = UiTransitionState()
    private val resolver = UiStyleResolver(theme, stylesheet, transitionState)
    private val layoutEngine = UiLayoutEngine()
    private val commandRenderer = UiCommandRenderer()

    fun frame(
        root: UiNode,
        width: Float,
        height: Float,
        bindings: UiBindingContext = UiBindingContext(),
        nowMillis: Long = 0L,
    ): HollowUiFrame {
        UiNodeKeys.assign(root)
        scrollState.update(nowMillis)
        val inputResolved = resolveInput(root, bindings, nowMillis)
        val resolved = resolver.resolve(root, bindings, nowMillis)
        val layout = layoutEngine.compute(resolved, width, height, scrollState, inputResolved)
        val commands = commandRenderer.collect(resolved, layout, bindings)
        return HollowUiFrame(resolved, layout, commands)
    }

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset = scrollState.scroll(node, deltaX, deltaY)

    fun setScrollImmediate(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset = scrollState.setImmediate(node, x, y)

    private fun resolveInput(root: UiNode, bindings: UiBindingContext, nowMillis: Long): ResolvedUiTree {
        val removed = mutableListOf<Pair<UiNode, Set<UiState>>>()
        root.forEachNode { node ->
            val transient = node.states.filterTo(mutableSetOf()) { it in TransientInputStates }
            if (transient.isNotEmpty()) {
                node.states.removeAll(transient)
                removed += node to transient
            }
        }
        return try {
            resolver.resolve(root, bindings, nowMillis, animate = false)
        } finally {
            removed.forEach { (node, states) -> node.states.addAll(states) }
        }
    }

    private fun UiNode.forEachNode(action: (UiNode) -> Unit) {
        action(this)
        children.forEach { it.forEachNode(action) }
    }

    companion object {
        private val TransientInputStates = setOf(UiState.HOVER, UiState.ACTIVE, UiState.DRAGGING)
    }
}
