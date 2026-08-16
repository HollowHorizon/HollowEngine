package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayouter

/**
 * The Alt+Enter menu: the fixes offered by the diagnostics under the caret. Kept apart from the
 * completion popup because it is driven by the diagnostics list rather than by typing, and because
 * accepting one rewrites arbitrary parts of the document instead of the word being typed.
 */
internal class EditableFieldQuickFixState {
    var fixes: List<UiTextQuickFix> by mutableStateOf(emptyList())
        private set
    var anchor: Int by mutableStateOf(0)
        private set
    var selectedIndex: Int by mutableStateOf(0)
        private set

    val active: Boolean get() = fixes.isNotEmpty()

    fun open(anchor: Int, fixes: List<UiTextQuickFix>) {
        if (fixes.isEmpty()) return
        this.anchor = anchor
        this.fixes = fixes
        selectedIndex = 0
    }

    fun close() {
        if (fixes.isEmpty()) return
        fixes = emptyList()
        selectedIndex = 0
    }

    fun moveSelection(delta: Int) {
        if (fixes.isEmpty()) return
        val size = fixes.size
        selectedIndex = ((selectedIndex + delta) % size + size) % size
    }

    fun selected(): UiTextQuickFix? = fixes.getOrNull(selectedIndex)
}

/** The fixes offered at [caret], in diagnostic order and without duplicate titles. */
internal fun quickFixesAt(diagnostics: List<UiTextDiagnostic>, caret: Int): List<UiTextQuickFix> {
    if (diagnostics.isEmpty()) return emptyList()
    return diagnostics
        .filter { it.fixes.isNotEmpty() && caret >= it.start && caret <= it.end }
        .flatMap { it.fixes }
        .distinctBy { it.title }
}

/**
 * Alt+Enter and the popup's own navigation keys. Returns false when there is nothing to fix, so
 * Alt+Enter keeps falling through to the completion popup.
 */
internal fun handleEditableFieldQuickFixKey(
    state: TextFieldState,
    input: UiKeyInput,
    quickFix: EditableFieldQuickFixState,
    diagnostics: List<UiTextDiagnostic>,
): Boolean {
    if (quickFix.active) {
        when (input.key) {
            GLFW.GLFW_KEY_UP -> {
                quickFix.moveSelection(-1)
                return true
            }

            GLFW.GLFW_KEY_DOWN -> {
                quickFix.moveSelection(1)
                return true
            }

            GLFW.GLFW_KEY_ESCAPE -> {
                quickFix.close()
                return true
            }

            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_TAB -> {
                val fix = quickFix.selected()
                quickFix.close()
                if (fix != null) applyEditableFieldQuickFix(state, fix)
                return true
            }

            else -> {
                quickFix.close()
                return false
            }
        }
    }
    if (state.readOnly) return false
    if (!input.alt || input.key != GLFW.GLFW_KEY_ENTER && input.key != GLFW.GLFW_KEY_KP_ENTER) return false
    val fixes = quickFixesAt(diagnostics, state.caret)
    if (fixes.isEmpty()) return false
    quickFix.open(state.caret, fixes)
    return true
}

/** Applies every edit of [fix] as one undoable step, leaving the caret where the first edit ended. */
internal fun applyEditableFieldQuickFix(state: TextFieldState, fix: UiTextQuickFix): Boolean {
    val text = state.text
    val edits = fix.edits
        .map { UiTextEdit(it.start.coerceIn(0, text.length), it.end.coerceIn(0, text.length), it.replacement) }
        .filter { it.end >= it.start }
        .sortedWith(compareBy({ it.start }, { it.end }))
    if (edits.isEmpty()) return false

    var caret = state.caret
    val next = buildString {
        var cursor = 0
        var shift = 0
        for (edit in edits) {
            if (edit.start < cursor) continue
            append(text, cursor, edit.start)
            append(edit.replacement)
            val delta = edit.replacement.length - (edit.end - edit.start)
            // The caret rides the edits: past one it shifts, inside one it snaps to the new end.
            if (caret >= edit.end) shift += delta
            else if (caret > edit.start) caret = edit.start + edit.replacement.length - shift
            cursor = edit.end
        }
        append(text, cursor, text.length)
        caret = (caret + shift).coerceIn(0, length)
    }
    return state.applyEdit(next, listOf(UiTextCaret(caret)))
}

@Composable
internal fun EditableFieldQuickFixPopup(
    quickFix: EditableFieldQuickFixState,
    layout: EditableFieldLayout,
    scrollState: UiScrollHandle,
    contentOffsetX: Float = 0f,
    onApply: (UiTextQuickFix) -> Unit,
) {
    val fixes = quickFix.fixes
    if (fixes.isEmpty()) return
    val viewport = scrollState.viewport
    if (viewport.width <= 0f || viewport.height <= 0f) return

    val fontSize = layout.fontSize
    val fontFamily = layout.fontFamily
    val caret = layout.caretAt(quickFix.anchor)
    val width = (fixes.maxOf { UiTextLayouter.measureTextWidth(it.title, fontSize, fontFamily) } + QuickFixRowChrome)
        .coerceIn(
            minOf(QuickFixMinWidth, viewport.width),
            (viewport.width - QuickFixViewportMargin * 2f).coerceAtLeast(1f),
        )
    val x = (contentOffsetX + caret.x - scrollState.offsetX).coerceIn(
        QuickFixViewportMargin,
        (viewport.width - width - QuickFixViewportMargin).coerceAtLeast(QuickFixViewportMargin),
    )
    val y = (caret.y - scrollState.offsetY + fontSize + QuickFixAnchorGap)
        .coerceAtMost((viewport.height - QuickFixViewportMargin).coerceAtLeast(0f))

    Popup(
        anchorBounds = UiRect(viewport.x + x, viewport.y + y, 0f, 0f),
        alignment = UiPopupAlignment(anchorVertical = UiAlign.START),
        id = "editable-text-field-quick-fix",
        tags = listOf("editable-text-field-quick-fix-popup", "ide-quick-fix-popup"),
        modifier = Modifier.size(width.px, UiLength.Fit).fontSize(fontSize)
            .let { base -> fontFamily?.let { base.fontFamily(it) } ?: base }
            .input(clickable = true, hoverable = true),
        dismissOnOutside = true,
        onDismiss = quickFix::close,
    ) {
        fixes.forEachIndexed { index, fix ->
            key(index) {
                Row(
                    tags = listOf("ide-quick-fix-row", if (index == quickFix.selectedIndex) "selected" else "idle"),
                    modifier = Modifier.input(clickable = true, hoverable = true)
                        .cursor(UiCursorShape.HAND)
                        .alignItems(vertical = UiAlign.CENTER)
                        .onClick { event ->
                            onApply(fix)
                            event.consume()
                        },
                ) {
                    Text(fix.title, tags = listOf("ide-quick-fix-label"))
                }
            }
        }
    }
}

private const val QuickFixMinWidth = 170f
private const val QuickFixRowChrome = 26f
private const val QuickFixAnchorGap = 4f
private const val QuickFixViewportMargin = 4f
