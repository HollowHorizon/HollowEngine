package ru.hollowhorizon.hollowengine.common.project.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.NotebookDocumentService
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.diagnostics.rendering.DiagnosticFactoryToRendererMap
import ru.hollowhorizon.hc.common.utils.UnsafeTools
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.scripting.IdeContent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.project.kt.command.ALL_COMMANDS
import ru.hollowhorizon.hollowengine.common.project.kt.progress.LanguageClientProgress
import ru.hollowhorizon.hollowengine.common.project.kt.progress.Progress
import ru.hollowhorizon.hollowengine.common.project.kt.semantictokens.semanticTokensLegend
import ru.hollowhorizon.hollowengine.common.project.kt.util.AsyncExecutor
import ru.hollowhorizon.hollowengine.common.project.kt.util.TemporaryDirectory
import ru.hollowhorizon.hollowengine.common.project.kt.externalsources.*
import ru.hollowhorizon.hollowengine.common.project.kt.imports.UNUSED_IMPORT_FACTORY
import java.io.Closeable
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

object KotlinLanguageServer : LanguageServer, LanguageClientAware, Closeable {
    val config: Configuration = Configuration()
    val classPath = CompilerClassPath(config.compiler, config.scripts, config.codegen)

    private val tempDirectory = TemporaryDirectory()
    private val uriContentProvider = URIContentProvider(ClassContentProvider(config.externalSources, classPath, tempDirectory, CompositeSourceArchiveProvider(JdkSourceArchiveProvider(classPath), ClassPathSourceArchiveProvider(classPath))))
    val sourcePath = SourcePath(classPath, uriContentProvider, config.indexing)
    val sourceFiles = SourceFiles(sourcePath, uriContentProvider, config.scripts)

    private val textDocuments = KotlinTextDocumentService(sourceFiles, sourcePath, config, tempDirectory, uriContentProvider, classPath)
    private val workspaces = KotlinWorkspaceService(sourceFiles, sourcePath, classPath, textDocuments, config)
    private val protocolExtensions = KotlinProtocolExtensionService(uriContentProvider, classPath, sourcePath)

    private lateinit var client: LanguageClient

    private val async = AsyncExecutor()
    private var progressFactory: Progress.Factory = Progress.Factory.None
        set(factory: Progress.Factory) {
            field = factory
            sourcePath.progressFactory = factory
        }

    override fun connect(client: LanguageClient) {
        this.client = client

        workspaces.connect(client)
        textDocuments.connect(client)

        HollowEngine.LOGGER.info("Connected to client")
    }

    override fun getTextDocumentService(): KotlinTextDocumentService = textDocuments

    override fun getWorkspaceService(): KotlinWorkspaceService = workspaces

    @JsonDelegate
    fun getProtocolExtensionService(): KotlinProtocolExtensions = protocolExtensions

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> = async.compute {
        val serverCapabilities = ServerCapabilities()
        serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental)
        serverCapabilities.workspace = WorkspaceServerCapabilities()
        serverCapabilities.workspace.workspaceFolders = WorkspaceFoldersOptions()
        serverCapabilities.workspace.workspaceFolders.supported = true
        serverCapabilities.workspace.workspaceFolders.changeNotifications = Either.forRight(true)
        serverCapabilities.inlayHintProvider = Either.forLeft(true)
        serverCapabilities.hoverProvider = Either.forLeft(true)
        serverCapabilities.renameProvider = Either.forLeft(true)
        serverCapabilities.completionProvider = CompletionOptions(false, listOf("."))
        serverCapabilities.signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
        serverCapabilities.definitionProvider = Either.forLeft(true)
        serverCapabilities.documentSymbolProvider = Either.forLeft(true)
        serverCapabilities.workspaceSymbolProvider = Either.forLeft(true)
        serverCapabilities.referencesProvider = Either.forLeft(true)
        serverCapabilities.semanticTokensProvider = SemanticTokensWithRegistrationOptions(semanticTokensLegend, true, true)
        serverCapabilities.codeActionProvider = Either.forLeft(true)
        serverCapabilities.documentFormattingProvider = Either.forLeft(true)
        serverCapabilities.documentRangeFormattingProvider = Either.forLeft(true)
        serverCapabilities.executeCommandProvider = ExecuteCommandOptions(ALL_COMMANDS)
        serverCapabilities.documentHighlightProvider = Either.forLeft(true)

        val storagePath = getStoragePath(params)

        val clientCapabilities = params.capabilities
        config.completion.snippets.enabled = clientCapabilities?.textDocument?.completion?.completionItem?.snippetSupport ?: false

        if (clientCapabilities?.window?.workDoneProgress ?: false) {
            progressFactory = LanguageClientProgress.Factory(client)
        }

        if (clientCapabilities?.textDocument?.rename?.prepareSupport ?: false) {
            serverCapabilities.renameProvider = Either.forRight(RenameOptions(false))
        }

        @Suppress("DEPRECATION")
        val folders = params.workspaceFolders?.takeIf { it.isNotEmpty() }
            ?: params.rootUri?.let { WorkspaceFolder(it, "Workspace") } ?.let(::listOf)
            ?: params.rootPath?.let(Paths::get)?.toUri()?.toString()?.let { WorkspaceFolder(it, "Workspace") }?.let(::listOf)
            ?: listOf()

        val progress = params.workDoneToken?.let { LanguageClientProgress("Workspace folders", it, client) }

        sourceFiles.addWorkspaceRoot(DirectoryManager.HOLLOW_ENGINE.resolve("scripts"))
        val refreshed = classPath.addWorkspaceRoot(DirectoryManager.HOLLOW_ENGINE.resolve("scripts"))

        if(refreshed) sourcePath.refresh()


        textDocuments.lintAll()

        val serverInfo = ServerInfo("Kotlin Language Server", "1.0.0")

        InitializeResult(serverCapabilities, serverInfo)
    }

    override fun close() {
        textDocumentService.close()
        classPath.close()
        tempDirectory.close()
        async.shutdown(awaitTermination = true)
    }

    override fun shutdown(): CompletableFuture<Any> {
        close()
        return completedFuture(null)
    }

    override fun exit() {}

    // Fixed in https://github.com/eclipse/lsp4j/commit/04b0c6112f0a94140e22b8b15bb5a90d5a0ed851
    // Causes issue in lsp 0.15
    override fun getNotebookDocumentService(): NotebookDocumentService? {
		return null;
	}
}

object KotlinLanguageClient : LanguageClient {
    override fun telemetryEvent(p0: Any?) {
        TODO("Not yet implemented")
    }

    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
    }

    override fun showMessage(p0: MessageParams) {
        TODO("Not yet implemented")
    }

    override fun showMessageRequest(p0: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        TODO("Not yet implemented")
    }

    override fun logMessage(p0: MessageParams) {
        TODO("Not yet implemented")
    }

}