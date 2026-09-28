package ru.hollowhorizon.hollowengine.addons.mcp

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import kotlinx.coroutines.CancellationException
import ru.hollowhorizon.hollowengine.HollowEngine
import java.security.MessageDigest

/**
 * The HTTP endpoint agents connect to. Every agent connection opens its own MCP session, and all of
 * them share [toolset]; [instructions] is asked again for each session, so it reports the game as it is
 * when the agent connects.
 */
class McpServer(
    private val version: String,
    private val toolset: List<McpTool>,
    private val instructions: () -> String,
) {
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var boundPort = 0

    /** Where agents connect, or null while the server is not running. */
    val url: String? get() = engine?.let { "http://$HOST:$boundPort$PATH" }

    /** Binds [port] on the loopback interface; throws when the port is taken. */
    fun start(port: Int, token: String, classLoader: ClassLoader) {
        val server = embeddedServer(CIO, host = HOST, port = port) {
            requireToken(token)
            mcpStreamableHttp(path = PATH) { createSession() }
        }
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader
        try {
            server.start(wait = false)
        } finally {
            thread.contextClassLoader = previous
        }
        engine = server
        boundPort = port
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 2_000)
        engine = null
    }

    private fun createSession(): Server = Server(
        serverInfo = Implementation(name = "hollowengine", version = version, title = "HollowEngine"),
        options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))),
        instructionsProvider = instructions,
    ) {
        toolset.forEach { tool ->
            addTool(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.schema,
                toolAnnotations = ToolAnnotations(readOnlyHint = tool.readOnly, destructiveHint = false),
            ) { request -> runTool(tool, ToolArguments(request.arguments)) }
        }
    }

    private suspend fun runTool(tool: McpTool, arguments: ToolArguments) = try {
        tool.handler(arguments)
    } catch (error: CancellationException) {
        throw error
    } catch (error: ToolInputException) {
        errorResult(error.message.orEmpty())
    } catch (error: Exception) {
        HollowEngine.LOGGER.error("MCP tool '{}' failed", tool.name, error)
        errorResult("${tool.name} failed: ${error.message ?: error::class.simpleName}")
    }

    /** Answers 401 to any request without the token. The comparison takes the same time for any input. */
    private fun Application.requireToken(token: String) {
        val expected = "Bearer $token".toByteArray()
        intercept(ApplicationCallPipeline.Plugins) {
            val presented = call.request.headers[HttpHeaders.Authorization]?.toByteArray()
            if (presented == null || !MessageDigest.isEqual(presented, expected)) {
                call.respondText("Missing or wrong bearer token", status = HttpStatusCode.Unauthorized)
                finish()
            }
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
        const val PATH = "/mcp"
    }
}
