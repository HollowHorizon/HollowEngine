package ru.hollowhorizon.hollowengine.common.ide.session.completion.util

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace
import org.jetbrains.kotlin.psi.psiUtil.startOffset

open class KeywordLookupObject {
    override fun equals(other: Any?): Boolean = this === other || javaClass == other?.javaClass
    override fun hashCode(): Int = javaClass.hashCode()
}

fun createKeywordConstructLookupElement(
    project: Project,
    keyword: String,
    fileTextToReformat: String,
    fileName: String,
    trimSpacesAroundCaret: Boolean = false
): LookupElement {
    val file = KtPsiFactory(project).createFile(fileName, fileTextToReformat)
    val newFileText = file.text

    val keywordOffset = newFileText.indexOf(keyword)
    assert(keywordOffset >= 0)
    val keywordEndOffset = keywordOffset + keyword.length

    val caretPlaceHolder = "caret"

    val caretOffset = newFileText.indexOf(caretPlaceHolder)
    assert(caretOffset >= 0)
    assert(caretOffset >= keywordEndOffset)

    var tailBeforeCaret = newFileText.substring(keywordEndOffset, caretOffset)
    var tailAfterCaret = newFileText.substring(caretOffset + caretPlaceHolder.length)

    if (trimSpacesAroundCaret) {
        tailBeforeCaret = tailBeforeCaret.trimEnd()
        tailAfterCaret = tailAfterCaret.trimStart()
    }

    val indent = detectIndent(newFileText, keywordOffset)
    tailBeforeCaret = tailBeforeCaret.unindent(indent)
    tailAfterCaret = tailAfterCaret.unindent(indent)

    val tailText = (if (tailBeforeCaret.contains('\n')) tailBeforeCaret.replace("\n", "").trimEnd() else tailBeforeCaret) +
            "..." +
            (if (tailAfterCaret.contains('\n')) tailAfterCaret.replace("\n", "").trimStart() else tailAfterCaret)

    return LookupElementBuilder.create(KeywordLookupObject(), keyword)
        .bold()
        .withTailText(tailText)
        .withInsertHandler { insertionContext, _ ->
            if (insertionContext.completionChar == '\n' ||
                insertionContext.completionChar == '\t' ||
                insertionContext.completionChar == 0.toChar()) {

                val offset = insertionContext.tailOffset
                val newIndent = detectIndent(insertionContext.document.charsSequence, offset - keyword.length)

                val beforeCaret = tailBeforeCaret.indentLinesAfterFirst(newIndent)
                val afterCaret = tailAfterCaret.indentLinesAfterFirst(newIndent)

                val element = insertionContext.file.findElementAt(offset)

                val sibling = when {
                    element !is PsiWhiteSpace -> element
                    element.textContains('\n') -> null
                    else -> element.getNextSiblingIgnoringWhitespace(true)
                }

                if (sibling != null && beforeCaret.trimStart().startsWith(insertionContext.document.getText(TextRange.from(sibling.startOffset, 1)))) {
                    //insertionContext.editor.moveCaret(sibling.startOffset + 1) TODO
                }
                else {
                    insertionContext.document.insertString(offset, beforeCaret + afterCaret)
                    //insertionContext.editor.moveCaret(offset + beforeCaret.length) TODO
                }
            }
        }
}

private fun detectIndent(text: CharSequence, offset: Int): String {
    return text.substring(0, offset)
        .substringAfterLast('\n')
        .takeWhile(Char::isWhitespace)
}

private fun String.unindent(indent: String): String {
    val text = this
    return buildString {
        val lines = text.lines()
        for ((index, line) in lines.withIndex()) {
            append(line.removePrefix(indent))
            if (index != lines.lastIndex) append('\n')
        }
    }
}

private fun String.indentLinesAfterFirst(indent: String): String {
    val text = this
    return buildString {
        val lines = text.lines()
        for ((index, line) in lines.withIndex()) {
            if (index > 0) append(indent)
            append(line)
            if (index != lines.lastIndex) append('\n')
        }
    }
}
