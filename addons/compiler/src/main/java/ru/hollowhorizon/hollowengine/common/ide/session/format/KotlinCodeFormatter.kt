package ru.hollowhorizon.hollowengine.common.ide.session.format

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.lexer.KtKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

/**
 * Reformats indentation and spacing without ever moving a token to another line.
 */
fun formatKotlinCode(file: KtFile): String = KotlinCodeFormatter(file).format()

private const val IndentUnit = "    "

/** Keywords that continue the statement above but stay at its indentation. */
private val ContinuationKeywords: Set<IElementType> = setOf(
    KtTokens.ELSE_KEYWORD,
    KtTokens.CATCH_KEYWORD,
    KtTokens.FINALLY_KEYWORD,
)

private val OpeningBrackets: Set<IElementType> = setOf(KtTokens.LBRACE, KtTokens.LPAR, KtTokens.LBRACKET)
private val ClosingBrackets: Set<IElementType> = setOf(KtTokens.RBRACE, KtTokens.RPAR, KtTokens.RBRACKET)

private class KotlinCodeFormatter(private val file: KtFile) {
    private val text = file.text
    private val leaves = ArrayList<PsiElement>()

    /** Bracket nesting in front of each leaf; a line's indent is the nesting it opens on. */
    private val depths: IntArray

    private val leafStarts: IntArray
    private val lineStarts: IntArray

    init {
        collectLeaves(file)
        leafStarts = IntArray(leaves.size) { index -> leaves[index].textRange.startOffset }
        depths = IntArray(leaves.size)
        var depth = 0
        for (index in leaves.indices) {
            depths[index] = depth
            val type = leaves[index].elementType()
            when (type) {
                in OpeningBrackets -> depth++
                in ClosingBrackets -> depth = (depth - 1).coerceAtLeast(0)
            }
        }
        lineStarts = lineStarts(text)
    }

    fun format(): String {
        val result = StringBuilder(text.length)
        val frames = ArrayDeque<IndentFrame>()
        var lineIndent = 0
        var spaceTyped = false

        var previous: PsiElement? = null

        for (index in leaves.indices) {
            val leaf = leaves[index]
            if (leaf is PsiWhiteSpace) {
                val newlines = leaf.text.count { it == '\n' }
                if (newlines == 0) {
                    spaceTyped = true
                    continue
                }
                repeat(newlines) { result.append('\n') }
                previous = null
                spaceTyped = false
                val next = index + 1
                if (next > leaves.lastIndex) continue
                lineIndent = indentLevelOf(next, frames)
                result.append(IndentUnit.repeat(lineIndent))
                continue
            }

            val separated = when (previous?.let { spacingBetween(it, leaf) }) {
                Spacing.REQUIRED -> true
                Spacing.FORBIDDEN -> false
                Spacing.KEEP -> spaceTyped
                null -> false
            }
            if (separated) result.append(' ')

            when (leaf.elementType()) {
                in OpeningBrackets -> frames.addLast(IndentFrame(lineIndent + 1, lineIndent))
                in ClosingBrackets -> frames.removeLastOrNull()
            }
            result.append(leaf.text)
            previous = leaf
            spaceTyped = false
        }
        if (result.isNotEmpty() && result.last() != '\n') result.append('\n')
        return result.toString()
    }

    private fun indentLevelOf(index: Int, frames: ArrayDeque<IndentFrame>): Int {
        val type = leaves[index].elementType()
        val frame = frames.lastOrNull()
        return when (type) {
            in ClosingBrackets -> frame?.openerLevel ?: 0
            in ContinuationKeywords -> frame?.contentLevel ?: 0
            else -> {
                val base = frame?.contentLevel ?: 0
                if (continuesEarlierStatement(index)) base + 1 else base
            }
        }.coerceAtLeast(0)
    }

    private fun continuesEarlierStatement(index: Int): Boolean {
        val leaf = leaves[index]
        val statement = statementOf(leaf) ?: return false
        val start = firstCodeLeafIndex(statement) ?: return false
        if (start >= index) return false
        if (lineOf(leafStarts[start]) == lineOf(leafStarts[index])) return false
        return depths[start] == depths[index]
    }

    private fun firstCodeLeafIndex(statement: PsiElement): Int? {
        val range = statement.textRange
        val found = leafStarts.binarySearch(range.startOffset)
        var index = if (found >= 0) found else -found - 1
        while (index <= leaves.lastIndex && leafStarts[index] < range.endOffset) {
            val leaf = leaves[index]
            if (leaf !is PsiWhiteSpace && leaf !is PsiComment && !leaf.isDeclarationPrefix()) return index
            index++
        }
        return null
    }

    private fun statementOf(leaf: PsiElement): PsiElement? {
        var current: PsiElement = leaf
        var parent: PsiElement? = current.parent
        while (parent != null && !parent.isStatementHolder()) {
            current = parent
            parent = parent.parent
        }
        return current.takeIf { parent != null && it !== leaf }
    }

