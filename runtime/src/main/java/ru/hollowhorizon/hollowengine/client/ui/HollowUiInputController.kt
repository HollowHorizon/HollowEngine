package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.layout.readOnlyIterator
import ru.hollowhorizon.hollowengine.client.ui.scroll.*
import ru.hollowhorizon.hollowengine.client.ui.style.*
import java.util.*

class HollowUiInputController {
    private var hoveredNode: UiNode? = null
    private var activeNode: UiNode? = null
    private var draggingNode: UiNode? = null
    private var dragStartX = 0f
    private var dragStartY = 0f

    // Focus is per scope (root, each popup, each dock window): every scope independently owns one
    // focused `focusable` target, so a popup and a text field stay focused at the same time. The
    // active scope is the one that last gained focus - Tab cycles within it.
    private val focusByScope = LinkedHashMap<UiNode, UiNode>()
    private val hoverChain = HashSet<UiNode>()
    private val runtimeStack = ArrayDeque<UiNode>()
    private var activeScope: UiNode? = null

    private fun primaryFocus(): UiNode? = activeScope?.let { focusByScope[it] } ?: focusByScope.values.firstOrNull()

    val focusedKey: String? get() = primaryFocus()?.id

    /** The cursor shape of the node currently under the pointer (for the window cursor). */
    val hoveredCursor: UiCursorShape
        get() = (draggingNode ?: hoveredNode)?.resolvedSnapshot?.cursor ?: UiCursorShape.DEFAULT

    var x = 0f
    var y = 0f

    private var scrollbarDrag: UiScrollbarDragState? = null

    fun reset() {
        clearInteraction()
    }

    fun clearInteraction(clearFocus: Boolean = true) {
        hoveredNode = null
        activeNode = null
        draggingNode = null
        scrollbarDrag = null
        if (clearFocus) {
            focusByScope.clear()
            activeScope = null
        }
    }

    fun isHovered(id: String): Boolean = hoveredNode?.id == id

    fun prepareRoot(root: UiNode, closing: Boolean = false) {
        remapTrackedNodes(root)
        applyRuntimeStates(root, closing)
    }

    /**
     * Re-binds interaction tracking (hover/active/focus/drag) to the current tree by id.
     * Compose may replace a node instance across a recomposition while keeping its id; without
     * this, the stale instance would fail identity checks and the node would lose its hover/
     * active/focus state for a frame — visibly resetting transitions (e.g. a hover animation
     * snapping back on click) and dropping press→release on re-parented nodes.
     */
    private fun remapTrackedNodes(root: UiNode) {
        hoveredNode = remapTracked(root, hoveredNode)
        activeNode = remapTracked(root, activeNode)
        draggingNode = remapTracked(root, draggingNode)
        activeScope = remapTracked(root, activeScope)?.takeIf { root.firstInSubtree { n -> n === it } != null }
        // Re-bind each scope/target to the current instance by id and drop entries whose scope or
        // target left the tree (e.g. a closed popup), so per-scope focus doesn't accumulate stale nodes.
        val remappedFocus = focusByScope.entries.mapNotNull { (scope, target) ->
            val s = relocate(root, scope) ?: return@mapNotNull null
            val t = relocate(root, target) ?: return@mapNotNull null
            s to t
        }
        focusByScope.clear()
        remappedFocus.forEach { (s, t) -> focusByScope[s] = t }
    }

    private fun remapTracked(root: UiNode, tracked: UiNode?): UiNode? {
        val id = tracked?.id ?: return tracked
        return root.firstInSubtree { it.id == id } ?: tracked
    }

    /** Re-binds a node to its current instance by id, or null if it left the tree entirely. */
    private fun relocate(root: UiNode, node: UiNode): UiNode? {
        node.id?.let { id -> return root.firstInSubtree { it.id == id } }
        return node.takeIf { root.firstInSubtree { n -> n === it } != null }
    }

