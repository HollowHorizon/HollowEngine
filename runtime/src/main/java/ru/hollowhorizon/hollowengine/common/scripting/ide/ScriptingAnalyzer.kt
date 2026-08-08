package ru.hollowhorizon.hollowengine.common.scripting.ide

interface ScriptingAnalyzer {
    fun highlight(name: String, text: String, offset: Int): List<TextLine>

    /** Ranges to highlight for the symbol/bracket at [offset]; cheap compared to [highlight]. */
    fun occurrences(name: String, text: String, offset: Int): List<OccurrenceRange> = emptyList()

    fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(listOf(line to SpanStyle(TokenType.DEFAULT, italic = false, bold = false, highlight = false)), ArrayList())
    }

    fun completions(name: String, text: String, offset: Int): List<CompletionItem>
    fun definition(name: String, text: String, offset: Int): DefinitionLocation? = null
    fun diagnostic(name: String, text: String): List<Diagnostic>
}

data class OccurrenceRange(
    val start: Int,
    val end: Int,
)

data class DefinitionLocation(
    val path: String,
    val offset: Int = 0,
    val text: String? = null,
    val readOnly: Boolean = text != null,
)

/**
 * What an inlay draws. Kinds are added here, not in the editor: the editor renders each
 * part with its own tags and lets the stylesheet decide how it looks.
 */
sealed interface InlayContent {
    data class Label(val text: String) : InlayContent

    data class Icon(val source: String) : InlayContent
}

/**
 * What clicking an inlay does. The editor treats the action as an opaque token and hands
 * [id] back to its host, so decoding lives here and nowhere else.
 */
sealed interface InlayAction {
    val id: String

    /** Opens the file a `namespace:path` literal points at. */
    data class OpenResource(val location: String) : InlayAction {
        override val id: String get() = OpenResourcePrefix + location
    }

    companion object {
        private const val OpenResourcePrefix = "open-resource:"

        fun decode(id: String): InlayAction? = when {
            id.startsWith(OpenResourcePrefix) -> OpenResource(id.removePrefix(OpenResourcePrefix))
            else -> null
        }
    }
}

/** Style hooks the IDE puts on its hints; a stylesheet keys on them to tell hints apart. */
object InlayTags {
    const val TYPE = "type-hint"
    const val PARAMETER = "parameter-hint"
    const val ACTION = "action-hint"

    /** The action targets a file the project owns and can edit. */
    const val PROJECT_TARGET = "project-target"

    /** The action targets a game resource, which opens read-only. */
    const val RESOURCE_TARGET = "resource-target"
}

/** Icons the IDE draws inside its hints. */
object InlayIcons {
    const val OPEN_RESOURCE = "hollowengine:textures/gui/icons/link.svg"
}

/**
 * A hint drawn inline in the editor at [index]. [tags] are style hooks the stylesheet can
 * key on (`parameter-hint`, `type-hint`, …), and an [action] turns the hint into a button.
 */
data class InlayHint(
    val index: Int,
    val content: List<InlayContent>,
    val tags: List<String> = emptyList(),
    val action: InlayAction? = null,
) {
    constructor(index: Int, text: String, tags: List<String> = emptyList()) :
            this(index, listOf(InlayContent.Label(text)), tags)

    /** The hint's plain-text form; empty for hints drawn as icons only. */
    val text: String
        get() = content.filterIsInstance<InlayContent.Label>().joinToString("") { it.text }
}

data class TextLine(val spans: List<Pair<String, SpanStyle>>, val hints: ArrayList<InlayHint>)

data class SpanStyle(
    val color: TokenType,
    val italic: Boolean,
    val bold: Boolean,
    val highlight: Boolean,
)
