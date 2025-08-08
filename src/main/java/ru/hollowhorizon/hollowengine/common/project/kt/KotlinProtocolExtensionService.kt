package ru.hollowhorizon.hollowengine.common.project.kt

import org.eclipse.lsp4j.*
import ru.hollowhorizon.hollowengine.common.project.kt.util.AsyncExecutor
import ru.hollowhorizon.hollowengine.common.project.kt.util.parseFile
import ru.hollowhorizon.hollowengine.common.project.kt.resolve.resolveMain
import ru.hollowhorizon.hollowengine.common.project.kt.position.offset
import ru.hollowhorizon.hollowengine.common.project.kt.overridemembers.listOverridableMembers
import java.util.concurrent.CompletableFuture

class KotlinProtocolExtensionService(
    private val uriContentProvider: URIContentProvider,
    private val cp: CompilerClassPath,
    private val sp: SourcePath
) : KotlinProtocolExtensions {
    private val async = AsyncExecutor()

    override fun jarClassContents(textDocument: TextDocumentIdentifier): CompletableFuture<String?> = async.compute {
        uriContentProvider.contentOf(parseFile(textDocument.uri))
    }

    override fun buildOutputLocation(): CompletableFuture<String?> = async.compute {
        cp.outputDirectory.absolutePath
    }

    override fun mainClass(textDocument: TextDocumentIdentifier): CompletableFuture<Map<String, Any?>> = async.compute {
        val file = parseFile(textDocument.uri)
        val filePath = file.toPath()
        
        // we find the longest one in case both the root and submodule are included
        val workspacePath = cp.workspaceRoots.filter {
            filePath.startsWith(it)
        }.map {
            it.toString()
        }.maxByOrNull(String::length) ?: ""
        
        val compiledFile = sp.currentVersion(file)

        resolveMain(compiledFile) + mapOf(
            "projectRoot" to workspacePath
        )
    }

    override fun overrideMember(position: TextDocumentPositionParams): CompletableFuture<List<CodeAction>> = async.compute {
        val fileUri = parseFile(position.textDocument.uri)
        val compiledFile = sp.currentVersion(fileUri)
        val cursorOffset = offset(compiledFile.content, position.position)

        listOverridableMembers(compiledFile, cursorOffset)
    }
}
