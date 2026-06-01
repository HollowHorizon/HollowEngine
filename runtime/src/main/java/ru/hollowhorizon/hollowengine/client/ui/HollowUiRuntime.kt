package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss

data class HollowUiFrame(
    val resolved: ResolvedUiTree,
    val layout: UiLayoutResult,
    val commands: List<UiRenderCommand>,
) {
    fun hitTest(x: Float, y: Float): UiHit? = textLinkHit(x, y) ?: UiHitTester().hitTest(resolved, layout, x, y)

    private fun textLinkHit(x: Float, y: Float): UiHit? {
        for (command in commands.asReversed().filterIsInstance<DrawTextCommand>()) {
            val node = command.node as? TextNode ?: continue
            val layoutNode = layout[node]
            val inverse = layoutNode.inputTransform.inverse() ?: continue
            val local = inverse.transform(x, y, 0f)
            val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
            if (!rect.contains(local.x, local.y)) continue
            layoutNode.clip?.let { if (!it.contains(x, y)) continue }
            val contentX = local.x - (layoutNode.content.x - layoutNode.rect.x) + command.scrollOffset.x
            val contentY = local.y - (layoutNode.content.y - layoutNode.rect.y) + command.scrollOffset.y
            val link = command.layout.linkAt(contentX, contentY) ?: continue
            return UiHit(node, local.x, local.y, link)
        }
        return null
    }
}

class HollowUiRuntime(
    theme: CompiledHss? = null,
    stylesheet: CompiledHss? = null,
    private val scrollState: UiScrollState = UiScrollState(),
) {
    private val transitionState = UiTransitionState()
    private val typingState = UiTypingState()
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
        val resolved = resolver.resolve(root, bindings, nowMillis)
        val layout = layoutEngine.compute(resolved, width, height, scrollState, bindings)
        val commands = commandRenderer.collect(resolved, layout, bindings, nowMillis, typingState)
        return HollowUiFrame(resolved, layout, commands)
    }

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset = scrollState.scroll(node, deltaX, deltaY)

    fun setScrollImmediate(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset =
        scrollState.setImmediate(node, x, y)

}

private fun UiTextLayout.linkAt(x: Float, y: Float): String? {
    val line = lines.firstOrNull { y >= it.y && y <= it.y + it.height } ?: return null
    return line.fragments.filterIsInstance<UiTextRun>().firstOrNull { fragment ->
        fragment.style.link != null &&
                x >= fragment.x &&
                x <= fragment.x + fragment.width &&
                y >= line.y + fragment.y &&
                y <= line.y + fragment.y + fragment.height
    }?.style?.link
}
