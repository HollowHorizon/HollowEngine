package ru.hollowhorizon.hollowengine.client.ui.ide

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonExtension
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionSink
import ru.hollowhorizon.hollowengine.common.scripting.ide.DefinitionLocation
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.HoverInfo
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayHint
import ru.hollowhorizon.hollowengine.common.scripting.ide.OccurrenceRange
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.SignatureHelp
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType

internal class HollowIdeAnalyzerProvider {
    private var base: ScriptingAnalyzer? = null
    private var revision = Long.MIN_VALUE
    private var composite: ScriptingAnalyzer? = null

    @Synchronized
    fun current(base: ScriptingAnalyzer): ScriptingAnalyzer {
        val point = HollowIdeExtensionPoints.CODE_INSIGHT
        val currentRevision = point.revision
        val cached = composite
        if (this.base === base && revision == currentRevision && cached != null) return cached
        this.base = base
        revision = currentRevision
        return ContributingScriptingAnalyzer(base, point.extensions()).also { composite = it }
    }
}

private class ContributingScriptingAnalyzer(
    private val base: ScriptingAnalyzer,
    private val contributors: List<HollowAddonExtension<HollowIdeCodeInsightContributor>>,
) : ScriptingAnalyzer {
    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        val lines = base.highlight(name, text, offset).map { line ->
            TextLine(line.spans, ArrayList(line.hints))
        }.toMutableList()
        val sourceLines = text.split('\n')
        while (lines.size < sourceLines.size) {
            lines += TextLine(
                spans = listOf(sourceLines[lines.size] to SpanStyle(TokenType.DEFAULT, false, false, false)),
                hints = ArrayList(),
            )
        }
        matching(name).flatMap { extension ->
            extension.call("inlays") { contributor -> contributor.inlays(name, text) }.orEmpty()
        }.forEach { positioned ->
            val safeOffset = positioned.offset.coerceIn(0, text.length)
            val lineIndex = text.take(safeOffset).count { it == '\n' }
            val lineStart = text.lastIndexOf('\n', (safeOffset - 1).coerceAtLeast(0))
                .let { index -> if (safeOffset == 0 || index < 0) 0 else index + 1 }
            lines.getOrNull(lineIndex)?.hints?.add(positioned.hint.copy(index = safeOffset - lineStart))
        }
        return lines
    }

    override fun occurrences(name: String, text: String, offset: Int): List<OccurrenceRange> = buildList {
        addAll(base.occurrences(name, text, offset))
        matching(name).forEach { extension ->
            addAll(extension.call("occurrences") { it.occurrences(name, text, offset) }.orEmpty())
        }
    }.distinct()

    override fun lightweightHighlightLine(name: String, line: String): TextLine =
        base.lightweightHighlightLine(name, line)

    override fun completions(name: String, text: String, offset: Int, sink: CompletionSink) {
        var accepting = true
        val guardedSink = CompletionSink { items ->
            if (!accepting) return@CompletionSink false
            accepting = sink.emit(items)
            accepting
        }
        base.completions(name, text, offset, guardedSink)
        if (!accepting) return
        for (extension in matching(name)) {
            val items = extension.call("completions") { it.completions(name, text, offset) }.orEmpty()
            if (items.isNotEmpty() && !guardedSink.emit(items)) return
        }
    }

    override val canFormat: Boolean
        get() = base.canFormat

    override fun format(name: String, text: String): String? = base.format(name, text)

    override fun definition(name: String, text: String, offset: Int): DefinitionLocation? =
        matching(name).firstNotNullOfOrNull { extension ->
            extension.call("definition") { it.definition(name, text, offset) }
        } ?: base.definition(name, text, offset)

    override fun signatureHelp(name: String, text: String, offset: Int): SignatureHelp? =
        matching(name).firstNotNullOfOrNull { extension ->
            extension.call("signature-help") { it.signatureHelp(name, text, offset) }
        } ?: base.signatureHelp(name, text, offset)

    override fun hover(name: String, text: String, offset: Int): HoverInfo? =
        matching(name).firstNotNullOfOrNull { extension ->
            extension.call("hover") { it.hover(name, text, offset) }
        } ?: base.hover(name, text, offset)

    override fun diagnostic(name: String, text: String): List<Diagnostic> = buildList {
        addAll(base.diagnostic(name, text))
        matching(name).forEach { extension ->
            addAll(extension.call("diagnostics") { it.diagnostics(name, text) }.orEmpty())
        }
    }

    private fun matching(path: String): List<HollowAddonExtension<HollowIdeCodeInsightContributor>> =
        contributors.filter { extension ->
            extension.call("supports") { it.supports(path) } == true
        }
}

private fun <R> HollowAddonExtension<HollowIdeCodeInsightContributor>.call(
    stage: String,
    block: (HollowIdeCodeInsightContributor) -> R,
): R? = runCatching { invoke(block) }
    .onFailure { failure ->
        HollowEngine.LOGGER.error(
            "IDE code insight extension '{}' failed during {}",
            qualifiedId,
            stage,
            failure,
        )
    }
    .getOrNull()
