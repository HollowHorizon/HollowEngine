package ru.hollowhorizon.hollowengine.client.ui.ide.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeOpenFile

private const val ModeLang = "hollowengine.gui.ide.preview.mode"
private const val ViewIcons = "hollowengine:textures/gui/icons/view"

/** How a file that has a preview is shown in its tab. */
enum class HollowIdeViewMode(val icon: String, val tooltip: String) {
    TEXT("$ViewIcons/text.svg", "$ModeLang.text"),
    SPLIT("$ViewIcons/split.svg", "$ModeLang.split"),
    PREVIEW("$ViewIcons/preview.svg", "$ModeLang.preview"),
}

/**
 * A visual view of a text file, shown beside or instead of its text the way IDEA shows Markdown.
 */
class HollowIdeFilePreview(
    val id: String,
    val defaultMode: HollowIdeViewMode = HollowIdeViewMode.SPLIT,
    private val matcher: (path: String) -> Boolean,
    val content: @Composable (HollowIdePreviewContext) -> Unit,
) {
    init {
        require(id.isNotBlank()) { "Preview ID cannot be blank" }
    }

    /** [path] is a project path or a `resource://` one from the asset manager. */
    fun matches(path: String): Boolean = matcher(path)
}

/** Previews by file path. Kept apart from file types so read-only assets get the same preview. */
class HollowIdePreviewRegistry {
    private val previews = mutableListOf<HollowIdeFilePreview>()

    @Synchronized
    fun register(preview: HollowIdeFilePreview) {
        require(previews.none { it.id == preview.id }) { "Preview '${preview.id}' is already registered" }
        previews += preview
    }

    @Synchronized
    fun find(path: String): HollowIdeFilePreview? = previews.firstOrNull { it.matches(path) }
}

/** What one open file remembers about its preview while it stays open. */
class HollowIdeFileView internal constructor(val preview: HollowIdeFilePreview) {
    var mode by mutableStateOf(preview.defaultMode)

    /** The text's share of the width in [HollowIdeViewMode.SPLIT]. */
    var split by mutableStateOf(0.5f)
        internal set

    private var state: Any? = null

    /** State the preview keeps across tab switches, made on first use. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> state(create: () -> T): T = (state as? T) ?: create().also { state = it }
}

class HollowIdePreviewContext internal constructor(
    val file: HollowIdeOpenFile,
    val view: HollowIdeFileView,
    private val write: (String) -> Unit,
) {
    val text: String get() = file.text
    val readOnly: Boolean get() = file.readOnly

    /** Replaces the whole text; the editor diffs it into one undoable step. */
    fun edit(next: String) {
        if (!readOnly && next != text) write(next)
    }

    fun <T : Any> state(create: () -> T): T = view.state(create)
}

/**
 * A model parsed from the text, kept for as long as the text is the one it came from.
 *
 * Objects the preview hands to its fields (the selection, a name being typed) therefore survive the
 * preview's own edits: after [wrote] the text matches again and nothing is parsed. Only a change made
 * on the text side builds a new model.
 */
class HollowIdeTextModel<T : Any>(private val parse: (String) -> T) {
    private var source: String? = null
    private var parsed: Result<T>? = null

    /** The model for [text], or the reason it does not read; a broken file is never edited over. */
    fun read(text: String): Result<T> {
        parsed?.takeIf { text == source }?.let { return it }
        return runCatching { parse(text) }.also {
            parsed = it
            source = text
        }
    }

    /** Records that [text] is what the current model already says. */
    fun wrote(text: String) {
        if (parsed?.isSuccess == true) source = text
    }

    /** Drops the model, so the next [read] parses the text again. */
    fun invalidate() {
        parsed = null
        source = null
    }
}