    fun updateHover(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        dispatch: (UiEvent) -> Boolean,
    ): Boolean {
        val hit = frame.hitTest(mouseX, mouseY)
        val previousNode = hoveredNode
        hoveredNode = hit?.node

        x = mouseX
        y = mouseY

        if (previousNode === hoveredNode) return false

        previousNode
            ?.takeIf { it in frame.nodes }
            ?.let { dispatch(UiEvent(UiEventKind.EXIT, it, x = mouseX, y = mouseY)) }
        hoveredNode
            ?.takeIf { it in frame.nodes }
            ?.let {
                dispatch(UiEvent(UiEventKind.ENTER, it, x = mouseX, y = mouseY))
            }
        return true
    }

    fun dispatchHover(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        dispatch: (UiEvent) -> Boolean,
    ): Boolean {
        val node = hoveredNode?.takeIf { it in frame.nodes } ?: return false
        return dispatch(UiEvent(UiEventKind.HOVER, node, x = mouseX, y = mouseY))
    }

    fun focus(
        frame: HollowUiFrame,
        nodeKey: String?,
        dispatch: (UiEvent) -> Boolean,
    ): Boolean {
        if (nodeKey == null) {
            val hadFocus = focusByScope.isNotEmpty()
            clearAllFocus(frame, dispatch)
            return hadFocus
        }
        val node = frame.nodeByIdentifier(nodeKey) ?: return false
        if (!node.resolvedSnapshot.focusable) return false
        val scope = node.enclosingFocusScope() ?: return false
        val previous = focusByScope[scope]
        setFocus(frame, scope, node, dispatch)
        return previous !== node
    }

    fun scrollTargetAt(frame: HollowUiFrame, x: Float, y: Float): UiNode? {
        return frame.scrollTargetAt(x, y) ?: focusedScrollableNode(frame)
    }

    fun mouseClicked(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        dispatch: (UiEvent) -> Boolean,
        modifiers: Int = 0,
    ): UiInputResult {
        val hit = frame.hitTest(mouseX, mouseY) ?: run {
            clearAllFocus(frame, dispatch)
            return UiInputResult(false)
        }

        val layoutNode = frame.layout[hit.node]
        activeNode = hit.node
        updateFocus(frame, hit.node, dispatch)

        val press = UiEvent(
            kind = UiEventKind.PRESS,
            node = hit.node,
            frame = frame,
            button = button,
            modifiers = modifiers,
            x = mouseX,
            y = mouseY,
            localX = hit.localX,
            localY = hit.localY,
            width = layoutNode.rect.width,
            height = layoutNode.rect.height,
        )
        val pressHandled = dispatch(press)
        if (pressHandled && press.consumed) return UiInputResult(true, hit.node, hit.node.id)

        if (hit.node.resolvedSnapshot.draggable && button == 0) {
            draggingNode = hit.node
            dragStartX = mouseX
            dragStartY = mouseY
            return UiInputResult(true, hit.node, hit.node.id)
        }

        val clickHandled =
            dispatchClick(frame, hit.node, button, mouseX, mouseY, hit.localX, hit.localY, modifiers, dispatch)
        return UiInputResult(clickHandled, hit.node, hit.node.id)
    }

    fun scrollbarMouseClicked(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        setScrollImmediate: (UiNode, UiScrollOffset) -> Unit,
    ): UiInputResult {
        if (button != 0) return UiInputResult(false)
        return when (val hit = frame.hitTest(mouseX, mouseY)?.node) {
            is ScrollbarThumbNode -> {
                scrollbarDrag = scrollbarThumbDragState(frame.layout.nodes, hit, mouseX, mouseY)
                val container = hit.scrollbarContainer()
                UiInputResult(true, container, container?.id)
            }

            is ScrollbarNode -> {
                val jump = scrollbarTrackJumpOffset(frame.layout.nodes, hit, mouseX, mouseY)
                    ?: return UiInputResult(false)
                setScrollImmediate(jump.first, jump.second)
                UiInputResult(true, jump.first, jump.first.id, changed = true)
            }

            else -> UiInputResult(false)
        }
    }

