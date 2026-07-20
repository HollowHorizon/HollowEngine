package ru.hollowhorizon.hollowengine.common.ide.session.diagnostic

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.diagnostics.KaSeverity
import org.jetbrains.kotlin.psi.KtFile
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Position
import ru.hollowhorizon.hollowengine.common.scripting.ide.Range
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity

fun diagnosticCode(file: KtFile): List<Diagnostic> {
    val document = file.fileDocument

    analyze(file) {
        return file.collectDiagnostics(KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
            .map {
                val severity = when(it.severity) {
                    KaSeverity.INFO -> Severity.INFO
                    KaSeverity.ERROR -> Severity.ERROR
                    KaSeverity.WARNING -> Severity.WARNING
                }
                val range = it.textRanges.firstOrNull() ?: it.psi.textRange
                val startLine = document.getLineNumber(range.startOffset)
                val startColumn = range.startOffset - document.getLineStartOffset(startLine)
                val endLine = document.getLineNumber(range.endOffset)
                val endColumn = range.endOffset - document.getLineStartOffset(endLine)

                Diagnostic(Range(Position(startLine, startColumn), Position(endLine, endColumn)), severity, it.defaultMessage)
            }
    }
}