package ru.hollowhorizon.hollowengine.common.project.kt.codeaction

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.codeaction.quickfix.ImplementAbstractMembersQuickFix
import ru.hollowhorizon.hollowengine.common.project.kt.codeaction.quickfix.AddMissingImportsQuickFix
import ru.hollowhorizon.hollowengine.common.project.kt.command.JAVA_TO_KOTLIN_COMMAND
import ru.hollowhorizon.hollowengine.common.project.kt.util.toPath
import ru.hollowhorizon.hollowengine.common.project.kt.index.SymbolIndex

val QUICK_FIXES = listOf(
    ImplementAbstractMembersQuickFix(),
    AddMissingImportsQuickFix()
)

fun codeActions(file: CompiledFile, index: SymbolIndex, range: Range, context: CodeActionContext): List<Either<Command, CodeAction>> {
    // context.only does not work when client is emacs... 
    val requestedKinds = context.only ?: listOf(CodeActionKind.Refactor, CodeActionKind.QuickFix)
    return requestedKinds.map {
        when (it) {
            CodeActionKind.Refactor -> getRefactors(file, range)
            CodeActionKind.QuickFix -> getQuickFixes(file, index, range, context.diagnostics)
            else -> listOf()
        }
    }.flatten()
}

fun getRefactors(file: CompiledFile, range: Range): List<Either<Command, CodeAction>> {
    val hasSelection = (range.end.line - range.start.line) != 0 || (range.end.character - range.start.character) != 0
    return if (hasSelection) {
        listOf(
            Either.forLeft<Command, CodeAction>(
                Command("Convert Java to Kotlin", JAVA_TO_KOTLIN_COMMAND, listOf(
                    file.parse.toPath().toUri().toString(),
                    range
                ))
            )
        )
    } else {
        emptyList()
    }
}

fun getQuickFixes(file: CompiledFile, index: SymbolIndex, range: Range, diagnostics: List<Diagnostic>): List<Either<Command, CodeAction>> {
    return QUICK_FIXES.flatMap {
        it.compute(file, index, range, diagnostics)
    }
}
