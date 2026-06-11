package ru.hollowhorizon.hollowengine.client.ui

import org.lwjgl.glfw.GLFW

class HollowUiInputController {
    var hoveredKey: String? = null
        private set
    var hoveredLink: String? = null
        private set
    var activeKey: String? = null
        private set
    var focusedKey: String? = null
        private set
    var draggingKey: String? = null
        private set

    private val stateStore = UiNodeStateStore()
    private var scrollbarDrag: UiScrollbarDragState? = null

    fun reset() {
        clearInteraction()
        stateStore.clear()
    }

    fun clearInteraction(clearFocus: Boolean = true) {
        hoveredKey = null
        hoveredLink = null
        activeKey = null
        draggingKey = null
        scrollbarDrag = null
        if (clearFocus) focusedKey = null
    }

    fun isHovered(id: String): Boolean = hoveredKey == id

    fun prepareRoot(root: UiNode, closing: Boolean = false) {
        UiNodeKeys.assign(root)
        stateStore.apply(root)
        applyRuntimeStates(root, closing)
    }

    fun updateHover(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        dispatch: (UiEvent) -> Boolean,
    ): Boolean {
        val hit = frame.hitTest(mouseX, mouseY)
        val previousKey = hoveredKey
        hoveredKey = hit?.node?.let(UiNodeKeys::key)
        hoveredLink = hit?.link
        if (previousKey == hoveredKey) return false

        previousKey?.let { key ->
            frame.nodeByKey(key)?.let { dispatch(UiEvent(UiEventKind.EXIT, it, x = mouseX, y = mouseY)) }
        }
        hoveredKey?.let { key ->
            frame.nodeByKey(key)?.let { dispatch(UiEvent(UiEventKind.ENTER, it, x = mouseX, y = mouseY)) }
        }
        return true
    }

    fun dispatchHover(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        dispatch: (UiEvent) -> Boolean,
    ): Boolean {
        val node = hoveredKey?.let(frame::nodeByKey) ?: return false
        return dispatch(UiEvent(UiEventKind.HOVER, node, x = mouseX, y = mouseY))
    }

    fun mouseClicked(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        dispatch: (UiEvent) -> Boolean,
        openUrl: (String) -> Unit,
    ): UiInputResult {
        val hit = frame.hitTest(mouseX, mouseY) ?: run {
            setFocus(frame, null, dispatch)
            return UiInputResult(false)
        }
        if (button == 0 && hit.link != null) {
            openUrl(hit.link)
            return UiInputResult(true, hit.node, UiNodeKeys.key(hit.node), changed = false)
        }

        val key = UiNodeKeys.key(hit.node)
        activeKey = key
        updateFocus(frame, hit.node, dispatch)

        val press = UiEvent(
            kind = UiEventKind.PRESS,
            node = hit.node,
            button = button,
            x = mouseX,
            y = mouseY,
            localX = hit.localX,
            localY = hit.localY,
        )
        val pressHandled = dispatch(press)
        if (pressHandled && press.consumed) return UiInputResult(true, hit.node, key)

        if (button == 0 && applyBuiltInPointerPress(frame, hit.node, hit.localX, hit.localY)) {
            dispatchClick(hit.node, button, mouseX, mouseY, hit.localX, hit.localY, dispatch)
            if (hit.node is SliderNode || hit.node is TextFieldNode) {
                draggingKey = key
                hit.node.states += UiState.DRAGGING
            }
            return UiInputResult(true, hit.node, key, changed = true)
        }

        if (frame.resolved[hit.node].input.draggable && button == 0) {
            draggingKey = key
            hit.node.states += UiState.DRAGGING
            return UiInputResult(true, hit.node, key)
        }

        val clickHandled = dispatchClick(hit.node, button, mouseX, mouseY, hit.localX, hit.localY, dispatch)
        return UiInputResult(clickHandled, hit.node, key)
    }

