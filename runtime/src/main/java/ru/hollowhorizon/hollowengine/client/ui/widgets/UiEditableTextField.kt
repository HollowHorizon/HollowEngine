package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.scroll.rememberScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkKeyframes
import ru.hollowhorizon.hollowengine.client.ui.style.UiCaretBlinkPeriodMillis
import ru.hollowhorizon.hollowengine.client.ui.text.*
import kotlin.math.abs

/** One logical line of the field's text: its offset in the document and its text (no newline). */
internal data class EditableFieldLine(val start: Int, val text: String) {
    val end: Int get() = start + text.length
}

internal fun editableFieldLines(text: String): List<EditableFieldLine> {
    val lines = ArrayList<EditableFieldLine>()
    var start = 0
    while (true) {
        val newline = text.indexOf('\n', start)
        if (newline < 0) {
            lines += EditableFieldLine(start, text.substring(start))
            return lines
        }
        lines += EditableFieldLine(start, text.substring(start, newline))
        start = newline + 1
    }
}

internal class EditableFieldLayout(
    val lines: List<EditableFieldLine>,
    private val offsets: FloatArray, // offsets[i] = top of line i; offsets[lines.size] = bottom
    val lineLayouts: Array<UiTextLayout?>,
    val fontSize: Float,
    val fontFamily: String?,
    val contentWidth: Float,
) {
    private val trailingMargin = fontSize
    val height: Float get() = (offsets.lastOrNull() ?: 0f) + trailingMargin

    fun lineTop(index: Int): Float = offsets[index]

    private fun lineAtY(y: Float): Int {
        if (lines.isEmpty()) return 0
        var lo = 0
        var hi = lines.size - 1
        var result = lines.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (offsets[mid + 1] > y) {
                result = mid
                hi = mid - 1
            } else {
                lo = mid + 1
            }
        }
        return result.coerceIn(0, lines.size - 1)
    }

    /** The inclusive index band whose rows fall within the scrolled viewport (plus overscan). */
    fun visibleRange(scrollY: Float, viewportHeight: Float, overscan: Float): IntRange {
        if (lines.isEmpty()) return IntRange.EMPTY
        return lineAtY(scrollY - overscan)..lineAtY(scrollY + viewportHeight + overscan)
    }

    /** Content-space point -> document offset. */
    fun offsetAt(x: Float, y: Float): Int {
        if (lines.isEmpty()) return 0
        val index = lineAtY(y)
        val line = lines[index]
        val local = lineLayouts[index]
        val column = local?.caretIndexAt(x, y - offsets[index], fontSize, fontFamily)?.coerceIn(0, line.text.length)
            ?: nearestColumn(line.text, x, fontSize, fontFamily)
        return line.start + column
    }

    /** Document offset -> content-space caret top-left. */
    fun caretAt(offset: Int): UiVec3 {
        if (lines.isEmpty()) return UiVec3()
        val index = lines.indexOfLast { it.start <= offset }.coerceIn(0, lines.size - 1)
        val line = lines[index]
        val column = (offset - line.start).coerceIn(0, line.text.length)
        val local = lineLayouts[index]
        return if (local != null) {
            val p = local.caretPosition(column, fontSize, fontFamily)
            UiVec3(p.x, offsets[index] + p.y)
        } else {
            UiVec3(UiTextLayouter.measureTextWidth(line.text.take(column), fontSize, fontFamily), offsets[index])
        }
    }
}

internal fun computeEditableFieldLayout(
    text: String,
    fontSize: Float,
    fontFamily: String?,
    wrap: Boolean,
    viewportWidth: Float,
): EditableFieldLayout {
    val lines = editableFieldLines(text)
    val offsets = FloatArray(lines.size + 1)
    val layouts = arrayOfNulls<UiTextLayout>(lines.size)
    val uniformHeight = UiTextLayouter.layout(
        "X", Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, false, UiTextAlign.LEFT, fontSize, fontFamily,
        preserveWhitespace = true,
    ).height
    val wrapping = wrap && viewportWidth > 0f
    var maxWidth = 0f
    var y = 0f
    for (index in lines.indices) {
        offsets[index] = y
        if (wrapping) {
            val layout = UiTextLayouter.layout(
                lines[index].text, viewportWidth, Float.POSITIVE_INFINITY, true, UiTextAlign.LEFT, fontSize, fontFamily,
                preserveWhitespace = true,
            )
            layouts[index] = layout
            y += maxOf(layout.height, uniformHeight)
        } else {
            maxWidth = maxOf(maxWidth, UiTextLayouter.measureTextWidth(lines[index].text, fontSize, fontFamily))
            y += uniformHeight
        }
    }
    offsets[lines.size] = y
    // A trailing margin (one glyph) so the scroll runs a touch past the last column/line.
    val contentWidth = if (wrapping) viewportWidth else maxWidth + fontSize
    return EditableFieldLayout(lines, offsets, layouts, fontSize, fontFamily, contentWidth)
}

