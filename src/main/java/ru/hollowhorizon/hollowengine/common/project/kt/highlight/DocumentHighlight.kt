package ru.hollowhorizon.hollowengine.common.project.kt.highlight

import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightKind
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.references.findReferencesToDeclarationInFile

fun documentHighlightsAt(file: CompiledFile, cursor: Int): List<DocumentHighlight> {
    val (declaration, declarationLocation) = file.findDeclaration(cursor)
        ?: return emptyList()
    val references = findReferencesToDeclarationInFile(declaration, file)

    return if (declaration.isInFile(file.parse)) {
        listOf(DocumentHighlight(declarationLocation.range, DocumentHighlightKind.Text))
    } else {
        emptyList()
    } + references.map { DocumentHighlight(it, DocumentHighlightKind.Text) }
}

private fun KtNamedDeclaration.isInFile(file: KtFile) = this.containingFile == file