    fun scrollbarMouseClicked(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        setScrollImmediate: (UiNode, UiScrollOffset) -> Unit,
    ): UiInputResult {
        if (button != 0) return UiInputResult(false)
        val scrollbar = frame.scrollbarAt(mouseX, mouseY) ?: return UiInputResult(false)
        when (scrollbar.pointerAreaAt(mouseX, mouseY)) {
            UiScrollbarPointerArea.THUMB -> {
                scrollbarDrag = scrollbar.dragStateAt(mouseX, mouseY)
                return UiInputResult(true, scrollbar.node, UiNodeKeys.key(scrollbar.node))
            }

            UiScrollbarPointerArea.TRACK -> {
                setScrollImmediate(scrollbar.node, scrollbar.trackClickOffset(frame.layout[scrollbar.node], mouseX, mouseY))
                return UiInputResult(true, scrollbar.node, UiNodeKeys.key(scrollbar.node), changed = true)
            }

            null -> return UiInputResult(false)
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
        val key = draggingKey ?: return UiInputResult(false)
        val node = frame.nodeByKey(key) ?: return UiInputResult(false)
        var changed = false
        if (button == 0 && node is SliderNode) {
            changed = updateSliderFromMouse(frame, node, mouseX, mouseY)
        }
        if (button == 0 && node is TextFieldNode) {
            changed = updateTextFieldSelectionFromMouse(frame, node, mouseX, mouseY)
        }
        val local = frame.layout[node].inputTransform.inverse()?.transform(mouseX, mouseY, 0f)
        val parent = frame.parentOf(node)
        val parentLayout = parent?.let { frame.layout[it] }
        val parentLocal = parentLayout?.inputTransform?.inverse()?.transform(mouseX, mouseY, 0f)
        val rootLocal = frame.layout.root.let { root ->
            frame.layout[root].inputTransform.inverse()?.transform(mouseX, mouseY, 0f)
        }
        val event = UiEvent(
            kind = UiEventKind.DRAG,
            node = node,
            button = button,
            x = mouseX,
            y = mouseY,
            localX = local?.x ?: 0f,
            localY = local?.y ?: 0f,
            parentLocalX = parentLocal?.x ?: 0f,
            parentLocalY = parentLocal?.y ?: 0f,
            parentWidth = parentLayout?.rect?.width ?: 0f,
            parentHeight = parentLayout?.rect?.height ?: 0f,
            rootLocalX = rootLocal?.x ?: mouseX,
            rootLocalY = rootLocal?.y ?: mouseY,
            ancestorLocalPositions = frame.ancestorLocalPositions(node, mouseX, mouseY),
            deltaX = deltaX,
            deltaY = deltaY,
        )
        val handled = dispatch(event)
        return UiInputResult(changed || handled, node, key, changed)
    }

    fun mouseReleased(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        val releaseNode = frame.hitTest(mouseX, mouseY)?.node ?: activeKey?.let(frame::nodeByKey)
        val handled = releaseNode?.let { node ->
            dispatch(
                UiEvent(
                    kind = UiEventKind.RELEASE,
                    node = node,
                    button = button,
                    x = mouseX,
                    y = mouseY,
                    released = true,
                )
            )
        } ?: false
        val key = releaseNode?.let(UiNodeKeys::key)
        activeKey = null
        draggingKey = null
        scrollbarDrag = null
        return UiInputResult(handled, releaseNode, key)
    }

    fun scrollbarMouseDragged(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        setScrollImmediate: (UiNode, UiScrollOffset) -> Unit,
    ): UiInputResult {
        val drag = scrollbarDrag ?: return UiInputResult(false)
        val node = frame.nodeByKey(drag.nodeKey) ?: return UiInputResult(false)
        setScrollImmediate(node, drag.offsetFor(frame.layout[node], mouseX, mouseY))
        return UiInputResult(true, node, UiNodeKeys.key(node), changed = true)
    }

    fun hasScrollbarDrag(): Boolean = scrollbarDrag != null

    fun charTyped(
        frame: HollowUiFrame,
        codePoint: Char,
        modifiers: Int,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        val node = focusedKey?.let(frame::nodeByKey) ?: return UiInputResult(false)
        val event = UiEvent(UiEventKind.CHAR_TYPED, node, modifiers = modifiers, codePoint = codePoint.code)
        val handled = dispatch(event)
        if (!event.consumed && node is TextFieldNode && node.insert(codePoint.toString())) {
            stateStore.save(node)
            return UiInputResult(true, node, UiNodeKeys.key(node), changed = true)
        }
        return UiInputResult(handled, node, UiNodeKeys.key(node))
    }

    fun keyPressed(
        frame: HollowUiFrame,
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        if (keyCode == GLFW.GLFW_KEY_TAB && focusNext(frame, dispatch)) {
            return UiInputResult(true, focusedKey?.let(frame::nodeByKey), focusedKey)
        }

        val node = focusedKey?.let(frame::nodeByKey) ?: return UiInputResult(false)
        val event = UiEvent(UiEventKind.KEY_PRESSED, node, key = keyCode, scanCode = scanCode, modifiers = modifiers)
        val handled = dispatch(event)
        if (!event.consumed && node is TextFieldNode && applyTextFieldKey(node, keyCode, modifiers)) {
            return UiInputResult(true, node, UiNodeKeys.key(node), changed = true)
        }
        return UiInputResult(handled, node, UiNodeKeys.key(node))
    }

    private fun updateFocus(frame: HollowUiFrame, node: UiNode, dispatch: (UiEvent) -> Boolean) {
        if (frame.resolved[node].input.focusable) {
            setFocus(frame, UiNodeKeys.key(node), dispatch)
        } else {
            setFocus(frame, null, dispatch)
        }
    }

    private fun setFocus(frame: HollowUiFrame, nextKey: String?, dispatch: (UiEvent) -> Boolean) {
        if (focusedKey == nextKey) return
        focusedKey?.let { key ->
            frame.nodeByKey(key)?.let { node ->
                if (node is TextFieldNode) {
                    node.clearSelection()
                    stateStore.save(node)
                }
                dispatch(UiEvent(UiEventKind.UNFOCUS, node))
            }
        }
        focusedKey = nextKey
        focusedKey?.let { key ->
            frame.nodeByKey(key)?.let { dispatch(UiEvent(UiEventKind.FOCUS, it)) }
        }
    }

    private fun focusNext(frame: HollowUiFrame, dispatch: (UiEvent) -> Boolean): Boolean {
        val focusables = frame.resolved.styles.keys.filter { frame.resolved[it].input.focusable }
        if (focusables.isEmpty()) return false
        val currentIndex = focusables.indexOfFirst { UiNodeKeys.key(it) == focusedKey }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % focusables.size
        setFocus(frame, UiNodeKeys.key(focusables[nextIndex]), dispatch)
        return true
    }

    private fun dispatchClick(
        node: UiNode,
        button: Int,
        mouseX: Float,
        mouseY: Float,
        localX: Float,
        localY: Float,
        dispatch: (UiEvent) -> Boolean,
    ): Boolean {
        return dispatch(
            UiEvent(
                kind = UiEventKind.CLICK,
                node = node,
                button = button,
                x = mouseX,
                y = mouseY,
                localX = localX,
                localY = localY,
            )
        )
    }

    private fun applyBuiltInPointerPress(frame: HollowUiFrame, node: UiNode, localX: Float, localY: Float): Boolean {
        val handled = when (node) {
            is SliderNode -> {
                val width = frame.layout.nodes[node]?.rect?.width ?: return false
                node.setFromLocalX(localX, width)
                true
            }
            is CheckboxNode -> {
                node.toggle()
                true
            }
            is TextFieldNode -> {
                node.moveCaret(textFieldCaretIndexAt(frame, node, localX, localY))
                true
            }
            else -> false
        }
        if (handled) stateStore.save(node)
        return handled
    }

    private fun updateSliderFromMouse(frame: HollowUiFrame, node: SliderNode, mouseX: Float, mouseY: Float): Boolean {
        val layout = frame.layout[node]
        val inverse = layout.inputTransform.inverse() ?: return false
        val local = inverse.transform(mouseX, mouseY, 0f)
        val changed = node.setFromLocalX(local.x, layout.rect.width)
        if (changed) stateStore.save(node)
        return changed
    }

    private fun updateTextFieldSelectionFromMouse(
        frame: HollowUiFrame,
        node: TextFieldNode,
        mouseX: Float,
        mouseY: Float,
    ): Boolean {
        val layout = frame.layout[node]
        val inverse = layout.inputTransform.inverse() ?: return false
        val local = inverse.transform(mouseX, mouseY, 0f)
        val index = textFieldCaretIndexAt(frame, node, local.x, local.y)
        val previousStart = node.selectionStart
        val previousEnd = node.selectionEnd
        node.setSelection(node.selectionAnchor ?: node.caret, index)
        val changed = previousStart != node.selectionStart || previousEnd != node.selectionEnd
        if (changed) stateStore.save(node)
        return changed
    }

    private fun applyTextFieldKey(node: TextFieldNode, keyCode: Int, modifiers: Int): Boolean {
        val select = modifiers and GLFW.GLFW_MOD_SHIFT != 0
        val control = modifiers and GLFW.GLFW_MOD_CONTROL != 0
        val changed = when (keyCode) {
            GLFW.GLFW_KEY_BACKSPACE -> node.backspace()
            GLFW.GLFW_KEY_DELETE -> node.deleteForward()
            GLFW.GLFW_KEY_A -> {
                if (!control) {
                    false
                } else {
                    node.selectAll()
                    true
                }
            }
            GLFW.GLFW_KEY_LEFT -> {
                node.moveCaret(node.caret - 1, select)
                true
            }
            GLFW.GLFW_KEY_RIGHT -> {
                node.moveCaret(node.caret + 1, select)
                true
            }
            GLFW.GLFW_KEY_HOME -> {
                node.moveCaret(0, select)
                true
            }
            GLFW.GLFW_KEY_END -> {
                node.moveCaret(node.value.length, select)
                true
            }
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> node.multiline && node.insert("\n")
            else -> false
        }
        if (changed) stateStore.save(node)
        return changed
    }

    private fun textFieldCaretIndexAt(frame: HollowUiFrame, node: TextFieldNode, localX: Float, localY: Float): Int {
        val layout = frame.layout[node]
        val style = frame.resolved[node]
        val contentX = localX - (layout.content.x - layout.rect.x) + layout.scrollOffset.x
        val contentY = localY - (layout.content.y - layout.rect.y) + layout.scrollOffset.y
        val textHeight = if (style.input.scrollable) Float.POSITIVE_INFINITY else layout.content.height
        val textLayout = UiTextLayouter.layout(
            text = node.value,
            width = layout.content.width,
            height = textHeight,
            wrap = style.textWrap && node.multiline,
            align = style.textAlign,
            fontSize = style.fontSize,
            fontFamily = style.fontFamily,
            preserveWhitespace = true,
        )
        return textLayout.caretIndexAt(contentX, contentY, style.fontSize, style.fontFamily)
    }

    private fun applyRuntimeStates(node: UiNode, closing: Boolean) {
        val key = UiNodeKeys.key(node)
        node.states -= UiState.HOVER
        node.states -= UiState.ACTIVE
        node.states -= UiState.DRAGGING
        if (key != focusedKey) node.states -= UiState.FOCUS
        if (node is TextNode) {
            node.hoveredLink = if (key == hoveredKey) hoveredLink else null
        }
        if (key == hoveredKey || node.containsNodeKey(hoveredKey)) node.states += UiState.HOVER
        if (key == activeKey) node.states += UiState.ACTIVE
        if (key == focusedKey) node.states += UiState.FOCUS
        if (key == draggingKey) node.states += UiState.DRAGGING
        if (closing) {
            node.states += UiState.CLOSING
        } else {
            node.states -= UiState.CLOSING
        }
        node.children.forEach { applyRuntimeStates(it, closing) }
    }
}

data class UiInputResult(
    val handled: Boolean,
    val node: UiNode? = null,
    val nodeKey: String? = null,
    val changed: Boolean = false,
)

private fun UiNode.containsNodeKey(key: String?): Boolean {
    if (key == null) return false
    return children.any { UiNodeKeys.key(it) == key || it.containsNodeKey(key) }
}

private fun HollowUiFrame.parentOf(node: UiNode): UiNode? {
    fun find(current: UiNode): UiNode? {
        if (current.children.any { it === node }) return current
        return current.children.firstNotNullOfOrNull(::find)
    }
    return find(resolved.root)
}

private fun HollowUiFrame.ancestorLocalPositions(node: UiNode, x: Float, y: Float): Map<String, UiVec3> {
    val ancestors = ancestorsOf(node)
    if (ancestors.isEmpty()) return emptyMap()
    val positions = linkedMapOf<String, UiVec3>()
    ancestors.forEach { ancestor ->
        val local = layout[ancestor].inputTransform.inverse()?.transform(x, y, 0f) ?: return@forEach
        positions[UiNodeKeys.key(ancestor)] = local
        ancestor.id?.let { positions[it] = local }
        ancestor.tags.forEach { tag -> positions[tag] = local }
    }
    return positions
}

private fun HollowUiFrame.ancestorsOf(node: UiNode): List<UiNode> {
    fun find(current: UiNode, path: List<UiNode>): List<UiNode>? {
        if (current === node) return path
        for (child in current.children) {
            find(child, path + current)?.let { return it }
        }
        return null
    }
    return find(resolved.root, emptyList()).orEmpty()
}
