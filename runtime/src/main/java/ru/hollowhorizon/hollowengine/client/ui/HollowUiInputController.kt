package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.layout.inlineWidgetMetrics
import ru.hollowhorizon.hollowengine.client.ui.scroll.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.*
import java.util.*
import kotlin.math.abs
import ru.hollowhorizon.hollowengine.client.ui.style.*

class HollowUiInputController {
    private var hoveredNode: UiNode? = null
    var hoveredLink: String? = null
        private set
    private var activeNode: UiNode? = null
    private var focusedNode: UiNode? = null
    private var draggingNode: UiNode? = null

    val focusedKey: String? get() = focusedNode?.id

    var x = 0f
    var y = 0f

    private var scrollbarDrag: UiScrollbarDragState? = null
    private var lastTextClickNode: TextFieldNode? = null
    private var lastTextClickAtMillis: Long = 0L
    private var lastTextClickIndex: Int = -1
    private var lastTextClickCount: Int = 0
    private var textAltSelectionAnchor: Int? = null

    fun reset() {
        clearInteraction()
    }

    fun clearInteraction(clearFocus: Boolean = true) {
        hoveredNode = null
        hoveredLink = null
        activeNode = null
        draggingNode = null
        scrollbarDrag = null
        textAltSelectionAnchor = null
        lastTextClickCount = 0
        if (clearFocus) focusedNode = null
    }

    fun isHovered(id: String): Boolean = hoveredNode?.id == id

