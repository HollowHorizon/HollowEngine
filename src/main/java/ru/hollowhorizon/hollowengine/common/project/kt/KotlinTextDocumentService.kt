package ru.hollowhorizon.hollowengine.common.project.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.TextDocumentService
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.project.kt.codeaction.codeActions
import ru.hollowhorizon.hollowengine.common.project.kt.completion.completions
import ru.hollowhorizon.hollowengine.common.project.kt.definition.goToDefinition
import ru.hollowhorizon.hollowengine.common.project.kt.diagnostic.convertDiagnostic
import ru.hollowhorizon.hollowengine.common.project.kt.formatting.FormattingService
import ru.hollowhorizon.hollowengine.common.project.kt.highlight.documentHighlightsAt
import ru.hollowhorizon.hollowengine.common.project.kt.hover.hoverAt
import ru.hollowhorizon.hollowengine.common.project.kt.inlayhints.provideHints
import ru.hollowhorizon.hollowengine.common.project.kt.position.extractRange
import ru.hollowhorizon.hollowengine.common.project.kt.position.offset
import ru.hollowhorizon.hollowengine.common.project.kt.position.position
import ru.hollowhorizon.hollowengine.common.project.kt.references.findReferences
import ru.hollowhorizon.hollowengine.common.project.kt.rename.renameSymbol
import ru.hollowhorizon.hollowengine.common.project.kt.semantictokens.encodedSemanticTokens
import ru.hollowhorizon.hollowengine.common.project.kt.signaturehelp.fetchSignatureHelpAt
import ru.hollowhorizon.hollowengine.common.project.kt.symbols.documentSymbols
import ru.hollowhorizon.hollowengine.common.project.kt.util.*
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture

