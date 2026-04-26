package ru.hollowhorizon.hollowengine.client.models.internal.animator

import com.sunnychung.lib.multiplatform.kotlite.KotliteInterpreter
import com.sunnychung.lib.multiplatform.kotlite.Interpreter
import com.sunnychung.lib.multiplatform.kotlite.model.BooleanValue
import com.sunnychung.lib.multiplatform.kotlite.model.DoubleValue
import com.sunnychung.lib.multiplatform.kotlite.model.ExecutionEnvironment
import com.sunnychung.lib.multiplatform.kotlite.model.GlobalProperty
import com.sunnychung.lib.multiplatform.kotlite.model.IntValue
import com.sunnychung.lib.multiplatform.kotlite.model.LongValue
import com.sunnychung.lib.multiplatform.kotlite.model.RuntimeValue
import com.sunnychung.lib.multiplatform.kotlite.model.SourcePosition
import com.sunnychung.lib.multiplatform.kotlite.stdlib.AllStdLibModules
import de.fabmax.kool.math.Vec3f
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationExpression
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationVectorExpression

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
    private val interpreter: Interpreter? by lazy {
        runCatching { createInterpreter() }
            .onFailure { logFailure("compile", it) }
            .getOrNull()
    }

    fun float(context: AnimatorEvaluationContext, default: Float): Float =
        fastExpression?.float(context) ?: (eval(context)?.asFloat(default) ?: default)

    fun boolean(context: AnimatorEvaluationContext, default: Boolean): Boolean =
        fastExpression?.boolean(context) ?: (eval(context)?.asBoolean(default) ?: default)

    @Synchronized
    private fun eval(context: AnimatorEvaluationContext): RuntimeValue? {
        updateVariables(context)
        val compiled = interpreter ?: return null
        return runCatching { compiled.eval() }
            .onFailure { logFailure("evaluate", it) }
            .getOrNull()
    }

    private fun createInterpreter(): Interpreter {
        val environment = ExecutionEnvironment().apply {
            install(AllStdLibModules { message -> HollowEngine.LOGGER.info(message) })
            variableNames.forEach { name ->
                registerGlobalProperty(
                    GlobalProperty(
                        SOURCE_POSITION,
                        name,
                        "Double",
                        true,
                        { interpreter -> DoubleValue(variableValues[name] ?: 0.0, interpreter.symbolTable()) },
                        { _, value -> variableValues[name] = value.asFloat(0f).toDouble() },
                    )
                )
            }
        }
        return KotliteInterpreter(
            filename = "animation-expression.kts",
            code = source,
            executionEnvironment = environment,
        )
    }

    private fun updateVariables(context: AnimatorEvaluationContext) {
        variableNames.forEach { name ->
            val value = context.katariValue(name).toDouble()
            variableValues[name] = value
        }
    }

    private fun logFailure(stage: String, throwable: Throwable) {
        if (failureLogged) return
        failureLogged = true
        HollowEngine.LOGGER.warn("Animation Katari expression `{}` failed to {}", source, stage, throwable)
    }

    private fun AnimatorEvaluationContext.katariValue(name: String): Float =
        when (name) {
            "delta_time" -> deltaTime
            "time", "anim_time" -> time
            else -> values[name] ?: 0f
        }

    private fun RuntimeValue.asFloat(default: Float): Float =
        when (this) {
            is DoubleValue -> value.toFloat()
            is IntValue -> value.toFloat()
            is LongValue -> value.toFloat()
            is BooleanValue -> if (value) 1f else 0f
            else -> convertToString().toFloatOrNull() ?: default
        }

    private fun RuntimeValue.asBoolean(default: Boolean): Boolean =
        when (this) {
            is BooleanValue -> value
            is DoubleValue -> value != 0.0
            is IntValue -> value != 0
            is LongValue -> value != 0L
            else -> convertToString().toBooleanStrictOrNull() ?: default
        }

    companion object {
        private val SOURCE_POSITION = SourcePosition("animation-expression.kts", 1, 1)
    }
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