    fun prepareRoot(root: UiNode, closing: Boolean = false) {
        root.forEachTextFields { field ->
            field.resolvePendingCompletions()
        }
        applyRuntimeStates(root, closing)
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
        hoveredLink = hit?.link

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
            val hadFocus = focusedNode != null
            setFocus(frame, null, dispatch)
            return hadFocus
        }
        val node = frame.nodeByIdentifier(nodeKey) ?: return false
        if (!node.resolvedSnapshot.input.focusable) return false
        val previous = focusedNode
        setFocus(frame, node, dispatch)
        return previous !== focusedNode
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
        openUrl: (String) -> Unit,
        modifiers: Int = 0,
    ): UiInputResult {
        val hit = frame.hitTest(mouseX, mouseY) ?: run {
            setFocus(frame, null, dispatch)
            return UiInputResult(false)
        }
        if (button == 0 && hit.link != null) {
            openUrl(hit.link)
            return UiInputResult(true, hit.node, hit.node.id, changed = false)
        }

        val layoutNode = frame.layout[hit.node]
        activeNode = hit.node
        updateFocus(frame, hit.node, dispatch)

        val press = UiEvent(
            kind = UiEventKind.PRESS,
            node = hit.node,
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

        if (button == 0 && applyBuiltInPointerPress(frame, hit.node, hit.localX, hit.localY)) {
            dispatchClick(frame, hit.node, button, mouseX, mouseY, hit.localX, hit.localY, modifiers, dispatch)
            if (hit.node is SliderNode || hit.node is TextFieldNode) {
                draggingNode = hit.node
            }
            return UiInputResult(true, hit.node, hit.node.id, changed = true)
        }

        if (hit.node.resolvedSnapshot.input.draggable && button == 0) {
            draggingNode = hit.node
            return UiInputResult(true, hit.node, hit.node.id)
        }

        val clickHandled = dispatchClick(frame, hit.node, button, mouseX, mouseY, hit.localX, hit.localY, modifiers, dispatch)
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
        val scrollbar = frame.scrollbarAt(mouseX, mouseY) ?: return UiInputResult(false)
        when (scrollbar.pointerAreaAt(mouseX, mouseY)) {
            UiScrollbarPointerArea.THUMB -> {
                scrollbarDrag = scrollbar.dragStateAt(mouseX, mouseY)
                return UiInputResult(true, scrollbar.node, scrollbar.node.id)
            }

            UiScrollbarPointerArea.TRACK -> {
                setScrollImmediate(scrollbar.node, scrollbar.trackClickOffset(frame.layout[scrollbar.node], mouseX, mouseY))
                return UiInputResult(true, scrollbar.node, scrollbar.node.id, changed = true)
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
        val node = draggingNode?.takeIf { it in frame.nodes } ?: return UiInputResult(false)
        var changed = false
        if (button == 0 && node is SliderNode) {
            changed = updateSliderFromMouse(frame, node, mouseX, mouseY)
        }
        if (button == 0 && node is TextFieldNode) {
            changed = updateTextFieldSelectionFromMouse(frame, node, mouseX, mouseY)
        }
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
        )
        val handled = dispatch(event)
        return UiInputResult(changed || handled, node, node.id, changed)
    }

    fun mouseReleased(
        frame: HollowUiFrame,
        mouseX: Float,
        mouseY: Float,
        button: Int,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        val releaseNode = frame.hitTest(mouseX, mouseY)?.node ?: activeNode?.takeIf { it in frame.nodes }
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
        activeNode = null
        draggingNode = null
        scrollbarDrag = null
        return UiInputResult(handled, releaseNode, releaseNode?.id)
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
        val node = focusedNode?.takeIf { it in frame.nodes } ?: return UiInputResult(false)
        val event = UiEvent(UiEventKind.CHAR_TYPED, node, modifiers = modifiers, codePoint = codePoint.code)
        val handled = dispatch(event)
        val hadCompletions = node is TextFieldNode && node.completionActive
        if (!event.consumed && node is TextFieldNode && node.typeCharacter(codePoint)) {
            if (codePoint.isCompletionTrigger() || hadCompletions) node.openCompletions()
            return UiInputResult(true, node, node.id, changed = true)
        }
        return UiInputResult(handled, node, node.id)
    }

    fun keyPressed(
        frame: HollowUiFrame,
        keyCode: Int,
        scanCode: Int,
        modifiers: Int,
        dispatch: (UiEvent) -> Boolean,
    ): UiInputResult {
        val node = focusedNode?.takeIf { it in frame.nodes } ?: return UiInputResult(false)
        val enterPressed = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER
        val altPressed = modifiers and GLFW.GLFW_MOD_ALT != 0 || enterPressed && isAltPressed()
        val effectiveModifiers = if (altPressed) modifiers or GLFW.GLFW_MOD_ALT else modifiers
        val event = UiEvent(
            UiEventKind.KEY_PRESSED,
            node,
            frame = frame,
            key = keyCode,
            scanCode = scanCode,
            modifiers = effectiveModifiers,
        )
        val handled = dispatch(event)
        if (event.changed && node is UiStatefulNode) {
            return UiInputResult(true, node, node.id, changed = true)
        }
        if (!event.consumed && keyCode == GLFW.GLFW_KEY_TAB && focusNext(frame, dispatch)) {
            return UiInputResult(true, focusedNode, focusedNode?.id)
        }
        return UiInputResult(handled, node, node.id)
    }

    private fun updateFocus(frame: HollowUiFrame, node: UiNode, dispatch: (UiEvent) -> Boolean) {
        if (node.resolvedSnapshot.input.focusable) {
            setFocus(frame, node, dispatch)
        } else {
            setFocus(frame, null, dispatch)
        }
    }

    private fun setFocus(frame: HollowUiFrame, nextNode: UiNode?, dispatch: (UiEvent) -> Boolean) {
        if (focusedNode === nextNode) return
        focusedNode?.takeIf { it in frame.nodes }?.let { node ->
            if (node is TextFieldNode) node.clearSelection()
            dispatch(UiEvent(UiEventKind.UNFOCUS, node))
        }
        focusedNode = nextNode
        focusedNode?.takeIf { it in frame.nodes }?.let { dispatch(UiEvent(UiEventKind.FOCUS, it)) }
    }

    private fun focusNext(frame: HollowUiFrame, dispatch: (UiEvent) -> Boolean): Boolean {
        val focusables = frame.nodes.filter { it.resolvedSnapshot.input.focusable }
        if (focusables.isEmpty()) return false
        val currentIndex = focusables.indexOfFirst { it === focusedNode }
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % focusables.size
        setFocus(frame, focusables[nextIndex], dispatch)
        return true
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
                val index = textFieldCaretIndexAt(frame, node, localX, localY)
                val altPressed = isAltPressed()
                val clickCount = textClickCount(node, index)
                textAltSelectionAnchor = null
                if (clickCount >= 3) {
                    val range = textFieldLineRangeAt(node.value, index)
                    if (node.multiCaret && altPressed) {
                        node.addCaretRange(UiTextCaret(range.end, range.start))
                    } else {
                        node.setSelection(range.start, range.end)
                    }
                } else if (clickCount == 2) {
                    val range = textFieldWordRangeAt(node.value, index)
                    if (node.multiCaret && altPressed) {
                        node.addCaretRange(UiTextCaret(range.end, range.start))
                    } else {
                        node.setSelection(range.start, range.end)
                    }
                } else if (node.multiCaret && altPressed) {
                    if (!node.removeCaretRangeAt(index)) {
                        node.addCaret(index)
                        textAltSelectionAnchor = index
                    }
                } else {
                    node.moveCaret(index)
                }
                rememberTextClick(node, index, clickCount)
                true
            }
            else -> false
        }
        return handled
    }

    private fun updateSliderFromMouse(frame: HollowUiFrame, node: SliderNode, mouseX: Float, mouseY: Float): Boolean {
        val layout = frame.layout[node]
        val inverse = layout.inputTransform.inverse() ?: return false
        val local = inverse.transform(mouseX, mouseY, 0f)
        val changed = node.setFromLocalX(local.x, layout.rect.width)
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
        val altAnchor = textAltSelectionAnchor
        if (altAnchor != null && node.multiCaret) {
            node.updateLastCaretRange(altAnchor, index)
        } else {
            node.setSelection(node.selectionAnchor ?: node.caret, index)
        }
        val changed = previousStart != node.selectionStart || previousEnd != node.selectionEnd
        return changed
    }

    private fun textFieldCaretIndexAt(frame: HollowUiFrame, node: TextFieldNode, localX: Float, localY: Float): Int {
        val layout = frame.layout[node]
        val style = node.resolvedSnapshot
        val textOffset = textFieldTextOffset(node, style)
        val contentX = localX - (layout.content.x - layout.rect.x) - textOffset + layout.scrollOffset.x
        val contentY = localY - (layout.content.y - layout.rect.y) + layout.scrollOffset.y
        val textLayout = textFieldEditLayout(node, style, layout, layout.inlineWidgetMetrics())
        return textLayout.caretIndexAt(contentX, contentY, style.fontSize, style.fontFamily)
    }


    private fun focusedTextField(frame: HollowUiFrame): TextFieldNode? {
        return focusedNode?.takeIf { it in frame.nodes } as? TextFieldNode
    }

    private fun textClickCount(node: TextFieldNode, index: Int): Int {
        val now = System.currentTimeMillis()
        val continues = lastTextClickNode === node &&
                now - lastTextClickAtMillis <= TextDoubleClickMillis &&
                abs(lastTextClickIndex - index) <= 1
        return if (continues) (lastTextClickCount + 1).coerceAtMost(3) else 1
    }

    private fun rememberTextClick(node: TextFieldNode, index: Int, count: Int) {
        lastTextClickNode = node
        lastTextClickIndex = index
        lastTextClickCount = count
        lastTextClickAtMillis = System.currentTimeMillis()
    }

    private fun isAltPressed(): Boolean {
        val window = Minecraft.getInstance()?.window?.window ?: return false
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
    }

    private fun focusedScrollableNode(frame: HollowUiFrame): UiNode? {
        return focusedNode
            ?.takeIf { it in frame.layout.nodes }
            ?.takeIf { it.resolvedSnapshot.input.scrollable && frame.layout[it].scrollRange.hasScrollableAxis() }
    }

    private fun applyRuntimeStates(node: UiNode, closing: Boolean) {
        val stack = ArrayDeque<UiNode>()
        stack.add(node)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val states = linkedSetOf<UiState>()
            if (current is TextNode) {
                current.hoveredLink = if (current === hoveredNode) hoveredLink else null
            }
            if (current === hoveredNode || current.containsNode(hoveredNode)) states += UiState.HOVER
            if (current === activeNode) states += UiState.ACTIVE
            if (current === focusedNode) states += UiState.FOCUS
            if (current === draggingNode) states += UiState.DRAGGING
            if (closing) states += UiState.CLOSING
            current.setRuntimeStates(states)
            for (index in current.children.indices.reversed()) {
                stack.add(current.children[index])
            }
        }
    }
}