class KotlinTextDocumentService(
    private val sf: SourceFiles,
    private val sp: SourcePath,
    val config: Configuration,
    private val tempDirectory: TemporaryDirectory,
    private val uriContentProvider: URIContentProvider,
    private val cp: CompilerClassPath,
) : TextDocumentService, Closeable {
    private lateinit var client: LanguageClient
    val async = AsyncExecutor()
    private val formattingService = FormattingService(config.formatting)

    private var debounceLint = Debouncer(Duration.ofMillis(config.diagnostics.debounceTime))
    var debounceHighlight = Debouncer(Duration.ofMillis(config.diagnostics.debounceTime))
    val lintTodo = mutableSetOf<File>()
    var lintCount = 0

    var lintRecompilationCallback: () -> Unit
        get() = sp.beforeCompileCallback
        set(callback) {
            sp.beforeCompileCallback = callback
        }

    private val TextDocumentItem.filePath: Path?
        get() = parseFile(uri).toPath()

    private val TextDocumentIdentifier.filePath: Path?
        get() = parseFile(uri).toPath()

    private val TextDocumentIdentifier.isKotlinScript: Boolean
        get() = uri.endsWith(".kts")

    private val TextDocumentIdentifier.content: String
        get() = sp.content(parseFile(uri))

    fun connect(client: LanguageClient) {
        this.client = client
    }

    private enum class Recompile {
        ALWAYS, AFTER_DOT, NEVER
    }

    private fun recover(position: TextDocumentPositionParams, recompile: Recompile): Pair<CompiledFile, Int>? {
        return recover(position.textDocument.uri, position.position, recompile)
    }

    private fun recover(fileString: String, position: Position, recompile: Recompile): Pair<CompiledFile, Int>? {
        val file = parseFile(fileString)
        val content = sp.content(file)
        val offset = offset(content, position.line, position.character)
        val shouldRecompile = when (recompile) {
            Recompile.ALWAYS -> true
            Recompile.AFTER_DOT -> offset > 0 && content[offset - 1] == '.'
            Recompile.NEVER -> false
        }
        val compiled = if (shouldRecompile) sp.currentVersion(file) else sp.latestCompiledVersion(file)
        return Pair(compiled, offset)
    }

    override fun codeAction(params: CodeActionParams): CompletableFuture<List<Either<Command, CodeAction>>> =
        async.compute {
            val (file, _) = recover(params.textDocument.uri, params.range.start, Recompile.NEVER)
                ?: return@compute emptyList()
            codeActions(file, sp.index, params.range, params.context)
        }

    override fun inlayHint(params: InlayHintParams): CompletableFuture<List<InlayHint>> = async.compute {
        val (file, _) = recover(params.textDocument.uri, params.range.start, Recompile.ALWAYS)
            ?: return@compute emptyList()
        provideHints(file, config.inlayHints)
    }

    //TODO: hoverAt - информация об элементе на котором курсор
    override fun hover(position: HoverParams): CompletableFuture<Hover?> = async.compute {
        val (file, cursor) = recover(position, Recompile.NEVER) ?: return@compute null
        hoverAt(file, cursor) ?: noResult("No hover found at ${describePosition(position)}", null)
    }

    // TODO: Это всякие выделения одинаковых переменных, скобок и т.п.
    override fun documentHighlight(position: DocumentHighlightParams): CompletableFuture<List<DocumentHighlight>> =
        async.compute {
            val (file, cursor) = recover(position.textDocument.uri, position.position, Recompile.NEVER)
                ?: return@compute emptyList()
            documentHighlightsAt(file, cursor)
        }

    override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<List<TextEdit>> {
        TODO("not implemented")
    }

    override fun definition(position: DefinitionParams): CompletableFuture<Either<List<Location>, List<LocationLink>>> =
        async.compute {
            TODO()
        }

    override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<List<TextEdit>> =
        async.compute {
            val code = extractRange(params.textDocument.content, params.range)
            listOf(
                TextEdit(
                    params.range,
                    formattingService.formatKotlinCode(code, params.options)
                )
            )
        }

    override fun codeLens(params: CodeLensParams): CompletableFuture<List<CodeLens>> {
        TODO("not implemented")
    }

    override fun rename(params: RenameParams) = async.compute {
        val (file, cursor) = recover(params, Recompile.NEVER) ?: return@compute null
        renameSymbol(file, cursor, sp, params.newName)
    }

    override fun completion(position: CompletionParams): CompletableFuture<Either<List<CompletionItem>, CompletionList>> =
        async.compute {
            val (file, cursor) = recover(position, Recompile.NEVER)
                ?: return@compute Either.forRight(CompletionList()) // TODO: Investigate when to recompile
            val completions = completions(file, cursor, sp.index, config.completion)

            TODO()
        }

    override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
        TODO("not implemented")
    }

    @Suppress("DEPRECATION")
    override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> =
        async.compute {
            val uri = parseFile(params.textDocument.uri)
            val parsed = sp.parsedFile(uri)

            documentSymbols(parsed)
        }

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = parseFile(params.textDocument.uri)
        sf.open(uri, params.textDocument.text, params.textDocument.version)
        lintNow(uri)
    }

    override fun didSave(params: DidSaveTextDocumentParams) {
        // Lint after saving to prevent inconsistent diagnostics
        val uri = parseFile(params.textDocument.uri)
        lintNow(uri)
        debounceLint.schedule {
            sp.save(uri)
        }
    }

    override fun signatureHelp(position: SignatureHelpParams): CompletableFuture<SignatureHelp?> = async.compute {
        val (file, cursor) = recover(position, Recompile.NEVER) ?: return@compute null
        fetchSignatureHelpAt(file, cursor) ?: noResult(
            "No function call around ${describePosition(position)}",
            null
        )
    }

    override fun didClose(params: DidCloseTextDocumentParams) {
        val uri = parseFile(params.textDocument.uri)
        sf.close(uri)
        clearDiagnostics(uri)
    }

    override fun formatting(params: DocumentFormattingParams): CompletableFuture<List<TextEdit>> = async.compute {
        val code = params.textDocument.content
        listOf(
            TextEdit(
                Range(Position(0, 0), position(code, code.length)),
                formattingService.formatKotlinCode(code, params.options)
            )
        )
    }

    fun format(code: String) = formattingService.formatKotlinCode(code)

    override fun didChange(params: DidChangeTextDocumentParams) {
        val uri = parseFile(params.textDocument.uri)
        sf.edit(uri, params.textDocument.version, params.contentChanges)
        lintLater(uri)
    }

    override fun references(position: ReferenceParams) = async.compute {
        position.textDocument.filePath
            ?.let { file ->
                val content = sp.content(parseFile(position.textDocument.uri))
                val offset = offset(content, position.position.line, position.position.character)
                findReferences(file, offset, sp)
            }
    }

    override fun semanticTokensFull(params: SemanticTokensParams) = async.compute {
        val uri = parseFile(params.textDocument.uri)
        val file = sp.currentVersion(uri)

        val tokens = encodedSemanticTokens(file)

        SemanticTokens(tokens)
    }

    override fun semanticTokensRange(params: SemanticTokensRangeParams) = async.compute {
        val uri = parseFile(params.textDocument.uri)
        val file = sp.currentVersion(uri)

        val tokens = encodedSemanticTokens(file, params.range)

        SemanticTokens(tokens)

    }

    override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
        TODO("not implemented")
    }

    private fun describePosition(position: TextDocumentPositionParams): String {
        return "${position.textDocument.uri} ${position.position.line + 1}:${position.position.character + 1}"
    }

    public fun updateDebouncer() {
        debounceLint = Debouncer(Duration.ofMillis(config.diagnostics.debounceTime))
    }

    fun lintAll() {
        debounceLint.submitImmediately {
            sp.compileAllFiles()
            sp.saveAllFiles()
            sp.refreshDependencyIndexes()
        }
    }

    private fun clearLint(): List<File> {
        val result = lintTodo.toList()
        lintTodo.clear()
        return result
    }

    private fun lintLater(uri: File) {
        lintTodo.add(uri)
        debounceLint.schedule(::doLint)
    }

    private fun lintNow(file: File) {
        lintTodo.add(file)
        debounceLint.submitImmediately(::doLint)
    }

    private fun doLint(cancelCallback: () -> Boolean) {
        val files = clearLint()
        val context = sp.compileFiles(files)
        if (!cancelCallback.invoke()) {
            reportDiagnostics(files, context.diagnostics)
        }
        lintCount++
    }

    private fun reportDiagnostics(compiled: Collection<File>, kotlinDiagnostics: Diagnostics) {
        val langServerDiagnostics = kotlinDiagnostics
            .flatMap(::convertDiagnostic)
            .filter { config.diagnostics.enabled && it.second.severity <= config.diagnostics.level }
        val byFile = langServerDiagnostics.groupBy({ it.first }, { it.second })

        for ((uri, diagnostics) in byFile) {
            if (sf.isOpen(uri)) {
                client.publishDiagnostics(PublishDiagnosticsParams(uri.toString(), diagnostics))

            }
        }

        val noErrors = compiled - byFile.keys
        for (file in noErrors) {
            clearDiagnostics(file)
        }

        lintCount++
    }

    private fun clearDiagnostics(uri: File) {
        client.publishDiagnostics(PublishDiagnosticsParams(uri.toString(), listOf()))
    }

    private fun shutdownExecutors(awaitTermination: Boolean) {
        async.shutdown(awaitTermination)
        debounceLint.shutdown(awaitTermination)
    }

    override fun close() {
        shutdownExecutors(awaitTermination = true)
    }
}
