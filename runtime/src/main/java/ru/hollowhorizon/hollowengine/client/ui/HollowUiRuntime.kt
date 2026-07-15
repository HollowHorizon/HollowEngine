package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.gui.screens.Screen.hasControlDown
import net.minecraft.client.gui.screens.Screen.hasShiftDown
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutNode
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutPipeline
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutResult
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.scroll.scrollWheelDelta
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import java.util.*

data class HollowUiFrame(
    val root: UiNode,
    val nodes: List<UiNode>,
    val layout: UiLayoutResult,
    val nowMillis: Long = 0L,
) {
    internal var profile: UiProfileFrame? = null

    private val nodesByIdentifier: Map<String, UiNode> by lazy {
        val index = HashMap<String, UiNode>(nodes.size * 2)
        for (node in nodes) {
            node.id?.let { index.putIfAbsent(it, node) }
            for (tag in node.tags) index.putIfAbsent(tag, node)
        }
        index
    }

    fun hitTest(x: Float, y: Float): UiHit? = UiHitTester().hitTest(root, layout, x, y)

    /** Whether visible, input-opaque UI geometry is under the point (blocks click-through). */
    fun hitsVisible(x: Float, y: Float): Boolean = UiHitTester().hitsVisible(root, layout, x, y)

    fun scrollTargetAt(x: Float, y: Float): UiNode? = scrollTargetIn(root, x, y)

    private fun scrollTargetIn(root: UiNode, x: Float, y: Float): UiNode? {
        val stack = ArrayDeque<ScrollTargetTask>()
        stack.add(ScrollTargetTask.Enter(root, ancestorClip = null))
        while (stack.isNotEmpty()) {
            when (val task = stack.removeLast()) {
                is ScrollTargetTask.Enter -> {
                    val node = task.node
                    val layoutNode = layout.nodes[node] ?: continue
                    val childClip = task.ancestorClip.intersect(layoutNode.clip)
                    stack.add(ScrollTargetTask.Test(node, task.ancestorClip))
                    val children = layout.childrenOf(node)
                    if (children.size == 1) {
                        stack.add(ScrollTargetTask.Enter(children[0], childClip))
                    } else if (children.size > 1) {
                        val sorted = ArrayList<UiNode>(children.size)
                        sorted.addAll(children)
                        sorted.sortWith(
                            compareByDescending<UiNode> { it.resolvedSnapshot.layer }
                                .thenByDescending { layout[it].rect.y }
                        )
                        for (child in sorted) stack.add(ScrollTargetTask.Enter(child, childClip))
                    }
                }

                is ScrollTargetTask.Test -> {
                    val node = task.node
                    if (!node.resolvedSnapshot.scrollable) continue
                    task.ancestorClip?.let { clip ->
                        if (!clip.contains(x, y)) continue
                    }
                    val layoutNode = layout[node]
                    if (!layoutNode.inputContains(x, y)) continue
                    return node
                }
            }
        }
        return null
    }

    fun nodeByIdentifier(identifier: String): UiNode? = nodesByIdentifier[identifier]

    fun nodeByKey(key: String): UiNode? = nodeByIdentifier(key)
}