private fun Char.isCompletionTrigger(): Boolean = this == '.' || this == '_' || isLetterOrDigit()

private const val TextDoubleClickMillis = 350L

internal data class ClickTextRange(
    val start: Int,
    val end: Int,
)

private fun textFieldWordRangeAt(text: String, caretIndex: Int): ClickTextRange {
    if (text.isEmpty()) return ClickTextRange(0, 0)
    val index = caretIndex.coerceIn(0, text.length)
    val characterIndex = when {
        index < text.length -> index
        else -> text.lastIndex
    }
    val character = text[characterIndex]
    val predicate: (Char) -> Boolean = when {
        character.isTextFieldWordChar() -> Char::isTextFieldWordChar
        character.isWhitespace() -> Char::isWhitespace
        else -> { candidate -> candidate == character }
    }
    var start = characterIndex
    var end = characterIndex + 1
    while (start > 0 && predicate(text[start - 1])) start--
    while (end < text.length && predicate(text[end])) end++
    return ClickTextRange(start, end)
}

internal fun textFieldLineRangeAt(text: String, caretIndex: Int): ClickTextRange {
    if (text.isEmpty()) return ClickTextRange(0, 0)
    val index = caretIndex.coerceIn(0, text.length)
    val start = text.lastIndexOf('\n', (index - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
    return ClickTextRange(start, end)
}

private fun Char.isTextFieldWordChar(): Boolean = this == '_' || isLetterOrDigit()

data class UiInputResult(
    val handled: Boolean,
    val node: UiNode? = null,
    val nodeKey: String? = null,
    val changed: Boolean = false,
)

private fun UiNode.containsNode(target: UiNode?): Boolean {
    if (target == null) return false
    val stack = ArrayDeque<UiNode>()
    children.asReversed().forEach(stack::add)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (node === target) return true
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
    return false
}

private fun UiNode.forEachTextFields(block: (TextFieldNode) -> Unit) {
    val stack = ArrayDeque<UiNode>()
    stack.add(this)
    while (stack.isNotEmpty()) {
        val node = stack.removeLast()
        if (node is TextFieldNode) block(node)
        for (index in node.children.indices.reversed()) {
            stack.add(node.children[index])
        }
    }
}

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
        ancestor.tags.forEach { tag -> positions[tag] = local }
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
