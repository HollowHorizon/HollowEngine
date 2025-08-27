package ru.hollowhorizon.hollowengine.client.models.fbx

import ru.hollowhorizon.hollowengine.client.models.util.*
import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty0

enum class TokenType {
    OPEN_BRACKET,
    CLOSE_BRACKET,
    DATA,
    BINARY_DATA,
    COMMA,
    KEY;

    val i = ordinal
}

class Token(
    var begin: Int,
    var end: Int,
    val type: TokenType,
    line: Int,
    val _column: Int = BINARY_MARKER
) {
    init {
        assert(begin != -1 && end != -1)

        if (_column == BINARY_MARKER)
            assert(end >= begin)
        else
            assert(end - begin > 0)
    }

    var contents = ""

    var line = line

    inline var offset: Int
        get() {
            return line
        }
        set(value) {
            line = value
        }

    val column = _column
        get() {
            assert(!isBinary)
            return field
        }

    val isBinary get() = _column == BINARY_MARKER

    override fun toString() = "$type, ${if (isBinary) "offset 0x$offset" else "line $line, col $column"}"

    val stringContents get() = String(ByteArray(end - begin, { buffer[begin + it] }))

    companion object {
        val BINARY_MARKER = -1
    }

    val parseAsString: String
        get() {
            if (type != TokenType.DATA) parseError("expected TOK_DATA token", this)

            if (isBinary) {
                if (buffer[begin].toInt().toChar() != 'S') parseError("failed to parse S(tring), unexpected data type (binary)", this)
                // read string length
                val len = buffer.getInt(begin + 1)

                assert(end - begin == 5 + len)
                return String(ByteArray(len, { buffer[begin + 5 + it] }))
            }

            val length = end - begin
            if (length < 2) parseError("token is too short to hold a string", this)

            val s = buffer[begin].toInt().toChar()
            val e = buffer[end - 1].toInt().toChar()
            if (s != '"' || e != '"') parseError("expected double quoted string", this)

            return String(ByteArray(length - 2, { buffer[begin + 1 + it] }))
        }

    val parseAsInt: Int
        get() {
            if (type != TokenType.DATA) parseError("expected TOK_DATA token", this)

            if (isBinary) {
                if (buffer[begin].toInt().toChar() != 'I') parseError("failed to parse I(nt), unexpected data type (binary)", this)

                return buffer.getInt(begin + 1)
            }

            assert(end - begin > 0)

            return buffer.strtol10(begin, end)
        }

    val parseAsId: Long
        get() {
            if (type != TokenType.DATA) parseError("expected TOK_DATA token", this)
            if (isBinary) {
                if (buffer[begin].toInt().toChar() != 'L') parseError("failed to parse ID, unexpected data type, expected L(ong) (binary)", this)
                return buffer.getLong(begin + 1)
            }
            val length = end - begin
            assert(length > 0)
            val beginOutMax = intArrayOf(begin, 0, length)
            val id = buffer.strtoul10_64(beginOutMax)
            if (beginOutMax[1] > end) parseError("failed to parse ID (text)", this)
            return id
        }

    val parseAsInt64: Long
        get() {
            if (type != TokenType.DATA) parseError("expected TOK_DATA token", this)
            if (isBinary) {
                if (buffer[begin].toInt().toChar() != 'L') parseError("failed to parse Int64, unexpected data type", this)
                return buffer.getLong(begin + 1)
            }
            val length = end - begin
            assert(length > 0)
            val beginOutMax = intArrayOf(begin, 0, length)
            val id = buffer.strtol10_64(begin, end)
            if (beginOutMax[1] > end) parseError("failed to parse Int64 (text)", this)
            return id
        }

    val parseAsDim: Long
        get() {
            if (type != TokenType.DATA) parseError("expected TOK_DATA token", this)
            if (isBinary) {
                TODO()
                if (buffer[begin].toInt().toChar() != 'L') parseError("failed to parse Int64, unexpected data type", this)
                return buffer.getLong(begin + 1)
            }
            if (buffer[begin].toInt().toChar() != '*')
                parseError("expected asterisk before array dimension", this)

            val length = end - ++begin

            if (length == 0)
                parseError("expected valid integer number after asterisk", this)

            return buffer.strtol10_64(begin, end)
        }

    val parseAsFloat: Float
        get() {
            if (type != TokenType.DATA) parseError("expected TOK_DATA token", this)
            if (isBinary)
                return when (buffer.get(begin).toInt().toChar()) {
                    'F' -> buffer.getFloat(begin + 1)
                    'D' -> buffer.getDouble(begin + 1).toFloat()
                    else -> error("failed to parse F(loat) or D(ouble), unexpected data type (binary)")
                }
            return buffer.fast_atof(begin, end)
        }
}