internal fun UiLayoutNode.inputContains(x: Float, y: Float): Boolean {
    val transform = inputTransform
    if (transform.isAxisAligned) {
        val inverse = transform.inverse() ?: return false
        val localX = inverse.transformX(x)
        val localY = inverse.transformY(y)
        return localX >= 0f && localY >= 0f && localX <= rect.width && localY <= rect.height
    }
    if (!inputQuadContains(x, y)) return false
    val inverse = transform.inverse() ?: return false
    val local = inverse.transform(x, y, 0f)
    return local.x >= 0f && local.y >= 0f && local.x <= rect.width && local.y <= rect.height
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
    private val horizontalScrollModifierDown: () -> Boolean = { hasShiftDown() || hasControlDown() },
    val profiler: UiProfiler = UiProfiler(),
) {
    private val transitionState = UiTransitionState()
    private val resolver = UiModifierResolver(theme, stylesheet, transitionState)
    private val layoutPipeline = UiLayoutPipeline()
    private val input = HollowUiInputController()
    private val placedBounds = WeakHashMap<UiNode, UiRect>()
    private val reportedTextLayouts = WeakHashMap<UiNode, UiTextLayout>()
    private var lastLayout: UiLayoutResult? = null
    private var lastLayoutKey: FrameLayoutKey? = null
    private var preparedAtMillis: Long? = null
    var lastFrame: HollowUiFrame? = null
        private set

    val mouseX: Float get() = input.x
    val mouseY: Float get() = input.y
    val focusedKey get() = input.focusedKey
    val isAnyFocused get() = focusedKey != null

    /** Cursor shape for the node under the pointer - apply to the window via [UiCursorManager]. */
    val cursor: UiCursorShape get() = input.hoveredCursor

    fun frame(
        root: UiNode,
        width: Float,
        height: Float,
        mouseX: Float,
        mouseY: Float,
        nowMillis: Long = 0L,
    ): HollowUiFrame = frame(root, width, height, mouseX, mouseY, nowMillis, profiler.beginFrame())

    internal fun frame(
        root: UiNode,
        width: Float,
        height: Float,
        mouseX: Float,
        mouseY: Float,
        nowMillis: Long,
        profile: UiProfileFrame?,
    ): HollowUiFrame {
        input.prepareRoot(root, false)
        if (preparedAtMillis == nowMillis) {
            preparedAtMillis = null
        } else {
            scrollState.update(nowMillis)
        }
        val frame = buildFrame(root, width, height, nowMillis, profile)
        val inputStartedAt = if (profile != null) System.nanoTime() else 0L
        input.updateHover(frame, mouseX, mouseY, ::dispatchUiEvent)
        input.dispatchHover(frame, mouseX, mouseY, ::dispatchUiEvent)
        if (profile != null) profile.inputNanos += System.nanoTime() - inputStartedAt
        lastFrame = frame
        return frame
    }

    internal fun prepareFrame(nowMillis: Long) {
        lastLayout?.let { layout -> applyPendingScrollRequests(layout.nodes.keys) }
        scrollState.update(nowMillis)
        lastLayout?.let(::syncScrollHandlesFromState)
        preparedAtMillis = nowMillis
    }

    private fun buildFrame(
        root: UiNode,
        width: Float,
        height: Float,
        nowMillis: Long,
        profile: UiProfileFrame?,
    ): HollowUiFrame {
        val styleStartedAt = if (profile != null) System.nanoTime() else 0L
        val nodes = resolver.resolve(root, nowMillis, profile = profile)
        if (profile != null) profile.styleNanos += System.nanoTime() - styleStartedAt
        // Pending scroll requests are applied in prepareFrame, BEFORE composition, so composition
        // and this frame's layout read the same offset. Applying them here (after composition) would
        // desync a scroll requested during composition itself - e.g. the editor's caret-follow, which
        // runs in a composition SideEffect - making pinned overlays (line-number gutter) jitter for a
        // frame. Such requests instead take effect on the next frame's prepareFrame.

        val layoutKey = FrameLayoutKey(
            width = width,
            height = height,
            scrollRevision = scrollState.revision,
            subtreeLayoutRevision = root.layoutState.subtreeLayoutRevision,
        )
        // Layout is reused whenever nothing that affects layout changed. The resolver bumps
        // `subtreeLayoutRevision` only when a layout-fingerprint prop actually changed (see
        // updateResolvedLayoutFingerprint), so a draw-only animation (colour/opacity) keeps the
        // key identical and skips relayout, while a width/padding animation forces one.
        val layoutReused = lastLayout != null && layoutKey == lastLayoutKey
        var layout = if (layoutReused) {
            if (profile != null) profile.layoutReuses++
            lastLayout!!
        } else {
            layoutPipeline.compute(root, width, height, scrollState, profile)
        }
        lastLayout = layout
        // Sample scroll revision after layout: clamping inside the pass may bump it.
        lastLayoutKey = layoutKey.copy(scrollRevision = scrollState.revision)
        dispatchPlacementCallbacks(layout)
        dispatchTextLayoutCallbacks(layout)
        syncScrollHandles(nodes, layout)

        return HollowUiFrame(
            root = root,
            nodes = nodes,
            layout = layout,
            nowMillis = nowMillis,
        ).also { it.profile = profile }
    }

    /**
     * Processes an input against [frame] (always the last presented frame). Input is handled
     * synchronously — not deferred to the next built frame — so callers get an accurate
     * "handled" result, and the click/drag hit-tests the layout the user actually saw. Only
     * the layout rebuild is per-frame; dispatching a handful of events against the existing
     * layout is cheap.
     */
    private fun processInput(frame: HollowUiFrame, input: QueuedUiInput): UiInputResult {
        return when (input) {
            is QueuedUiInput.MouseClicked -> {
                val scrollbarResult = this.input.scrollbarMouseClicked(
                    frame, input.mouseX, input.mouseY, input.button, ::setScrollImmediate,
                )
                if (scrollbarResult.handled) scrollbarResult else this.input.mouseClicked(
                    frame, input.mouseX, input.mouseY, input.button, ::dispatchUiEvent, input.modifiers,
                )
            }

            is QueuedUiInput.MouseReleased ->
                this.input.mouseReleased(
                    frame, input.mouseX, input.mouseY, input.button, ::dispatchUiEvent, input.modifiers,
                )

            is QueuedUiInput.MouseDragged -> {
                val scrollbarResult = this.input.scrollbarMouseDragged(
                    frame, input.mouseX, input.mouseY, ::setScrollImmediate,
                )
                if (scrollbarResult.handled) scrollbarResult else this.input.mouseDragged(
                    frame, input.mouseX, input.mouseY, input.button, input.dragX, input.dragY,
                    input.modifiers, ::dispatchUiEvent,
                )
            }

            is QueuedUiInput.MouseScrolled -> handleQueuedScroll(frame, input)
            is QueuedUiInput.CharTyped ->
                this.input.charTyped(frame, input.codePoint, input.modifiers, ::dispatchUiEvent)

            is QueuedUiInput.KeyPressed ->
                this.input.keyPressed(
                    frame, input.keyCode, input.scanCode, input.modifiers, input.repeat, ::dispatchUiEvent,
                )
        }
    }

    private fun dispatchInput(input: QueuedUiInput): Boolean {
        val frame = lastFrame ?: return false
        val result = processInput(frame, input)
        return result.handled || result.changed
    }

    private fun UiInputResult.orConsumed(consumed: Boolean) = handled || changed || consumed

    private fun handleQueuedScroll(frame: HollowUiFrame, input: QueuedUiInput.MouseScrolled): UiInputResult {
        val target = this.input.scrollTargetAt(frame, input.mouseX, input.mouseY) ?: return UiInputResult(false)
        val range = frame.layout[target].scrollRange
        val delta = scrollWheelDelta(range, input.scrollX, input.scrollY, horizontalScrollModifierDown())
        val event = UiEvent(
            kind = UiEventKind.SCROLL,
            node = target,
            x = input.mouseX,
            y = input.mouseY,
            scrollX = delta.x,
            scrollY = delta.y,
        )
        if (dispatchUiEvent(event) && event.consumed) return UiInputResult(
            true,
            target,
            target.id,
            changed = event.changed
        )
        scroll(target, delta.x * 32f, delta.y * 32f)
        return UiInputResult(true, target, target.id, changed = true)
    }

    fun scroll(node: UiNode, deltaX: Float, deltaY: Float): UiScrollOffset =
        scrollState.scroll(node, deltaX, deltaY)

    fun setScrollImmediate(node: UiNode, offset: UiScrollOffset): UiScrollOffset =
        scrollState.setImmediate(node, offset.x, offset.y)

    /**
     * Applies scroll offsets requested through hoisted [UiScrollHandle]s before layout runs, so the
     * bumped scroll revision forces a fresh pass that places content at the requested offset.
     */
    private fun applyPendingScrollRequests(nodes: Iterable<UiNode>) {
        for (node in nodes) {
            val handle = node.scrollHandle() ?: continue
            if (handle.pendingX != null || handle.pendingY != null) {
                scrollState.setImmediate(node, handle.pendingX, handle.pendingY)
                handle.pendingX = null
                handle.pendingY = null
            }
            if (handle.pendingAnimatedDeltaX != 0f || handle.pendingAnimatedDeltaY != 0f) {
                scrollState.scroll(node, handle.pendingAnimatedDeltaX, handle.pendingAnimatedDeltaY)
                handle.pendingAnimatedDeltaX = 0f
                handle.pendingAnimatedDeltaY = 0f
            }
        }
    }

    /** Mirrors each scroll container's clamped offset and content viewport into its [UiScrollHandle]. */
    private fun syncScrollHandles(nodes: List<UiNode>, layout: UiLayoutResult) {
        for (node in nodes) {
            val handle = node.scrollHandle() ?: continue
            val layoutNode = layout.nodes[node] ?: continue
            handle.offsetX = layoutNode.scrollOffset.x
            handle.offsetY = layoutNode.scrollOffset.y
            handle.viewport = layoutNode.content
        }
    }

    private fun syncScrollHandlesFromState(layout: UiLayoutResult) {
        for ((node, layoutNode) in layout.nodes) {
            val handle = node.scrollHandle() ?: continue
            val offset = scrollState.offset(node)
            handle.offsetX = offset.x
            handle.offsetY = offset.y
            handle.viewport = layoutNode.content
        }
    }

    private fun UiNode.scrollHandle(): UiScrollHandle? =
        resolvedModifiers.firstNotNullOfOrNull { (it as? ScrollModifier)?.state }

    /**
     * After layout, hands every [OnPlacedModifier] its node's bounds in root coordinates, but only
     * when they changed since the last report. The change guard keeps a callback that writes state
     * (e.g. a popup anchor) from looping: once the bounds settle, it stops firing.
     */
    private fun dispatchPlacementCallbacks(layout: UiLayoutResult) {
        for (node in layout.traversalOrder) {
            var rect: UiRect? = null
            for (modifier in node.resolvedModifiers) {
                if (modifier !is OnPlacedModifier) continue
                if (rect == null) {
                    val current = layout[node].rect
                    if (placedBounds[node] == current) break
                    placedBounds[node] = current
                    rect = current
                }
                modifier.callback(rect)
            }
        }
    }

    private fun dispatchTextLayoutCallbacks(layout: UiLayoutResult) {
        for (node in layout.traversalOrder) {
            var textLayout: UiTextLayout? = null
            for (modifier in node.resolvedModifiers) {
                if (modifier !is OnTextLayoutModifier) continue
                if (textLayout == null) {
                    val current = layout[node].textLayout ?: break
                    if (reportedTextLayouts[node] == current) break
                    reportedTextLayouts[node] = current
                    textLayout = current
                }
                modifier.callback(textLayout)
            }
        }
    }

    fun mouseClicked(mouseX: Float, mouseY: Float, button: Int, modifiers: Int = 0): Boolean = profileInput {
        val frame = lastFrame ?: return@profileInput false
        processInput(frame, QueuedUiInput.MouseClicked(mouseX, mouseY, button, modifiers))
            .orConsumed(frame.hitsVisible(mouseX, mouseY))
    }

    fun mouseReleased(mouseX: Float, mouseY: Float, button: Int, modifiers: Int = 0): Boolean =
        profileInput { dispatchInput(QueuedUiInput.MouseReleased(mouseX, mouseY, button, modifiers)) }

    fun mouseDragged(
        mouseX: Float,
        mouseY: Float,
        button: Int,
        dragX: Float,
        dragY: Float,
        modifiers: Int = currentUiKeyModifiers(),
    ): Boolean = profileInput {
        dispatchInput(QueuedUiInput.MouseDragged(mouseX, mouseY, button, dragX, dragY, modifiers))
    }

    fun mouseScrolled(mouseX: Float, mouseY: Float, scrollX: Float, scrollY: Float): Boolean = profileInput {
        val frame = lastFrame ?: return@profileInput false
        processInput(frame, QueuedUiInput.MouseScrolled(mouseX, mouseY, scrollX, scrollY))
            .orConsumed(input.scrollTargetAt(frame, mouseX, mouseY) != null)
    }

    private fun dispatchUiEvent(event: UiEvent): Boolean {
        var handled = false
        if (!event.consumed && event.node.dispatch(event)) handled = true
        return handled
    }

    fun charTyped(codePoint: Char, modifiers: Int): Boolean = profileInput {
        val frame = lastFrame ?: return@profileInput false
        processInput(frame, QueuedUiInput.CharTyped(codePoint, modifiers)).orConsumed(isAnyFocused)
    }

    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int, repeat: Boolean = false): Boolean = profileInput {
        val frame = lastFrame ?: return@profileInput false
        val result = processInput(frame, QueuedUiInput.KeyPressed(keyCode, scanCode, modifiers, repeat))
        result.consumed || result.changed
    }

    private inline fun profileInput(block: () -> Boolean): Boolean {
        if (!profiler.enabled) return block()
        val startedAt = System.nanoTime()
        return try {
            block()
        } finally {
            profiler.recordInput(System.nanoTime() - startedAt)
        }
    }

    fun reset() {
        input.reset()
        lastLayout = null
        lastLayoutKey = null
    }

    fun focus(editorKey: String) {
        input.focus(lastFrame ?: return, editorKey, ::dispatchUiEvent)
    }

    fun unfocus() {
        input.focus(lastFrame ?: return, null, ::dispatchUiEvent)
    }
}

private data class FrameLayoutKey(
    val width: Float,
    val height: Float,
    val scrollRevision: Long,
    val subtreeLayoutRevision: Long,
)


