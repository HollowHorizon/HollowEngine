import ru.hollowhorizon.hollowengine.addons.mcp.McpServer
import ru.hollowhorizon.hollowengine.addons.mcp.McpTool
import ru.hollowhorizon.hollowengine.addons.mcp.ParameterType
import ru.hollowhorizon.hollowengine.addons.mcp.ToolParameter
import ru.hollowhorizon.hollowengine.addons.mcp.textResult
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class McpServerTest {
    private val echo = McpTool(
        name = "echo",
        description = "Repeats its text",
        parameters = listOf(ToolParameter("text", ParameterType.STRING, "What to repeat", required = true)),
        readOnly = true,
    ) { arguments -> textResult("echo: ${arguments.string("text")}") }

    private val server = McpServer("test", listOf(echo)) { "instructions for the agent" }
    private val client = HttpClient.newHttpClient()
    private val port = ServerSocket(0).use { it.localPort }

    @BeforeTest
    fun start() = server.start(port, TOKEN, javaClass.classLoader)

    @AfterTest
    fun stop() = server.stop()

    @Test
    fun `a request without the token is refused`() {
        assertEquals(401, post(INITIALIZE, token = null).statusCode())
        assertEquals(401, post(INITIALIZE, token = "wrong").statusCode())
    }

    @Test
    fun `a session lists and calls the tools`() {
        val initialized = post(INITIALIZE)
        assertEquals(200, initialized.statusCode(), initialized.body())
        assertContains(initialized.body(), "instructions for the agent")
        val session = assertNotNull(initialized.headers().firstValue("mcp-session-id").orElse(null))

        post("""{"jsonrpc":"2.0","method":"notifications/initialized"}""", session = session)
        val listed = post("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""", session = session).body()
        assertContains(listed, "\"echo\"")
        assertContains(listed, "\"readOnlyHint\":true")

        val called = post(
            """{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"echo","arguments":{"text":"hi"}}}""",
            session = session,
        ).body()
        assertContains(called, "echo: hi")

        val missing = post(
            """{"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"echo","arguments":{}}}""",
            session = session,
        ).body()
        assertContains(missing, "'text' is required")
        assertContains(missing, "\"isError\":true")
    }

    private fun post(body: String, token: String? = TOKEN, session: String? = null): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/mcp"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .apply { if (session != null) header("mcp-session-id", session) }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private companion object {
        const val TOKEN = "secret"
        const val INITIALIZE = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""
    }
}