/** Nearest caret column to [x] within [text] (prefix-width scan, non-wrapped lines). */
private fun nearestColumn(text: String, x: Float, fontSize: Float, fontFamily: String?): Int {
    var best = 0
    var bestDistance = Float.POSITIVE_INFINITY
    for (column in 0..text.length) {
        val distance = abs(UiTextLayouter.measureTextWidth(text.take(column), fontSize, fontFamily) - x)
        if (distance < bestDistance) {
            bestDistance = distance
            best = column
        }
    }
    return best
}

/**
 * A text field measured and rendered with one consistent layout ([EditableFieldLayout]): a single
 * sized content box holds only the rows in the scrolled viewport (manual virtualization), each row
 * carrying its own glyphs plus that line's selection and caret(s). Editing lives in [TextFieldState].
 */
@Composable
fun EditableTextField(
    state: TextFieldState,
    modifier: Modifier? = null,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    scrollState: UiScrollHandle = rememberScrollState(),
) {
    val text = state.text
    val fontSize = state.fontSize
    val fontFamily = state.fontFamily
    val wrap = state.wrap
    state.caretVisibilityRevision
    val viewportWidth = if (wrap) scrollState.viewport.width else 0f
    val layout = remember(text, fontSize, fontFamily, wrap, viewportWidth) {
        computeEditableFieldLayout(text, fontSize, fontFamily, wrap, viewportWidth)
    }
    val autoScroll = remember { EditableFieldAutoScroll() }
    SideEffect { autoScroll.follow(state, layout, scrollState) }

    val visible = layout.visibleRange(scrollState.offsetY, scrollState.viewport.height, overscan = fontSize * 4f)

    Box(
        id = id,
        tags = tags,
        mode = UiBoxMode.STACK,
        modifier = Modifier
            .focus()
            .scroll(vertical = true, horizontal = !wrap, state = scrollState)
            .whitespace(UiWhitespace.PRESERVE)
            .fontSize(fontSize)
            .let { if (fontFamily != null) it.fontFamily(fontFamily) else it }
            .let { state.textShadow?.let { shadow -> it.textEffects(shadow) } ?: it }
            .onCharTyped { event ->
                val char = event.codePoint.toChar()
                if (event.codePoint != 0 && !char.isISOControl() && state.typeCharacter(char)) event.consume()
            }
            .onKeyInput { input -> if (handleEditableFieldKey(state, input)) input.consume() }
            .onPress { event ->
                val offset = clickOffset(event, layout)
                autoScroll.dragAnchor = offset
                if (event.modifiers and GLFW.GLFW_MOD_ALT != 0) state.addCaret(offset) else state.moveCaret(offset)
            }
            .onDrag { event -> state.setSelection(autoScroll.dragAnchor, clickOffset(event, layout)) }
            .then(modifier ?: Modifier),
    ) {
        Box(mode = UiBoxMode.STACK, modifier = Modifier.size(layout.contentWidth.px, layout.height.px)) {
            for (index in visible) {
                key(index) {
                    EditableFieldRow(state, index, layout)
                }
            }
        }
    }
}

