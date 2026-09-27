package ru.hollowhorizon.hollowengine.addons.mcp

/** The agents `/he mcp` can copy a connection for; each spells the same address and token its own way. */
internal enum class AgentClient(val id: String, val title: String, val color: Int, val hint: String) {
    CLAUDE_CODE("claude", "Claude Code", 0xD97757, "A terminal command that adds this server to Claude Code") {
        override fun connection(url: String, token: String) =
            "claude mcp add --transport http hollowengine $url --header \"Authorization: Bearer $token\""
    },
    CODEX("codex", "Codex", 0x10A37F, "A block for ~/.codex/config.toml") {
        override fun connection(url: String, token: String) = """
            [mcp_servers.hollowengine]
            url = "$url"
            http_headers = { "Authorization" = "Bearer $token" }
        """.trimIndent()
    },
    JSON("json", "JSON", 0xE5C07B, "An mcpServers entry for Cursor and other clients that read one") {
        override fun connection(url: String, token: String) = """
            {
              "mcpServers": {
                "hollowengine": {
                  "url": "$url",
                  "headers": { "Authorization": "Bearer $token" }
                }
              }
            }
        """.trimIndent()
    },
    TOKEN("token", "Token", 0x8F9BAD, "The access token alone") {
        override fun connection(url: String, token: String) = token
    };

    abstract fun connection(url: String, token: String): String

    val hintKey: String get() = "hollowengine_mcp.command.hint.$id"
}
