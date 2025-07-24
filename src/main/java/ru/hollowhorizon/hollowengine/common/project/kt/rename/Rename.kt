package ru.hollowhorizon.hollowengine.common.project.kt.rename

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.SourcePath
import ru.hollowhorizon.hollowengine.common.project.kt.references.findReferences

fun renameSymbol(file: CompiledFile, cursor: Int, sp: SourcePath, newName: String): WorkspaceEdit? {
    val (declaration, location) = file.findDeclaration(cursor) ?: return null
    return declaration.let {
        val declarationEdit = Either.forLeft<TextDocumentEdit, ResourceOperation>(TextDocumentEdit(
            VersionedTextDocumentIdentifier().apply { uri = location.uri },
            listOf(TextEdit(location.range, newName))
        ))

        val referenceEdits = findReferences(declaration, sp).map {
            Either.forLeft<TextDocumentEdit, ResourceOperation>(TextDocumentEdit(
                VersionedTextDocumentIdentifier().apply { uri = it.uri },
                listOf(TextEdit(it.range, newName))
            ))
        }

        WorkspaceEdit(listOf(declarationEdit) + referenceEdits)
    }
}