    fun mouseDragged(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        deltaX: Float,
        deltaY: Float,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        val node = draggingNode?.takeIf { it in frame.nodes } ?: return UiInputResult(false)
        val local = frame.layout[node].inputTransform.inverse()?.transform(mouseX, mouseY, 0f)
        val layoutNode = frame.layout[node]
        val parent = frame.parentOf(node)
        val parentLayout = parent?.let { frame.layout[it] }
        val parentLocal = parentLayout?.inputTransform?.inverse()?.transform(mouseX, mouseY, 0f)
        val rootLocal = frame.layout.root.let { root ->
            frame.layout[root].inputTransform.inverse()?.transform(mouseX, mouseY, 0f)
        }
        val event = UiEvent(
            kind = UiEventKind.DRAG,
            node = node,
            frame = frame,
            button = button,
            x = mouseX,
            y = mouseY,
            localX = local?.x ?: 0f,
            localY = local?.y ?: 0f,
            width = layoutNode.rect.width,
            height = layoutNode.rect.height,
            parentLocalX = parentLocal?.x ?: 0f,
            parentLocalY = parentLocal?.y ?: 0f,
            parentWidth = parentLayout?.rect?.width ?: 0f,
            parentHeight = parentLayout?.rect?.height ?: 0f,
            rootLocalX = rootLocal?.x ?: mouseX,
            rootLocalY = rootLocal?.y ?: mouseY,
            ancestorLocalPositions = frame.ancestorLocalPositions(node, mouseX, mouseY),
            deltaX = deltaX,
            deltaY = deltaY,
            dragTotalX = mouseX - dragStartX,
            dragTotalY = mouseY - dragStartY,
        )
        val handled = dispatch(event)
        return UiInputResult(handled, node, node.id)
    }

    fun mouseReleased(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        dispatch: (UiEvent) -> Boolean,
        modifiers: Int = 0,
    ): UiInputResult {
        var received = false
        fun dispatch(node: UiNode): Boolean {
            val event =
                UiEvent(
                    kind = UiEventKind.RELEASE,
                    node = node,
                    frame = frame,
                    button = button,
                    modifiers = modifiers,
                    x = mouseX,
                    y = mouseY,
                    released = true
                )
            received = dispatch(event) || received
            return event.consumed
        }

        val handled = (frame.hitTest(mouseX, mouseY)?.node
            ?: activeNode?.takeIf { it in frame.nodes })?.let { dispatch(it) } ?: false

        val releaseNode = draggingNode?.takeIf { it in frame.nodes }
        if(!handled) releaseNode?.let { node -> dispatch(node) }
        activeNode = null
        draggingNode = null
        scrollbarDrag = null
        return UiInputResult(received, releaseNode, releaseNode?.id)
    }

    fun scrollbarMouseDragged(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        setScrollImmediate: (UiNode, UiScrollOffset) -> Unit,
    ): UiInputResult {
        val drag = scrollbarDrag ?: return UiInputResult(false)
        val node = drag.node.takeIf { it in frame.nodes } ?: return UiInputResult(false)
        setScrollImmediate(node, drag.offsetFor(frame.layout[node], mouseX, mouseY))
        return UiInputResult(true, node, node.id, changed = true)
    }

    fun hasScrollbarDrag(): Boolean = scrollbarDrag != null

    fun charTyped(
        frame: HollowUiFrame,
        codePoint: Char,
        modifiers: Int,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        val targets = focusTargets(frame)
        var handled = false
        for (node in targets) {
            val event =
                UiEvent(UiEventKind.CHAR_TYPED, node, frame = frame, modifiers = modifiers, codePoint = codePoint.code)
            if (dispatch(event)) handled = true
            if (event.consumed) return UiInputResult(true, node, node.id)
        }
        return UiInputResult(handled, primaryFocus(), primaryFocus()?.id)
    }

