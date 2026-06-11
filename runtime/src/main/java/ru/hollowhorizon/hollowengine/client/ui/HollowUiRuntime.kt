package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss

data class HollowUiFrame(
    val resolved: ResolvedUiTree,
    val layout: UiLayoutResult,
    val commands: List<UiRenderCommand>,
    private val activeTransitionDurations: Map<String, Long> = emptyMap(),
    private val startedTransitionDurations: Map<String, Long> = emptyMap(),
    private val activeScrollAnimation: Boolean = false,
) {
    fun hitTest(x: Float, y: Float): UiHit? = textLinkHit(x, y) ?: UiHitTester().hitTest(resolved, layout, x, y)

    fun scrollTargetAt(x: Float, y: Float): UiNode? = scrollTargetAt(resolved.root, x, y, ancestorClip = null)

    fun nodeByKey(key: String): UiNode? = resolved.styles.keys.firstOrNull { UiNodeKeys.key(it) == key }

    fun scrollbarAt(x: Float, y: Float): DrawScrollbarCommand? {
        val scrollbars = commands.filterIsInstance<DrawScrollbarCommand>()
        return scrollbars.lastOrNull { it.pointerAreaAt(x, y) == UiScrollbarPointerArea.THUMB }
            ?: scrollbars.lastOrNull { it.pointerAreaAt(x, y) == UiScrollbarPointerArea.TRACK }
    }

    fun requiresContinuousRefresh(): Boolean {
        return activeScrollAnimation ||
                activeTransitionDurations.values.any { it > 0L } ||
                resolved.styles.values.any { it.requiresContinuousRefresh() }
    }

    fun motionDurationMillis(previous: HollowUiFrame?): Long {
        val previousStyles = previous?.resolved?.styles
            ?.mapKeys { (node, _) -> UiNodeKeys.key(node) }
            .orEmpty()
        return resolved.styles.maxOfOrNull { (node, style) ->
            val key = UiNodeKeys.key(node)
            maxOf(
                startedTransitionDurations[key] ?: 0L,
                style.motionDurationMillis(previousStyles[key]),
            )
        } ?: 0L
    }

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

    private fun scrollTargetAt(node: UiNode, x: Float, y: Float, ancestorClip: UiRect?): UiNode? {
        val children = node.children.sortedWith(compareBy<UiNode> { resolved[it].layer }.thenBy { layout[it].rect.y })
        val layoutNode = layout[node]
        val childClip = ancestorClip.intersect(layoutNode.clip)
        for (child in children.asReversed()) {
            scrollTargetAt(child, x, y, childClip)?.let { return it }
        }
        if (!resolved[node].input.scrollable) return null
        ancestorClip?.let { clip ->
            if (!clip.contains(x, y)) return null
        }
        if (!layoutNode.inputQuadContains(x, y)) return null
        val inverse = layoutNode.inputTransform.inverse() ?: return null
        val local = inverse.transform(x, y, 0f)
        val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
        return if (rect.contains(local.x, local.y)) node else null
    }

    private fun UiLayoutNode.inputQuadContains(x: Float, y: Float): Boolean {
        val corners = arrayOf(
            inputTransform.transform(0f, 0f),
            inputTransform.transform(0f, rect.height),
            inputTransform.transform(rect.width, rect.height),
            inputTransform.transform(rect.width, 0f),
        )
        return pointInConvexPolygon(x, y, corners)
    }

    private fun pointInConvexPolygon(x: Float, y: Float, corners: Array<UiVec3>): Boolean {
        if (corners.size < 3) return false
        var sign = 0f
        for (index in corners.indices) {
            val current = corners[index]
            val next = corners[(index + 1) % corners.size]
            val cross = (next.x - current.x) * (y - current.y) - (next.y - current.y) * (x - current.x)
            if (cross == 0f) continue
            if (sign == 0f) {
                sign = cross
            } else if (sign * cross < 0f) {
                return false
            }
        }
        return true
    }
}

private fun UiRect?.intersect(other: UiRect?): UiRect? {
    if (this == null) return other
    if (other == null) return this
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    if (right <= left || bottom <= top) return UiRect(left, top, 0f, 0f)
    return UiRect(left, top, right - left, bottom - top)
}