@Composable
private fun EditableFieldRow(state: TextFieldState, index: Int, layout: EditableFieldLayout) {
    val line = layout.lines[index]
    val top = layout.lineTop(index)
    val lineLayout = layout.lineLayouts[index]
    val fontSize = layout.fontSize
    val fontFamily = layout.fontFamily
    val ranges = state.caretRanges.filter { it.selectionStart <= line.end && it.selectionEnd >= line.start }

    ranges.filter { it.hasSelection }.forEachIndexed { rangeIndex, range ->
        val localStart = (range.selectionStart - line.start).coerceIn(0, line.text.length)
        val localEnd = (range.selectionEnd - line.start).coerceIn(0, line.text.length)
        val crossesNewline = range.selectionEnd > line.end
        selectionRectsForRow(line, lineLayout, localStart, localEnd, crossesNewline, fontSize, fontFamily, layout.contentWidth)
            .forEachIndexed { rectIndex, rect ->
                key("sel", rangeIndex, rectIndex) {
                    Box(
                        modifier = Modifier
                            .position(rect.x.px, (top + rect.y).px)
                            .size(rect.width.px, rect.height.px)
                            .background(state.selectionColor),
                    )
                }
            }
    }

    if (lineLayout != null) {
        lineLayout.lines.forEach { visual ->
            if (visual.text.isNotEmpty()) {
                key("v", visual.y) {
                    Text(modifier = Modifier.position(visual.x.px, (top + visual.y).px).textWrap(false)) {
                        Span(visual.text)
                    }
                }
            }
        }
    } else if (line.text.isNotEmpty()) {
        Text(modifier = Modifier.position(0.px, top.px).textWrap(false)) { Span(line.text) }
    }

    key(state.caretVisibilityRevision) {
        ranges.filter { it.position in line.start..line.end }.forEachIndexed { caretIndex, range ->
            val caret = layout.caretAt(range.position)
            key("caret", caretIndex) {
                Box(
                    modifier = Modifier
                        .position(caret.x.px, caret.y.px)
                        .size(TextFieldCaretWidth.px, fontSize.px)
                        .background(state.caretColor)
                        .layer(1)
                        .animation(UiCaretBlinkKeyframes, UiCaretBlinkPeriodMillis, iterationCount = Float.POSITIVE_INFINITY),
                )
            }
        }
    }
}

private fun selectionRectsForRow(
    line: EditableFieldLine,
    lineLayout: UiTextLayout?,
    localStart: Int,
    localEnd: Int,
    crossesNewline: Boolean,
    fontSize: Float,
    fontFamily: String?,
    fullWidth: Float,
): List<UiRect> {
    if (lineLayout != null) {
        val rects = lineLayout.selectionRects(localStart, localEnd, fontSize, fontFamily, fillLineGaps = true)
        if (!crossesNewline || rects.isEmpty()) return rects
        val last = rects.last()
        return rects + UiRect(last.x + last.width, last.y, (fullWidth - (last.x + last.width)).coerceAtLeast(0f), fontSize)
    }
    val x1 = UiTextLayouter.measureTextWidth(line.text.take(localStart), fontSize, fontFamily)
    val x2 = UiTextLayouter.measureTextWidth(line.text.take(localEnd), fontSize, fontFamily)
    val right = if (crossesNewline) fullWidth else x2
    if (right <= x1) return emptyList()
    return listOf(UiRect(x1, 0f, right - x1, fontSize))
}

/** Maps a pointer event on the field to a document offset (padding + scroll aware). */
private fun clickOffset(event: UiEvent, layout: EditableFieldLayout): Int {
    val fieldLayout = event.frame?.layout?.nodes?.get(event.node) ?: return 0
    val x = event.localX - (fieldLayout.content.x - fieldLayout.rect.x) + fieldLayout.scrollOffset.x
    val y = event.localY - (fieldLayout.content.y - fieldLayout.rect.y) + fieldLayout.scrollOffset.y
    return layout.offsetAt(x, y)
}

/** Follows the primary caret with the scroll so it stays inside the viewport (with a margin). */
private class EditableFieldAutoScroll {
    var dragAnchor = 0
    private var lastRevision = Int.MIN_VALUE

    fun follow(state: TextFieldState, layout: EditableFieldLayout, scrollState: UiScrollHandle) {
        if (lastRevision == state.caretVisibilityRevision) return
        lastRevision = state.caretVisibilityRevision
        val viewport = scrollState.viewport
        if (viewport.height <= 0f) return

        val caret = layout.caretAt(state.primaryCaret.position)
        val margin = layout.fontSize

        val targetY = when {
            caret.y < scrollState.offsetY + margin -> (caret.y - margin).coerceAtLeast(0f)
            caret.y + layout.fontSize > scrollState.offsetY + viewport.height - margin ->
                caret.y + layout.fontSize + margin - viewport.height

            else -> null
        }
        val targetX = when {
            state.wrap -> null
            caret.x < scrollState.offsetX + margin -> (caret.x - margin).coerceAtLeast(0f)
            caret.x > scrollState.offsetX + viewport.width - margin -> caret.x + margin - viewport.width
            else -> null
        }
        if (targetX != null || targetY != null) scrollState.scrollTo(targetX, targetY)
    }
}