    /**
     * The nodes that currently receive key/char events, most-in-front first. Multi-focus: every open
     * focus scope (always active — the root, popups, dock windows) plus each scope's focused target.
     * Higher layer (popups/overlays) gets first refusal.
     */
    private fun focusTargets(frame: HollowUiFrame): List<UiNode> {
        val set = LinkedHashSet<UiNode>()
        focusByScope.values.forEach { if (it in frame.nodes) set += it }
        frame.nodes.forEach { if (it.resolvedSnapshot.focusScope) set += it }
        return set.sortedByDescending { it.resolvedSnapshot.layer }
    }

    fun keyPressed(
        frame: HollowUiFrame,
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        val targets = focusTargets(frame)
        val enterPressed = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
        val altPressed = modifiers and GLFW.GLFW_MOD_ALT != 0 || enterPressed && isAltPressed()
        val effectiveModifiers = if (altPressed) modifiers or GLFW.GLFW_MOD_ALT else modifiers
        var handled = false
        for (node in targets) {
            val event = UiEvent(
                UiEventKind.KEY_PRESSED,
                node,
                frame = frame,
                key = keyCode,
                scanCode = scanCode,
                modifiers = effectiveModifiers,
            )
            if (dispatch(event)) handled = true
            if (event.consumed) return UiInputResult(true, node, node.id)
        }
        if (keyCode == GLFW.GLFW_KEY_TAB && focusNext(frame, dispatch)) {
            return UiInputResult(true, primaryFocus(), primaryFocus()?.id)
        }
        return UiInputResult(handled, primaryFocus(), primaryFocus()?.id)
    }

    /** On a click, resolve focus within the hit node's nearest enclosing scope. */
    private fun updateFocus(frame: HollowUiFrame, node: UiNode, dispatch: (UiEvent) -> Boolean) {
        val scope = node.enclosingFocusScope() ?: return
        setFocus(frame, scope, node.focusTargetWithin(scope), dispatch)
    }

    /** Sets (or clears, when [target] is null) the focused target of [scope]; other scopes untouched. */
    private fun setFocus(frame: HollowUiFrame, scope: UiNode, target: UiNode?, dispatch: (UiEvent) -> Boolean) {
        val old = focusByScope[scope]
        if (old === target) return
        old?.takeIf { it in frame.nodes }?.let { node ->
            dispatch(UiEvent(UiEventKind.UNFOCUS, node))
        }
        if (target != null) {
            focusByScope[scope] = target
            activeScope = scope
            target.takeIf { it in frame.nodes }?.let { dispatch(UiEvent(UiEventKind.FOCUS, it)) }
        } else {
            focusByScope.remove(scope)
            if (activeScope === scope) activeScope = focusByScope.keys.lastOrNull()
        }
    }

    private fun clearAllFocus(frame: HollowUiFrame, dispatch: (UiEvent) -> Boolean) {
        if (focusByScope.isEmpty()) return
        focusByScope.values.toList().forEach { node ->
            if (node in frame.nodes) {
                dispatch(UiEvent(UiEventKind.UNFOCUS, node))
            }
        }
        focusByScope.clear()
        activeScope = null
    }

    private fun focusNext(frame: HollowUiFrame, dispatch: (UiEvent) -> Boolean): Boolean {
        val scope = activeScope ?: frame.nodes.lastOrNull { it.resolvedSnapshot.focusScope } ?: return false
        val targets = frame.nodes.filter { it.resolvedSnapshot.focusable && it.enclosingFocusScope() === scope }
        if (targets.isEmpty()) return false
        val current = focusByScope[scope]
        val currentIndex = targets.indexOfFirst { it === current }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % targets.size
        setFocus(frame, scope, targets[nextIndex], dispatch)
        return true
    }

    /** The nearest ancestor (or self) marked as a focus scope; the root is always one. */
    private fun UiNode.enclosingFocusScope(): UiNode? {
        var node: UiNode? = this
        while (node != null) {
            if (node.resolvedSnapshot.focusScope) return node
            node = node.layoutState.parentNode
        }
        return null
    }

