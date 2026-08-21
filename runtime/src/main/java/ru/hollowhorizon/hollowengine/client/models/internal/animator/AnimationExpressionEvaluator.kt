package ru.hollowhorizon.hollowengine.client.models.internal.animator

import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.attachments.components.AnimationExpression
import ru.hollowhorizon.hollowengine.common.attachments.components.AnimationVectorExpression

data class AnimatorEvaluationContext(
    val deltaTime: Float,
    val time: Float,
    val values: Map<String, Float> = emptyMap(),
) {
    fun with(vararg pairs: Pair<String, Float>): AnimatorEvaluationContext =
        copy(values = values + pairs)
}

class AnimationExpressionEvaluator {
    private val cache = linkedMapOf<String, CompiledAnimationExpression>()

    fun float(expression: AnimationExpression, context: AnimatorEvaluationContext, default: Float = 0f): Float {
        val source = expression.source.trim()
        if (source.isEmpty()) return default
        source.toFloatOrNull()?.let { return it }
        context.values[source]?.let { return it }

        return cache.getOrPut(source) { CompiledAnimationExpression(source) }
            .float(context, default)
    }

    fun boolean(expression: AnimationExpression, context: AnimatorEvaluationContext, default: Boolean = false): Boolean {
        val source = expression.source.trim()
        if (source.isEmpty()) return default
        source.toBooleanStrictOrNull()?.let { return it }
        context.values[source]?.let { return it != 0f }

        return cache.getOrPut(source) { CompiledAnimationExpression(source) }
            .boolean(context, default)
    }

    fun vector(expression: AnimationVectorExpression, context: AnimatorEvaluationContext): Vec3f =
        Vec3f(
            float(expression.x, context),
            float(expression.y, context),
            float(expression.z, context),
        )
}

private class CompiledAnimationExpression(private val source: String) {
    private val fastExpression = runCatching { FastAnimationExpression.compile(source) }.getOrNull()
    private val variableNames = source.variableNames()
    private val variableValues = linkedMapOf<String, Double>()
    private var failureLogged = false


    fun float(context: AnimatorEvaluationContext, default: Float): Float = fastExpression?.float(context) ?: default

    fun boolean(context: AnimatorEvaluationContext, default: Boolean): Boolean = fastExpression?.boolean(context) ?: default

}

private fun String.variableNames(): Set<String> {
    val result = linkedSetOf<String>()
    var index = 0
    while (index < length) {
        val char = this[index]
        if (!char.isIdentifierStart()) {
            index++
            continue
        }

        val start = index
        index++
        while (index < length && this[index].isIdentifierPart()) index++
        val name = substring(start, index)
        val next = nextNonWhitespace(index)
        if (name !in RESERVED_WORDS && next != '(') result += name
    }
    return result
}

private fun String.nextNonWhitespace(start: Int): Char? {
    var index = start
    while (index < length && this[index].isWhitespace()) index++
    return getOrNull(index)
}

private fun Char.isIdentifierStart(): Boolean =
    this == '_' || isLetter()

private fun Char.isIdentifierPart(): Boolean =
    this == '_' || isLetterOrDigit()

private val RESERVED_WORDS = setOf(
    "true",
    "false",
    "null",
    "if",
    "else",
    "when",
    "is",
    "as",
    "val",
    "var",
    "fun",
    "return",
)