/**
 * Maps a key press to a [TextFieldState] edit/navigation operation. Returns whether the field handled the key.
 */
internal fun handleEditableFieldKey(state: TextFieldState, input: UiKeyInput): Boolean {
    val text = state.text
    // Ctrl+Alt+Up/Down drops an extra caret on the line above/below the primary one.
    if (input.control && input.alt && (input.key == GLFW.GLFW_KEY_UP || input.key == GLFW.GLFW_KEY_DOWN)) {
        val delta = if (input.key == GLFW.GLFW_KEY_UP) -1 else 1
        state.addCaret(verticalCaretMove(text, state.primaryCaret.position, delta))
        return true
    }
    if (input.command) {
        when (input.key) {
            GLFW.GLFW_KEY_A -> return state.selectAll().let { true }
            GLFW.GLFW_KEY_C -> return copySelection(state)
            GLFW.GLFW_KEY_X -> return cutSelection(state)
            GLFW.GLFW_KEY_V -> return pasteClipboard(state)
            GLFW.GLFW_KEY_Z -> return if (input.shift) state.redo() else state.undo()
            GLFW.GLFW_KEY_Y -> return state.redo()
        }
    }
    return when (input.key) {
        GLFW.GLFW_KEY_BACKSPACE -> state.backspace(word = input.control)
        GLFW.GLFW_KEY_DELETE -> state.deleteForward(word = input.control)

        GLFW.GLFW_KEY_LEFT -> {
            state.moveCarets({ if (input.control) wordLeft(text, it.position) else it.position - 1 }, input.shift)
            true
        }

        GLFW.GLFW_KEY_RIGHT -> {
            state.moveCarets({ if (input.control) wordRight(text, it.position) else it.position + 1 }, input.shift)
            true
        }

        GLFW.GLFW_KEY_UP -> {
            state.moveCarets({ verticalCaretMove(text, it.position, -1) }, input.shift)
            true
        }

        GLFW.GLFW_KEY_DOWN -> {
            state.moveCarets({ verticalCaretMove(text, it.position, 1) }, input.shift)
            true
        }

        GLFW.GLFW_KEY_HOME -> {
            state.moveCarets({ if (input.control) 0 else lineStart(text, it.position) }, input.shift)
            true
        }

        GLFW.GLFW_KEY_END -> {
            state.moveCarets({ if (input.control) text.length else lineEnd(text, it.position) }, input.shift)
            true
        }

        GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> state.insertNewlineWithIndent()
        GLFW.GLFW_KEY_TAB -> if (input.shift) state.unindent() else state.indent()
        else -> false
    }
}

/** Moves an offset [delta] logical lines up/down, keeping the column (clamped to the target line). */
internal fun verticalCaretMove(text: String, position: Int, delta: Int): Int {
    var start = lineStart(text, position)
    val column = position - start
    repeat(abs(delta)) {
        start = if (delta < 0) {
            if (start == 0) return 0
            lineStart(text, start - 1)
        } else {
            val end = lineEnd(text, start)
            if (end >= text.length) return text.length
            end + 1
        }
    }
    val length = lineEnd(text, start) - start
    return start + column.coerceAtMost(length)
}

private fun copySelection(state: TextFieldState): Boolean {
    state.selectedText()?.let { Minecraft.getInstance().keyboardHandler.clipboard = it }
    return true
}

private fun cutSelection(state: TextFieldState): Boolean {
    val selected = state.selectedText() ?: return true
    Minecraft.getInstance().keyboardHandler.clipboard = selected
    return state.backspace()
}

private fun pasteClipboard(state: TextFieldState): Boolean {
    val clipboard = Minecraft.getInstance().keyboardHandler.clipboard
    return clipboard.isNotEmpty() && state.insert(clipboard)
}
