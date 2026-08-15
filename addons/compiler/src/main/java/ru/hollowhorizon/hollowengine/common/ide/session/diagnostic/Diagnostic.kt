package ru.hollowhorizon.hollowengine.common.ide.session.diagnostic

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
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
    val analysisDiagnostics = analyze(file) {
        file.collectDiagnostics(KaDiagnosticCheckerFilter.EXTENDED_AND_COMMON_CHECKERS)
            .map { diagnostic ->
                val severity = when(diagnostic.severity) {
                    KaSeverity.INFO -> Severity.INFO
                    KaSeverity.ERROR -> Severity.ERROR
                    KaSeverity.WARNING -> Severity.WARNING
                }
                val range = diagnostic.textRanges.firstOrNull() ?: diagnostic.psi.textRange
                Diagnostic(document.rangeOf(range), severity, diagnostic.defaultMessage)
            }
    }
    val syntaxDiagnostics = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java).map { error ->
        Diagnostic(document.rangeOf(error.textRange), Severity.ERROR, error.errorDescription)
    }
    return (analysisDiagnostics + syntaxDiagnostics).distinctBy { diagnostic ->
        Triple(diagnostic.range, diagnostic.severity, diagnostic.message)
    }
}

private fun Document.rangeOf(textRange: TextRange): Range {
    val startOffset = textRange.startOffset.coerceIn(0, textLength)
    val endOffset = textRange.endOffset.coerceIn(startOffset, textLength)
    val startLine = getLineNumber(startOffset)
    val endLine = getLineNumber(endOffset)
    return Range(
        Position(startLine, startOffset - getLineStartOffset(startLine)),
        Position(endLine, endOffset - getLineStartOffset(endLine)),
    )
}
