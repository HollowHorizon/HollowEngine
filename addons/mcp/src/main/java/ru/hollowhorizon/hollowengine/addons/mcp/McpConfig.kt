package ru.hollowhorizon.hollowengine.addons.mcp

import ru.hollowhorizon.hollowengine.common.config.Config
import ru.hollowhorizon.hollowengine.common.config.ConfigName
import ru.hollowhorizon.hollowengine.common.config.PropertyComment
import ru.hollowhorizon.hollowengine.common.config.PropertyName
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
import java.security.SecureRandom
import java.util.Base64

@ConfigName("hollowengine-mcp")
object McpConfig : Config() {
    @PropertyComment("Starts the MCP server coding agents connect to. Off by default on dedicated servers")
    @PropertyName("enabled")
    var enabled by property(isPhysicalClient)

    @PropertyComment("Port the server listens on. It only ever binds to 127.0.0.1")
    @PropertyName("port")
    var port by property(25590)

    @PropertyComment("Agents send it as 'Authorization: Bearer <token>'. Generated when empty; clear it to issue a new one")
    @PropertyName("token")
    var token by property("")

    /** The token, generated and saved on the first start. */
    fun ensureToken(): String {
        token.takeIf(String::isNotBlank)?.let { return it }
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also { generated ->
            token = generated
            save(force = true)
        }
    }
}
