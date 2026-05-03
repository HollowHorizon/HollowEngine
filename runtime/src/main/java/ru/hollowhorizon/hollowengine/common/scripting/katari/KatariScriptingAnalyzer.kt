package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariNarrativeProgram
import ru.hollowhorizon.hollowengine.common.scripting.ide.*
import java.util.*

object KatariScriptingAnalyzer : ScriptingAnalyzer {
    private val keywords = setOf(
        "choose", "checkpoint", "jump", "if", "else", "val", "var", "fun", "return",
        "while", "for", "in", "break", "continue", "true", "false", "null", "disableIf", "with"
    )
    private val functions = setOf(
        "narrate", "say", "readLine", "choose", "chooseIndexed", "choiceOption",
        "pos", "wait", "npc", "moveTo", "lookAt", "remove", "despawn", "setHealth", "heal",
        "setCustomName", "isAlive", "position", "dimension", "stopNavigation"
    )

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        return text.lines().map { line -> TextLine(tokenize(line), ArrayList()) }
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        return emptyList()
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        return runCatching<List<Diagnostic>> {
            KatariNarrativeProgram(name, text)
            emptyList()
        }.getOrElse { error ->
            listOf(error.toDiagnostic())
        }
    }

    private fun tokenize(line: String): List<Pair<String, SpanStyle>> {
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var i = 0
        while (i < line.length) {
            val start = i
            val style = when {
                line.startsWith("//", i) -> {
                    spans += line.substring(i) to style(TokenType.COMMENT)
                    break
                }

                line[i].isWhitespace() -> {
                    while (i < line.length && line[i].isWhitespace()) i++
                    style(TokenType.DEFAULT)
                }

                line[i] == '"' -> {
                    i++
                    while (i < line.length) {
                        if (line[i] == '\\' && i + 1 < line.length) i += 2
                        else if (line[i++] == '"') break
                    }
                    style(TokenType.STRING)
                }

                line[i].isDigit() -> {
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) i++
                    style(TokenType.NUMERIC_LITERAL)
                }

                line[i].isIdentifierStart() -> {
                    i++
                    while (i < line.length && line[i].isIdentifierPart()) i++
                    val word = line.substring(start, i)
                    when {
                        word in keywords -> style(TokenType.KEYWORD)
                        word in functions -> style(TokenType.FUNCTION)
                        word.first().isUpperCase() -> style(TokenType.CLASS)
                        else -> style(TokenType.DEFAULT)
                    }
                }

                else -> {
                    i++
                    style(TokenType.DEFAULT)
                }
            }
            if (i > start) spans += line.substring(start, i) to style
        }
        return spans
    }

    private fun Throwable.toDiagnostic(): Diagnostic {
        val message = message ?: javaClass.simpleName
        val match = Regex(""":(\d+):(\d+)]""").find(message)
        val line = (match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
        val col = (match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
        return Diagnostic(
            Range(Position(line, col), Position(line, col + 1)),
            Severity.ERROR,
            message.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
        )
    }

    private fun style(token: TokenType) = SpanStyle(token, italic = false, bold = false, highlight = false)

    private fun Char.isIdentifierStart() = this == '_' || isLetter()
    private fun Char.isIdentifierPart() = this == '_' || isLetterOrDigit()
}