    /** The focusable target the receiver belongs to within [scope], or null if it's not on one. */
    private fun UiNode.focusTargetWithin(scope: UiNode): UiNode? {
        var node: UiNode? = this
        while (node != null) {
            if (node.resolvedSnapshot.focusable) return node
            if (node === scope) return null
            node = node.layoutState.parentNode
        }
        return null
    }

    private fun dispatchClick(
        frame: HollowUiFrame,
        node: UiNode,
        button: Int,
        mouseX: Float,
        mouseY: Float,
        localX: Float,
        localY: Float,
        modifiers: Int,
        dispatch: (UiEvent) -> Boolean,
    ): Boolean {
        val layoutNode = frame.layout[node]
        return dispatch(
            UiEvent(
                kind = UiEventKind.CLICK,
                node = node,
                frame = frame,
                button = button,
                modifiers = modifiers,
                x = mouseX,
                y = mouseY,
                localX = localX,
                localY = localY,
                width = layoutNode.rect.width,
                height = layoutNode.rect.height,
            )
        )
    }

    private fun isAltPressed(): Boolean {
        val window = Minecraft.getInstance()?.window?.window ?: return false
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
    }

    private fun focusedScrollableNode(frame: HollowUiFrame): UiNode? {
        return focusByScope.values.firstOrNull {
            it in frame.layout.nodes &&
                    it.resolvedSnapshot.scrollable &&
                    frame.layout[it].scrollRange.hasScrollableAxis()
        }
    }

    private fun applyRuntimeStates(node: UiNode, closing: Boolean) {
        hoverChain.clear()

        var ancestor = hoveredNode
        while (ancestor != null) {
            hoverChain += ancestor
            ancestor = ancestor.layoutState.parentNode
        }

        runtimeStack.clear()
        runtimeStack.add(node)
        while (runtimeStack.isNotEmpty()) {
            val current = runtimeStack.removeLast()
            val states = linkedSetOf<UiState>()
            if (current in hoverChain) states += UiState.HOVER
            if (current === activeNode) states += UiState.ACTIVE
            if (current in focusByScope.values) states += UiState.FOCUS
            if (current === draggingNode) states += UiState.DRAGGING
            if (closing) states += UiState.CLOSING
            current.setRuntimeStates(states)
            for (index in current.children.indices.reversed()) {
                runtimeStack.add(current.children[index])
            }
        }
    }
}

data class UiInputResult(
    val handled: Boolean,
    val node: UiNode? = null,
    val nodeKey: String? = null,
    val changed: Boolean = false,
)

private fun HollowUiFrame.parentOf(node: UiNode): UiNode? {
    val stack = ArrayDeque<UiNode>()
    stack.add(root)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        for (child in current.children) {
            if (child === node) return current
            stack.add(child)
        }
    }
    return null
}

private fun HollowUiFrame.ancestorLocalPositions(node: UiNode, x: Float, y: Float): Map<String, UiVec3> {
    val ancestors = ancestorsOf(node)
    if (ancestors.isEmpty()) return emptyMap()
    val positions = linkedMapOf<String, UiVec3>()
    ancestors.forEach { ancestor ->
        val local = layout[ancestor].inputTransform.inverse()?.transform(x, y, 0f) ?: return@forEach
        ancestor.id?.let { positions[it] = local }
        val tags = ancestor.tags.readOnlyIterator()
        while (tags.hasNext()) positions[tags.next()] = local
    }
    return positions
}

private fun HollowUiFrame.ancestorsOf(node: UiNode): List<UiNode> {
    val parents = linkedMapOf<UiNode, UiNode?>()
    val stack = ArrayDeque<UiNode>()
    parents[root] = null
    stack.add(root)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        if (current === node) break
        for (child in current.children) {
            parents[child] = current
            stack.add(child)
        }
    }
    if (node !in parents) return emptyList()
    val result = ArrayDeque<UiNode>()
    var current = parents[node]
    while (current != null) {
        result.addFirst(current)
        current = parents[current]
    }
    return result.toList()
}