    private fun lineOf(offset: Int): Int {
        val found = lineStarts.binarySearch(offset)
        return if (found >= 0) found else -found - 2
    }

    private fun collectLeaves(element: PsiElement) {
        val child = element.firstChild
        if (child == null) {
            if (element.textLength > 0) leaves += element
            return
        }
        var current: PsiElement? = child
        while (current != null) {
            collectLeaves(current)
            current = current.nextSibling
        }
    }
}

private data class IndentFrame(val contentLevel: Int, val openerLevel: Int)
private enum class Spacing { REQUIRED, FORBIDDEN, KEEP }

private val TightBefore: Set<IElementType> = setOf(
    KtTokens.COMMA,
    KtTokens.SEMICOLON,
    KtTokens.RPAR,
    KtTokens.RBRACKET,
    KtTokens.EXCLEXCL,
    KtTokens.QUEST,
    KtTokens.DOT,
    KtTokens.SAFE_ACCESS,
    KtTokens.COLONCOLON,
)

private val TightAfter: Set<IElementType> = setOf(
    KtTokens.LPAR,
    KtTokens.LBRACKET,
    KtTokens.DOT,
    KtTokens.SAFE_ACCESS,
    KtTokens.COLONCOLON,
    KtTokens.AT,
)

private val ValueEndTokens: Set<IElementType> = setOf(
    KtTokens.IDENTIFIER,
    KtTokens.RPAR,
    KtTokens.RBRACE,
    KtTokens.RBRACKET,
)

private val ConditionKeywords: Set<IElementType> = setOf(
    KtTokens.IF_KEYWORD,
    KtTokens.WHILE_KEYWORD,
    KtTokens.FOR_KEYWORD,
    KtTokens.WHEN_KEYWORD,
    KtTokens.CATCH_KEYWORD,
)

private fun spacingBetween(left: PsiElement, right: PsiElement): Spacing {
    val leftType = left.elementType()
    val rightType = right.elementType()

    if (rightType in TightBefore) return Spacing.FORBIDDEN
    if (leftType in TightAfter) return Spacing.FORBIDDEN
    if (rightType == KtTokens.LPAR && right.parent.isArgumentOrParameterList()) return Spacing.FORBIDDEN
    if (rightType == KtTokens.LBRACKET && leftType in ValueEndTokens) return Spacing.FORBIDDEN

    if (leftType in ConditionKeywords && rightType == KtTokens.LPAR) return Spacing.REQUIRED
    if (rightType is KtKeywordToken && leftType in ValueEndTokens) return Spacing.REQUIRED
    if (rightType in ContinuationKeywords) return Spacing.REQUIRED
    if (rightType == KtTokens.LBRACE) return Spacing.REQUIRED
    if (leftType == KtTokens.LBRACE && left.parent is KtFunctionLiteral) return Spacing.REQUIRED
    if (rightType == KtTokens.RBRACE && right.parent is KtFunctionLiteral) return Spacing.REQUIRED
    if (leftType == KtTokens.COMMA || leftType == KtTokens.SEMICOLON) return Spacing.REQUIRED
    if (leftType == KtTokens.ARROW || rightType == KtTokens.ARROW) return Spacing.REQUIRED
    if (leftType == KtTokens.EQ || rightType == KtTokens.EQ) return Spacing.REQUIRED
    if (left.isBinaryOperator() || right.isBinaryOperator()) return Spacing.REQUIRED
    if (leftType == KtTokens.COLON && left.parent !is KtAnnotationEntry) return Spacing.REQUIRED

    return Spacing.KEEP
}

private fun PsiElement?.isArgumentOrParameterList(): Boolean =
    this is KtValueArgumentList || this is KtParameterList

private fun PsiElement.isBinaryOperator(): Boolean {
    val reference = parent as? KtOperationReferenceExpression ?: return false
    if (reference.parent !is KtBinaryExpression) return false
    return elementType() != KtTokens.RANGE && text != "..<"
}

private fun PsiElement.isStatementHolder(): Boolean =
    this is KtBlockExpression ||
            this is KtClassBody ||
            this is KtFile ||
            this is KtScript ||
            this is KtImportList ||
            this is KtFileAnnotationList

private fun PsiElement.isDeclarationPrefix(): Boolean {
    var current: PsiElement? = parent
    while (current != null) {
        if (current is KtModifierList || current is KtAnnotationEntry) return true
        if (current is KtDeclaration || current is KtFile) return false
        current = current.parent
    }
    return false
}

private fun PsiElement.elementType(): IElementType? = node?.elementType

private fun lineStarts(text: String): IntArray {
    val starts = ArrayList<Int>()
    starts += 0
    for (index in text.indices) {
        if (text[index] == '\n') starts += index + 1
    }
    return starts.toIntArray()
}
