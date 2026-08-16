package ru.hollowhorizon.hollowengine.common.ide.session.diagnostic

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.DiagnosticFix
import ru.hollowhorizon.hollowengine.common.scripting.ide.Position
import ru.hollowhorizon.hollowengine.common.scripting.ide.Range
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextEdit

/**
 * Imports the file never mentions, plus imports it mentions twice.
 */
internal fun unusedImportDiagnostics(file: KtFile): List<Diagnostic> {
    val imports = file.importDirectives.filter { !it.isAllUnder }
    if (imports.isEmpty()) return emptyList()

    val document = file.fileDocument
    val text = file.text
    val used = usedIdentifiers(file)
    val seen = HashSet<String>()
    val problems = ArrayList<ImportProblem>()

    for (directive in imports) {
        val name = directive.importedName() ?: continue
        val duplicate = !seen.add(directive.importedFqName?.asString().orEmpty() + "/" + name)
        val unused = !duplicate && name !in used && name !in ConventionNames
        if (!duplicate && !unused) continue
        problems += ImportProblem(
            directive = directive,
            message = if (duplicate) "Duplicate import '$name'" else "Unused import '$name'",
            removal = removalEdit(text, directive),
        )
    }
    if (problems.isEmpty()) return emptyList()

    val removeAll = DiagnosticFix(
        title = "hollowengine.gui.ide.editor.fix.remove_unused_imports",
        edits = problems.map { it.removal },
        titleArgs = listOf(problems.size.toString()),
    )
    return problems.map { problem ->
        Diagnostic(
            range = document.rangeOf(problem.directive.textRange.startOffset, problem.directive.textRange.endOffset),
            severity = Severity.WARNING,
            message = problem.message,
            fixes = buildList {
                add(DiagnosticFix("hollowengine.gui.ide.editor.fix.remove_import", listOf(problem.removal)))
                if (problems.size > 1) add(removeAll)
            },
        )
    }
}

private class ImportProblem(
    val directive: KtImportDirective,
    val message: String,
    /** Deletes the directive and the line break after it. */
    val removal: TextEdit,
)

private fun KtImportDirective.importedName(): String? =
    aliasName ?: importedFqName?.shortName()?.asString()?.takeIf { it.isNotEmpty() }

/** The directive together with its trailing newline, so removing it does not leave a blank line. */
private fun removalEdit(text: String, directive: KtImportDirective): TextEdit {
    val start = directive.textRange.startOffset
    var end = directive.textRange.endOffset
    while (end < text.length && (text[end] == ' ' || text[end] == '\t')) end++
    if (end < text.length && text[end] == '\r') end++
    if (end < text.length && text[end] == '\n') end++
    return TextEdit(start, end, "")
}

/** Every identifier outside the import list, as written. */
private fun usedIdentifiers(file: KtFile): Set<String> {
    val importList = file.importList
    val names = HashSet<String>()
    var leaf: PsiElement? = file.findElementAt(0)
    while (leaf != null) {
        val type = leaf.node?.elementType
        if (type == KtTokens.IDENTIFIER && (importList == null || !importList.textRange.contains(leaf.textRange))) {
            names += leaf.text
        }
        leaf = PsiTreeUtil.nextLeaf(leaf)
    }
    return names
}

/** Callable names Kotlin invokes through syntax instead of by name. */
private val ConventionNames = buildSet {
    addAll(
        listOf(
            "invoke", "get", "set", "contains", "iterator", "next", "hasNext", "compareTo", "equals",
            "rangeTo", "rangeUntil", "getValue", "setValue", "provideDelegate", "unaryPlus", "unaryMinus",
            "not", "inc", "dec", "plus", "minus", "times", "div", "rem", "mod",
            "plusAssign", "minusAssign", "timesAssign", "divAssign", "remAssign", "modAssign",
        ),
    )
    for (index in 1..16) add("component$index")
}

private fun Document.rangeOf(startOffset: Int, endOffset: Int): Range {
    val start = startOffset.coerceIn(0, textLength)
    val end = endOffset.coerceIn(start, textLength)
    val startLine = getLineNumber(start)
    val endLine = getLineNumber(end)
    return Range(
        Position(startLine, start - getLineStartOffset(startLine)),
        Position(endLine, end - getLineStartOffset(endLine)),
    )
}