private fun ComputedStyle.requiresContinuousRefresh(): Boolean {
    if (typing != null) return true
    return animations.any { animation ->
        animation.totalDurationMillis()?.let { it > 0L } ?: true
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
    private val ensuredTextFieldCaretRevisions = mutableMapOf<String, Long>()

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
        val activeTransitionDurations = resolved.styles.keys.associate { node ->
            UiNodeKeys.key(node) to transitionState.activeDurationMillis(node)
        }
        val startedTransitionDurations = resolved.styles.keys.associate { node ->
            UiNodeKeys.key(node) to transitionState.startedDurationMillis(node)
        }
        var layout = layoutEngine.compute(resolved, width, height, scrollState, bindings)
        if (ensureFocusedTextFieldsVisible(resolved, layout)) {
            layout = layoutEngine.compute(resolved, width, height, scrollState, bindings)
        }
        val commands = commandRenderer.collect(resolved, layout, bindings, nowMillis, typingState)
        return HollowUiFrame(
            resolved = resolved,
            layout = layout,
            commands = commands,
            activeTransitionDurations = activeTransitionDurations,
            startedTransitionDurations = startedTransitionDurations,
            activeScrollAnimation = scrollState.isAnimating(),
        )
    }

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset = scrollState.scroll(node, deltaX, deltaY)

    fun setScrollImmediate(node: UiNode, x: Float? = null, y: Float? = null): UiScrollOffset =
        scrollState.setImmediate(node, x, y)

    private fun ensureFocusedTextFieldsVisible(resolved: ResolvedUiTree, layout: UiLayoutResult): Boolean {
        var changed = false
        for (node in resolved.styles.keys.filterIsInstance<TextFieldNode>()) {
            if (UiState.FOCUS !in node.states) continue
            val key = UiNodeKeys.key(node)
            if (ensuredTextFieldCaretRevisions[key] == node.caretVisibilityRevision) continue
            val style = resolved[node]
            if (!style.input.scrollable) {
                ensuredTextFieldCaretRevisions[key] = node.caretVisibilityRevision
                continue
            }
            val layoutNode = layout[node]
            if (!layoutNode.scrollRange.hasScrollableAxis()) {
                ensuredTextFieldCaretRevisions[key] = node.caretVisibilityRevision
                continue
            }
            val caret = textFieldEditLayout(node, style, layoutNode).caretPosition(node.caret, style.fontSize, style.fontFamily)
            val next = layoutNode.scrollOffset.scrollCaretIntoView(
                caretX = caret.x,
                caretY = caret.y,
                caretWidth = TextFieldCaretWidth,
                caretHeight = style.fontSize,
                viewportWidth = layoutNode.content.width,
                viewportHeight = layoutNode.content.height,
                range = layoutNode.scrollRange,
            )
            if (next != layoutNode.scrollOffset) {
                scrollState.setImmediate(node, next.x, next.y)
                changed = true
            }
            ensuredTextFieldCaretRevisions[key] = node.caretVisibilityRevision
        }
        return changed
    }
}

private fun UiScrollOffset.scrollCaretIntoView(
    caretX: Float,
    caretY: Float,
    caretWidth: Float,
    caretHeight: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    range: UiScrollOffset,
): UiScrollOffset {
    var nextX = x
    var nextY = y
    val left = x + TextFieldCaretVisibilityPadding
    val right = x + viewportWidth - TextFieldCaretVisibilityPadding
    val top = y + TextFieldCaretVisibilityPadding
    val bottom = y + viewportHeight - TextFieldCaretVisibilityPadding
    if (caretX < left) nextX = caretX - TextFieldCaretVisibilityPadding
    if (caretX + caretWidth > right) nextX = caretX + caretWidth + TextFieldCaretVisibilityPadding - viewportWidth
    if (caretY < top) nextY = caretY - TextFieldCaretVisibilityPadding
    if (caretY + caretHeight > bottom) nextY = caretY + caretHeight + TextFieldCaretVisibilityPadding - viewportHeight
    return UiScrollOffset(
        x = nextX.coerceIn(0f, range.x),
        y = nextY.coerceIn(0f, range.y),
    )
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
