package ru.hollowhorizon.hollowengine.addons.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** A tool agents can call. [readOnly] tools only look at the game and never change it. */
class McpTool(
    val name: String,
    val description: String,
    val parameters: List<ToolParameter>,
    val readOnly: Boolean,
    val handler: suspend (ToolArguments) -> CallToolResult,
) {
    val schema: ToolSchema
        get() = ToolSchema(
            properties = buildJsonObject {
                parameters.forEach { parameter -> put(parameter.name, parameter.schema()) }
            },
            required = parameters.filter(ToolParameter::required).map(ToolParameter::name),
        )
}

enum class ParameterType(val jsonName: String) { STRING("string"), INTEGER("integer"), NUMBER("number"), BOOLEAN("boolean") }

class ToolParameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = false,
    val allowed: List<String> = emptyList(),
) {
    fun schema(): JsonObject = buildJsonObject {
        put("type", type.jsonName)
        put("description", description)
        if (allowed.isNotEmpty()) putJsonArray("enum") { allowed.forEach { add(JsonPrimitive(it)) } }
    }
}

/** Thrown for arguments the agent got wrong; it is answered as a tool error rather than logged. */
class ToolInputException(message: String) : RuntimeException(message)

class ToolArguments(private val json: JsonObject?) {
    fun string(name: String): String = optionalString(name) ?: throw ToolInputException("'$name' is required")

    fun optionalString(name: String): String? = primitive(name)?.content?.takeIf { it.isNotEmpty() }

    fun int(name: String, default: Int): Int {
        val value = primitive(name) ?: return default
        return value.intOrNull ?: throw ToolInputException("'$name' must be an integer")
    }

    fun optionalDouble(name: String): Double? {
        val value = primitive(name) ?: return null
        return value.doubleOrNull ?: throw ToolInputException("'$name' must be a number")
    }

    fun boolean(name: String, default: Boolean): Boolean {
        val value = primitive(name) ?: return default
        return value.booleanOrNull ?: throw ToolInputException("'$name' must be true or false")
    }

    private fun primitive(name: String): JsonPrimitive? = json?.get(name) as? JsonPrimitive
}

fun textResult(text: String): CallToolResult = CallToolResult(content = listOf(TextContent(text)))

fun errorResult(text: String): CallToolResult = CallToolResult(content = listOf(TextContent(text)), isError = true)

fun contentResult(content: List<ContentBlock>): CallToolResult = CallToolResult(content = content)

/** Cuts [text] to [limit] characters, saying how much was left out, so one answer cannot flood the agent. */
fun truncate(text: String, limit: Int = OUTPUT_LIMIT): String {
    if (text.length <= limit) return text
    return text.take(limit) + "\n… ${text.length - limit} more characters cut"
}

const val OUTPUT_LIMIT = 20_000