fun tokenize(outputTokens: ArrayList<Token>, input: ByteBuffer) {

//    assert(input.isNotEmpty())  // TODO

    // line and column numbers numbers are one-based
    var line = 1
    var column = 1

    var comment = false
    var inDoubleQuotes = false
    var pendingDataToken = false

    val chars = input

    var cur = 0
    var begin = true

    while (cur + 1 < chars.rem) {

        if (!begin) column += if (chars[cur++].toInt().toChar() == HT) 4 else 1
        begin = false

        var c = chars[cur].toInt().toChar()

        if (c.isLineEnd) {
            comment = false
            column = 0
            ++line
            // if we have another lineEnd at the next position (typically \f\n), move directly to next char (\n)
            if (cur + 1 < chars.rem && chars[cur + 1].toInt().toChar().isLineEnd) c = chars[++cur].toInt().toChar()
            continue
        }

        if (comment) continue

        if (inDoubleQuotes) {
            if (c == '\"') {
                inDoubleQuotes = false
                tokenEnd = cur

                processDataToken(outputTokens, chars, ::tokenBegin, ::tokenEnd, line, column)
                pendingDataToken = false
            }
            continue
        }
        if (c == '\"') {
            if (tokenBegin != -1) tokenizeError("unexpected double-quote", line, column)
            tokenBegin = cur
            inDoubleQuotes = true
            continue
        } else if (c == ';') {
            processDataToken(outputTokens, chars, ::tokenBegin, ::tokenEnd, line, column)
            comment = true
            continue
        } else if (c == '{') {
            processDataToken(outputTokens, chars, ::tokenBegin, ::tokenEnd, line, column)
            outputTokens += Token(cur, cur + 1, TokenType.OPEN_BRACKET, line, column)
            continue
        } else if (c == '}') {
            processDataToken(outputTokens, chars, ::tokenBegin, ::tokenEnd, line, column)
            outputTokens += Token(cur, cur + 1, TokenType.CLOSE_BRACKET, line, column)
            continue
        } else if (c == ',') {
            if (pendingDataToken)
                processDataToken(outputTokens, chars, ::tokenBegin, ::tokenEnd, line, column, TokenType.DATA, true)
            outputTokens += Token(cur, cur + 1, TokenType.COMMA, line, column)
            continue
        } else if (c == ':') {
            if (pendingDataToken)
                processDataToken(outputTokens, chars, ::tokenBegin, ::tokenEnd, line, column, TokenType.KEY, true)
            else tokenizeError("unexpected colon", line, column)
            continue
        }

        if (c.isSpaceOrNewLine) {
            if (tokenBegin != -1) {
                // peek ahead and check if the next token is a colon in which case this counts as KEY token.
                var type = TokenType.DATA
                var peek = cur
                var p = chars[peek].toInt().toChar()
                while (p != NUL && p.isSpaceOrNewLine) {
                    if (p == ':') {
                        type = TokenType.KEY
                        cur = peek
                        break
                    }
                    p = chars[++peek].toInt().toChar()
                }
                processDataToken(outputTokens, chars, ::tokenBegin, ::tokenEnd, line, column, type)
            }
            pendingDataToken = false
        } else {
            tokenEnd = cur
            if (tokenBegin == -1) tokenBegin = cur
            pendingDataToken = true
        }
    }
}

private var tokenBegin = -1
private var tokenEnd = -1

fun tokenizeError(message: String, line: Int, column: Int): Nothing = throw Exception(Util.addLineAndColumn("FBX-Tokenize", message, line, column))

fun processDataToken(outputTokens: ArrayList<Token>, chars: ByteBuffer, start: KMutableProperty0<Int>, end: KMutableProperty0<Int>,
                     line: Int, column: Int, type: TokenType = TokenType.DATA, mustHaveToken: Boolean = false) {

    if (start() != -1 && end() != -1) {
        // sanity check:
        // tokens should have no whitespace outside quoted text and [start,end] should properly delimit the valid range.
        var inDoubleQuotes = false
        for (i in start()..end()) {
            val c = chars[i].toInt().toChar()
            if (c == '\"')
                inDoubleQuotes = !inDoubleQuotes
            if (!inDoubleQuotes && c.isSpaceOrNewLine)
                tokenizeError("unexpected whitespace in token", line, column)
        }
        if (inDoubleQuotes)
            tokenizeError("non-terminated double quotes", line, column)
        outputTokens += Token(start(), end() + 1, type, line, column)
    } else if (mustHaveToken)
        tokenizeError("unexpected character, expected data token", line, column)

    start.set(-1)
    end.set(-1)
}