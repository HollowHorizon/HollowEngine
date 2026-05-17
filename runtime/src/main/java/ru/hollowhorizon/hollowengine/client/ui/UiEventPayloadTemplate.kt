package ru.hollowhorizon.hollowengine.client.ui

import net.minecraft.nbt.CompoundTag

class UiEventPayloadTemplate private constructor(
    private val entries: List<Entry>,
) {
    fun resolve(event: UiEvent): CompoundTag {
        val tag = CompoundTag()
        for (entry in entries) {
            putValue(tag, entry.key, entry.value.resolve(event))
        }
        return tag
    }

    private data class Entry(
        val key: String,
        val value: Value,
    )

    private sealed interface Value {
        fun resolve(event: UiEvent): Any?

        data class Constant(private val value: Any?) : Value {
            override fun resolve(event: UiEvent): Any? = value
        }

        data class Dynamic(private val path: String) : Value {
            override fun resolve(event: UiEvent): Any? = event.read(path)
        }

        data class Compound(private val template: UiEventPayloadTemplate) : Value {
            override fun resolve(event: UiEvent): Any = template.resolve(event)
        }
    }

    companion object {
        fun parse(source: String): UiEventPayloadTemplate = Parser(source).parse()
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): UiEventPayloadTemplate {
            skipWhitespace()
            if (peek() == '{') index++
            val entries = mutableListOf<Entry>()
            while (!isEnd()) {
                skipSeparators()
                if (peek() == '}') {
                    index++
                    break
                }
                val key = parseKey()
                skipWhitespace()
                expect(':')
                val value = parseValue()
                entries += Entry(key, value)
                skipSeparators()
                if (peek() == '}') {
                    index++
                    break
                }
            }
            return UiEventPayloadTemplate(entries)
        }

        private fun parseKey(): String {
            skipWhitespace()
            if (peek() == '"' || peek() == '\'') return parseQuoted()
            val start = index
            while (!isEnd()) {
                val char = source[index]
                if (!char.isLetterOrDigit() && char != '_' && char != '-' && char != '.') break
                index++
            }
            require(start != index) { "Expected event payload key at $index" }
            return source.substring(start, index)
        }

        private fun parseValue(): Value {
            skipWhitespace()
            return when (peek()) {
                '"', '\'' -> Value.Constant(parseQuoted())
                '<' -> Value.Dynamic(parseDynamic())
                '{' -> Value.Compound(parse())
                else -> Value.Constant(parseLiteral())
            }
        }

        private fun parseDynamic(): String {
            expect('<')
            val start = index
            while (!isEnd() && source[index] != '>') index++
            require(!isEnd()) { "Unclosed dynamic event expression at $start" }
            val value = source.substring(start, index).trim()
            expect('>')
            return value
        }

        private fun parseQuoted(): String {
            val quote = peek()
            expect(quote)
            val result = StringBuilder()
            while (!isEnd()) {
                val char = source[index++]
                if (char == quote) return result.toString()
                if (char == '\\' && !isEnd()) {
                    result.append(source[index++])
                } else {
                    result.append(char)
                }
            }
            throw IllegalArgumentException("Unclosed quoted event payload value")
        }

        private fun parseLiteral(): Any? {
            val start = index
            while (!isEnd() && source[index] != ',' && source[index] != ';' && source[index] != '}') index++
            val raw = source.substring(start, index).trim()
            return when {
                raw.equals("true", ignoreCase = true) -> true
                raw.equals("false", ignoreCase = true) -> false
                raw.equals("null", ignoreCase = true) -> null
                raw.toIntOrNull() != null -> raw.toInt()
                raw.toFloatOrNull() != null -> raw.toFloat()
                else -> raw
            }
        }

        private fun skipSeparators() {
            skipWhitespace()
            while (!isEnd() && (source[index] == ',' || source[index] == ';')) {
                index++
                skipWhitespace()
            }
        }

        private fun skipWhitespace() {
            while (!isEnd() && source[index].isWhitespace()) index++
        }

        private fun expect(char: Char) {
            require(peek() == char) { "Expected '$char' at $index" }
            index++
        }

        private fun peek(): Char = source.getOrNull(index) ?: '\u0000'

        private fun isEnd(): Boolean = index >= source.length
    }
}

private fun putValue(tag: CompoundTag, key: String, value: Any?) {
    when (value) {
        null -> tag.putString(key, "")
        is CompoundTag -> tag.put(key, value)
        is Boolean -> tag.putBoolean(key, value)
        is Int -> tag.putInt(key, value)
        is Long -> tag.putLong(key, value)
        is Float -> tag.putFloat(key, value)
        is Double -> tag.putDouble(key, value)
        else -> tag.putString(key, value.toString())
    }
}
