package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.gui.screens.Screen.hasControlDown
import net.minecraft.client.gui.screens.Screen.hasShiftDown
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutPipeline
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutResult
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.*
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import ru.hollowhorizon.hollowengine.common.utils.openUrl
import java.util.*

data class HollowUiFrame(
    val root: UiNode,
    val nodes: List<UiNode>,
    val layout: UiLayoutResult,
    val nowMillis: Long = 0L,
    val typingState: UiTypingState = UiTypingState(),
    private val activeTransitionDurations: Map<UiNode, Long> = emptyMap(),
    private val startedTransitionDurations: Map<UiNode, Long> = emptyMap(),
    private val activeScrollAnimation: Boolean = false,
) {
    fun hitTest(x: Float, y: Float): UiHit? = textLinkHit(x, y) ?: UiHitTester().hitTest(root, layout, x, y)

    fun scrollTargetAt(x: Float, y: Float): UiNode? {
        val popups = layout.popupNodes
            .sortedBy { it.resolvedSnapshot.layer }
        for (popup in popups.asReversed()) {
            scrollTargetIn(popup, x, y)?.let { return it }
        }
        return scrollTargetIn(root, x, y)
    }

    private fun scrollTargetIn(root: UiNode, x: Float, y: Float): UiNode? {
        val stack = ArrayDeque<ScrollTargetTask>()
        stack.add(ScrollTargetTask.Enter(root, ancestorClip = null))
        while (stack.isNotEmpty()) {
            when (val task = stack.removeLast()) {
                is ScrollTargetTask.Enter -> {
                    val node = task.node
                    val layoutNode = layout[node]
                    val childClip = task.ancestorClip.intersect(layoutNode.clip)
                    stack.add(ScrollTargetTask.Test(node, task.ancestorClip))
                    val children = node.children
                        .filter { it in layout.nodes }
                        .sortedWith(compareByDescending<UiNode> { it.resolvedSnapshot.layer }.thenByDescending { layout[it].rect.y })
                    for (child in children) stack.add(ScrollTargetTask.Enter(child, childClip))
                }

                is ScrollTargetTask.Test -> {
                    val node = task.node
                    if (!node.resolvedSnapshot.input.scrollable) continue
                    task.ancestorClip?.let { clip ->
                        if (!clip.contains(x, y)) continue
                    }
                    val layoutNode = layout[node]
                    if (!layoutNode.inputQuadContains(x, y)) continue
                    val inverse = layoutNode.inputTransform.inverse() ?: continue
                    val local = inverse.transform(x, y, 0f)
                    val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
                    if (rect.contains(local.x, local.y)) return node
                }
            }
        }
        return null
    }

    fun nodeByIdentifier(identifier: String): UiNode? =
        nodes.firstOrNull { it.id == identifier || identifier in it.tags }

    fun nodeByKey(key: String): UiNode? = nodeByIdentifier(key)

    internal fun scrollbarAt(x: Float, y: Float): UiScrollbarHandle? {
        val scrollbars = scrollbarHandlesInDrawOrder()
        return scrollbars.lastOrNull { it.pointerAreaAt(x, y) == UiScrollbarPointerArea.THUMB }
            ?: scrollbars.lastOrNull { it.pointerAreaAt(x, y) == UiScrollbarPointerArea.TRACK }
    }

    fun motionDurationMillis(previous: HollowUiFrame?): Long {
        val previousStyles = previous?.nodes.orEmpty().associateWith { it.resolvedSnapshot }
        return nodes.maxOfOrNull { node ->
            maxOf(
                startedTransitionDurations[node] ?: 0L,
                node.resolvedSnapshot.motionDurationMillis(previousStyles[node]),
            )
        } ?: 0L
    }

    private fun textLinkHit(x: Float, y: Float): UiHit? {
        for (node in layout.traversalOrder.asReversed()) {
            if (node !is TextNode) continue
            val layoutNode = layout.nodes[node] ?: continue
            val textLayout = layoutNode.textLayout ?: continue
            val inverse = layoutNode.inputTransform.inverse() ?: continue
            val local = inverse.transform(x, y, 0f)
            val rect = UiRect(0f, 0f, layoutNode.rect.width, layoutNode.rect.height)
            if (!rect.contains(local.x, local.y)) continue
            layoutNode.clip?.let { if (!it.contains(x, y)) continue }
            val contentX = local.x - (layoutNode.content.x - layoutNode.rect.x) + layoutNode.scrollOffset.x
            val contentY = local.y - (layoutNode.content.y - layoutNode.rect.y) + layoutNode.scrollOffset.y
            val link = textLayout.linkAt(contentX, contentY) ?: continue
            return UiHit(node, local.x, local.y, link)
        }
        return null
    }

    private fun scrollbarHandlesInDrawOrder(): List<UiScrollbarHandle> {
        val result = mutableListOf<UiScrollbarHandle>()
        fun collect(root: UiNode) {
            val stack = ArrayDeque<ScrollbarTraversalTask>()
            stack.add(ScrollbarTraversalTask(root, visited = false))
            while (stack.isNotEmpty()) {
                val task = stack.removeLast()
                val node = task.node
                if (task.visited) {
                    val layoutNode = layout.nodes[node] ?: continue
                    for (geometry in layoutNode.scrollbars) {
                        result += UiScrollbarHandle(node, geometry, layoutNode.worldTransform)
                    }
                    continue
                }
                stack.add(ScrollbarTraversalTask(node, visited = true))
                val children = node.children
                    .filterNot { it is PopupNode }
                    .filter { it in layout.nodes }
                    .sortedByDescending { it.resolvedSnapshot.layer }
                for (child in children) stack.add(ScrollbarTraversalTask(child, visited = false))
            }
        }
        collect(root)
        layout.popupNodes
            .sortedBy { it.resolvedSnapshot.layer }
            .forEach(::collect)
        return result
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

private data class ScrollbarTraversalTask(
    val node: UiNode,
    val visited: Boolean,
)

private sealed interface ScrollTargetTask {
    data class Enter(
        val node: UiNode,
        val ancestorClip: UiRect?,
    ) : ScrollTargetTask

    data class Test(
        val node: UiNode,
        val ancestorClip: UiRect?,
    ) : ScrollTargetTask
}

fun UiRect?.intersect(other: UiRect?): UiRect? {
    if (this == null) return other
    if (other == null) return this
    val left = maxOf(x, other.x)
    val top = maxOf(y, other.y)
    val right = minOf(x + width, other.x + other.width)
    val bottom = minOf(y + height, other.y + other.height)
    if (right <= left || bottom <= top) return UiRect(left, top, 0f, 0f)
    return UiRect(left, top, right - left, bottom - top)
}

class HollowUiRuntime(
    theme: CompiledHss? = null,
    stylesheet: CompiledHss? = null,
    private val scrollState: UiScrollState = UiScrollState(),
) {
    private val transitionState = UiTransitionState()
    private val typingState = UiTypingState()
    private val resolver = UiModifierResolver(theme, stylesheet, transitionState)
    private val layoutPipeline = UiLayoutPipeline()
    private val ensuredTextFieldCaretRevisions = WeakHashMap<TextFieldNode, Long>()
    private val stateStore = UiNodeStateStore()
    private val input = HollowUiInputController()
    private val pendingInputs = ArrayDeque<QueuedUiInput>()
    private var lastNodes: List<UiNode>? = null
    private var lastLayout: UiLayoutResult? = null
    private var lastLayoutKey: FrameLayoutKey? = null
    var lastFrame: HollowUiFrame? = null
        private set

    val mouseX: Float get() = input.x
    val mouseY: Float get() = input.y
    val focusedKey get() = input.focusedKey
    val isAnyFocused get() = focusedKey != null

    fun frame(
        root: UiNode,
        width: Float,
        height: Float,
        mouseX: Float,
        mouseY: Float,
        nowMillis: Long = 0L,
    ): HollowUiFrame {
        stateStore.apply(root)
        input.prepareRoot(root, false)
        scrollState.update(nowMillis)
        val frame = buildFrame(root, width, height, nowMillis)
        drainInputQueue(frame)
        input.updateHover(frame, mouseX, mouseY, ::dispatchUiEvent)
        input.dispatchHover(frame, mouseX, mouseY, ::dispatchUiEvent)
        lastFrame = frame
        return frame
    }

    private fun buildFrame(root: UiNode, width: Float, height: Float, nowMillis: Long): HollowUiFrame {
        val nodes = resolver.resolve(root, nowMillis)
        val resolutionReused = nodes === lastNodes
        val transitionDurations = collectTransitionDurations(nodes)

        // Styles resolved during transitions/animations change without bumping node
        // revisions (transform interpolation feeds placement), so layout reuse is only
        // safe while motion is idle.
        val layoutKey = FrameLayoutKey(
            width = width,
            height = height,
            scrollRevision = scrollState.revision,
            subtreeLayoutRevision = root.layoutState.subtreeLayoutRevision,
        )
        val motionIdle = resolutionReused ||
                (!transitionState.hasActiveTransitions() && nodes.none { it.resolvedSnapshot.animations.isNotEmpty() })
        val layoutReused = motionIdle && lastLayout?.takeIf { it.root === root } != null && layoutKey == lastLayoutKey
        var layout = if (layoutReused) {
            lastLayout!!
        } else {
            layoutPipeline.compute(root, width, height, scrollState)
        }
        if (ensureFocusedTextFieldsVisible(nodes, layout)) {
            layout = layoutPipeline.compute(root, width, height, scrollState)
        }
        lastLayout = layout
        // Sample scroll revision after layout: clamping inside the pass may bump it.
        lastLayoutKey = layoutKey.copy(scrollRevision = scrollState.revision)
        lastNodes = nodes

        return HollowUiFrame(
            root = root,
            nodes = nodes,
            layout = layout,
            nowMillis = nowMillis,
            typingState = typingState,
            activeTransitionDurations = transitionDurations.active,
            startedTransitionDurations = transitionDurations.started,
            activeScrollAnimation = scrollState.isAnimating(),
        )
    }

    private fun drainInputQueue(frame: HollowUiFrame): Boolean {
        if (pendingInputs.isEmpty()) return false
        var changed = false
        while (pendingInputs.isNotEmpty()) {
            val result = when (val input = pendingInputs.removeFirst()) {
                is QueuedUiInput.MouseClicked -> {
                    val scrollbarResult = this.input.scrollbarMouseClicked(
                        frame,
                        input.mouseX,
                        input.mouseY,
                        input.button,
                        ::setScrollImmediate,
                    )
                    if (scrollbarResult.handled) scrollbarResult else this.input.mouseClicked(
                        frame,
                        input.mouseX,
                        input.mouseY,
                        input.button,
                        ::dispatchUiEvent,
                        ::openUrl,
                    )
                }

                is QueuedUiInput.MouseReleased ->
                    this.input.mouseReleased(frame, input.mouseX, input.mouseY, input.button, ::dispatchUiEvent)

                is QueuedUiInput.MouseDragged -> {
                    val scrollbarResult = this.input.scrollbarMouseDragged(
                        frame,
                        input.mouseX,
                        input.mouseY,
                        ::setScrollImmediate,
                    )
                    if (scrollbarResult.handled) scrollbarResult else this.input.mouseDragged(
                        frame,
                        input.mouseX,
                        input.mouseY,
                        input.button,
                        input.dragX,
                        input.dragY,
                        ::dispatchUiEvent,
                    )
                }

                is QueuedUiInput.MouseScrolled -> handleQueuedScroll(frame, input)
                is QueuedUiInput.CharTyped ->
                    this.input.charTyped(frame, input.codePoint, input.modifiers, ::dispatchUiEvent)

                is QueuedUiInput.KeyPressed ->
                    this.input.keyPressed(frame, input.keyCode, input.scanCode, input.modifiers, ::dispatchUiEvent)
            }
            changed = changed || result.handled || result.changed
        }
        return changed
    }

    private fun handleQueuedScroll(frame: HollowUiFrame, input: QueuedUiInput.MouseScrolled): UiInputResult {
        val target = this.input.scrollTargetAt(frame, input.mouseX, input.mouseY) ?: return UiInputResult(false)
        val range = frame.layout[target].scrollRange
        val delta = scrollWheelDelta(range, input.scrollX, input.scrollY, hasShiftDown() || hasControlDown())
        val event = UiEvent(
            kind = UiEventKind.SCROLL,
            node = target,
            x = input.mouseX,
            y = input.mouseY,
            scrollX = delta.x,
            scrollY = delta.y,
        )
        if (dispatchUiEvent(event) && event.consumed) return UiInputResult(true, target, target.id, changed = event.changed)
        scroll(target, delta.x * 32f, delta.y * 32f)
        return UiInputResult(true, target, target.id, changed = true)
    }

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset =
        scrollState.scroll(node, deltaX, deltaY)

    fun setScrollImmediate(node: UiNode, offset: UiScrollOffset): UiScrollOffset =
        scrollState.setImmediate(node, offset.x, offset.y)

    private fun collectTransitionDurations(nodes: List<UiNode>): TransitionDurations {
        if (nodes.none { it.resolvedSnapshot.transitions.isNotEmpty() }) return TransitionDurations.Empty
        val active = mutableMapOf<UiNode, Long>()
        val started = mutableMapOf<UiNode, Long>()
        nodes.forEach { node ->
            active[node] = transitionState.activeDurationMillis(node)
            started[node] = transitionState.startedDurationMillis(node)
        }
        return TransitionDurations(active, started)
    }

    private fun ensureFocusedTextFieldsVisible(nodes: List<UiNode>, layout: UiLayoutResult): Boolean {
        var changed = false
        for (node in nodes.filterIsInstance<TextFieldNode>()) {
            if (UiState.FOCUS !in node.effectiveStates()) continue
            if (ensuredTextFieldCaretRevisions[node] == node.caretVisibilityRevision) continue
            val style = node.resolvedSnapshot
            if (!style.input.scrollable) {
                ensuredTextFieldCaretRevisions[node] = node.caretVisibilityRevision
                continue
            }
            val layoutNode = layout[node]
            if (!layoutNode.scrollRange.hasScrollableAxis()) {
                ensuredTextFieldCaretRevisions[node] = node.caretVisibilityRevision
                continue
            }
            val fontSize = style.fontSize
            val caret =
                textFieldEditLayout(node, style, layoutNode).caretPosition(node.caret, fontSize, style.fontFamily)
            val textOffset = textFieldTextOffset(node, style)
            val next = layoutNode.scrollOffset.scrollCaretIntoView(
                caretX = caret.x,
                caretY = caret.y,
                caretWidth = TextFieldCaretWidth,
                caretHeight = fontSize,
                viewportWidth = (layoutNode.content.width - textOffset).coerceAtLeast(1f),
                viewportHeight = layoutNode.content.height,
                range = layoutNode.scrollRange,
            )
            if (next != layoutNode.scrollOffset) {
                scrollState.setImmediate(node, next.x, next.y)
                changed = true
            }
            ensuredTextFieldCaretRevisions[node] = node.caretVisibilityRevision
        }
        return changed
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int): Boolean {
        val frame = lastFrame ?: return false
        pendingInputs += QueuedUiInput.MouseClicked(mouseX, mouseY, button)
        return frame.scrollbarAt(mouseX, mouseY) != null || frame.hitTest(mouseX, mouseY) != null
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int): Boolean {
        lastFrame ?: return false
        pendingInputs += QueuedUiInput.MouseReleased(mouseX, mouseY, button)
        return true
    }

    fun mouseDragged(mouseX: Float, mouseY: Float, button: Int, dragX: Float, dragY: Float): Boolean {
        lastFrame ?: return false
        pendingInputs += QueuedUiInput.MouseDragged(mouseX, mouseY, button, dragX, dragY)
        return true
    }

    fun mouseScrolled(mouseX: Float, mouseY: Float, scrollX: Float, scrollY: Float): Boolean {
        val frame = lastFrame ?: return false
        pendingInputs += QueuedUiInput.MouseScrolled(mouseX, mouseY, scrollX, scrollY)
        return input.scrollTargetAt(frame, mouseX, mouseY) != null
    }

    private fun dispatchUiEvent(event: UiEvent): Boolean {
        var handled = false
        if (!event.consumed && event.node.dispatch(event)) handled = true
        return handled
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        lastFrame ?: return false
        pendingInputs += QueuedUiInput.CharTyped(codePoint, modifiers)
        return isAnyFocused
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        lastFrame ?: return false
        pendingInputs += QueuedUiInput.KeyPressed(keyCode, scanCode, modifiers)
        return isAnyFocused
    }

    fun reset() {
        pendingInputs.clear()
        input.reset()
        lastNodes = null
        lastLayout = null
        lastLayoutKey = null
    }

    fun saveState(node: UiStatefulNode) {
        stateStore.save(node)
    }

    fun focus(editorKey: String) {
        input.focus(lastFrame ?: return, editorKey, ::dispatchUiEvent)
    }

    fun unfocus() {
        input.focus(lastFrame ?: return, null, ::dispatchUiEvent)
    }
}

private data class TransitionDurations(
    val active: Map<UiNode, Long>,
    val started: Map<UiNode, Long>,
) {
    companion object {
        val Empty = TransitionDurations(emptyMap(), emptyMap())
    }
}

private data class FrameLayoutKey(
    val width: Float,
    val height: Float,
    val scrollRevision: Long,
    val subtreeLayoutRevision: Long,
)

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
    val horizontalPadding = textFieldHorizontalScrollPadding(viewportWidth)
    val left = x + horizontalPadding
    val right = x + viewportWidth - horizontalPadding
    val top = y + TextFieldCaretVisibilityPadding
    val bottom = y + viewportHeight - TextFieldCaretVisibilityPadding
    if (caretX < left) nextX = caretX - horizontalPadding
    if (caretX + caretWidth > right) nextX = caretX + caretWidth + horizontalPadding - viewportWidth
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
