package ru.hollowhorizon.hollowengine.common.dialogue.lang

import ru.hollowhorizon.hollowengine.common.dialogue.EntityCharacter
import ru.hollowhorizon.hollowengine.common.dialogue.StoryActor
import ru.hollowhorizon.hollowengine.common.dialogue.StoryBool
import ru.hollowhorizon.hollowengine.common.dialogue.StoryList
import ru.hollowhorizon.hollowengine.common.dialogue.StoryNumber
import ru.hollowhorizon.hollowengine.common.dialogue.StoryString
import ru.hollowhorizon.hollowengine.common.dialogue.StoryValue
import ru.hollowhorizon.hollowengine.common.dialogue.isTruthy

/** Expression tree of `@if`/`@while`/`@set`, `if=` parameters and `{...}` interpolation. */
sealed interface StoryExpr {
    val span: StorySpan

    data class Lit(val value: StoryValue, override val span: StorySpan) : StoryExpr
    data class VarRef(val name: String, override val span: StorySpan) : StoryExpr
    data class Unary(val op: UnaryOp, val operand: StoryExpr, override val span: StorySpan) : StoryExpr
    data class Binary(val op: BinaryOp, val left: StoryExpr, val right: StoryExpr, override val span: StorySpan) : StoryExpr
    data class ListLit(val items: List<StoryExpr>, override val span: StorySpan) : StoryExpr

    /** `items[0]`: an element of a list. */
    data class Index(val target: StoryExpr, val index: StoryExpr, override val span: StorySpan) : StoryExpr

    /** `items.size`, `Vitalik.uuid`: what a value can be asked about without calling anything. */
    data class Property(val target: StoryExpr, val name: String, override val span: StorySpan) : StoryExpr
}

enum class UnaryOp { NOT, NEG }

enum class BinaryOp(val symbol: String) {
    OR("||"), AND("&&"),
    EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="),
    ADD("+"), SUB("-"), MUL("*"), DIV("/"), MOD("%"),
}

/** Thrown by [evaluate] for type errors; carries the span of the failing subexpression. */
class StoryEvalException(message: String, val span: StorySpan) : RuntimeException(message)

fun StoryExpr.evaluate(variables: (String) -> StoryValue?): StoryValue = when (this) {
    is StoryExpr.Lit -> value
    is StoryExpr.ListLit -> StoryList(items.map { it.evaluate(variables) })
    is StoryExpr.VarRef -> variables(name)
        ?: throw StoryEvalException("Unknown variable '$name'", span)

    is StoryExpr.Unary -> when (op) {
        UnaryOp.NOT -> StoryBool(!operand.evaluate(variables).isTruthy())
        UnaryOp.NEG -> {
            val v = operand.evaluate(variables)
            if (v is StoryNumber) StoryNumber(-v.value)
            else throw StoryEvalException("Cannot negate ${v.typeName()}", span)
        }
    }

    is StoryExpr.Binary -> evaluateBinary(variables)

    is StoryExpr.Index -> {
        val list = target.evaluate(variables) as? StoryList
            ?: throw StoryEvalException("Only a list can be indexed", span)
        val position = (index.evaluate(variables) as? StoryNumber)?.value?.toInt()
            ?: throw StoryEvalException("A list index must be a number", span)
        list.values.getOrNull(position)
            ?: throw StoryEvalException("No element $position: the list holds ${list.values.size}", span)
    }

    is StoryExpr.Property -> propertyOf(target.evaluate(variables), name, span)
}

/** What a value answers without being called: sizes, and the pieces of a character. */
private fun propertyOf(value: StoryValue, name: String, span: StorySpan): StoryValue = when {
    value is StoryList && name == "size" -> StoryNumber(value.values.size.toFloat())
    value is StoryString && name == "size" -> StoryNumber(value.value.length.toFloat())
    value is StoryActor && name == "name" -> StoryString(value.character.name)
    value is StoryActor && name == "uuid" -> value.uuid()
        ?: throw StoryEvalException("'${value.character.name}' is a name without an entity, so it has no uuid", span)

    else -> throw StoryEvalException("A ${value.typeName()} has no '$name'", span)
}

private fun StoryActor.uuid(): StoryValue? =
    (character as? EntityCharacter)?.let { StoryString(it.entity.uuid.toString()) }

private fun StoryExpr.Binary.evaluateBinary(variables: (String) -> StoryValue?): StoryValue {
    when (op) {
        BinaryOp.OR -> return StoryBool(left.evaluate(variables).isTruthy() || right.evaluate(variables).isTruthy())
        BinaryOp.AND -> return StoryBool(left.evaluate(variables).isTruthy() && right.evaluate(variables).isTruthy())
        else -> {}
    }
    val l = left.evaluate(variables)
    val r = right.evaluate(variables)
    return when (op) {
        BinaryOp.EQ -> StoryBool(valuesEqual(l, r))
        BinaryOp.NE -> StoryBool(!valuesEqual(l, r))
        BinaryOp.LT -> StoryBool(compare(l, r) < 0)
        BinaryOp.LE -> StoryBool(compare(l, r) <= 0)
        BinaryOp.GT -> StoryBool(compare(l, r) > 0)
        BinaryOp.GE -> StoryBool(compare(l, r) >= 0)
        BinaryOp.ADD -> when {
            l is StoryList && r is StoryList -> StoryList(l.values + r.values)
            l is StoryList -> StoryList(l.values + r)
            r is StoryList -> StoryList(listOf(l) + r.values)
            l is StoryString || r is StoryString -> StoryString(l.display() + r.display())
            else -> StoryNumber(numeric(l) + numeric(r))
        }

        BinaryOp.SUB -> StoryNumber(numeric(l) - numeric(r))
        BinaryOp.MUL -> StoryNumber(numeric(l) * numeric(r))
        BinaryOp.DIV -> StoryNumber(numeric(l) / numeric(r))
        BinaryOp.MOD -> StoryNumber(numeric(l) % numeric(r))
        BinaryOp.OR, BinaryOp.AND -> error("unreachable")
    }
}

private fun StoryExpr.Binary.numeric(v: StoryValue): Float =
    (v as? StoryNumber)?.value
        ?: throw StoryEvalException("Operator '${op.symbol}' expects a number, got ${v.typeName()}", span)

private fun StoryExpr.Binary.compare(l: StoryValue, r: StoryValue): Int = when {
    l is StoryNumber && r is StoryNumber -> l.value.compareTo(r.value)
    l is StoryString && r is StoryString -> l.value.compareTo(r.value)
    else -> throw StoryEvalException(
        "Cannot compare ${l.typeName()} with ${r.typeName()} using '${op.symbol}'", span,
    )
}

private fun valuesEqual(l: StoryValue, r: StoryValue): Boolean = when {
    l is StoryNumber && r is StoryNumber -> l.value == r.value
    l is StoryString && r is StoryString -> l.value == r.value
    l is StoryBool && r is StoryBool -> l.value == r.value
    l is StoryList && r is StoryList ->
        l.values.size == r.values.size && l.values.zip(r.values).all { (a, b) -> valuesEqual(a, b) }

    else -> false
}

internal fun StoryValue.typeName(): String = when (this) {
    is StoryString -> "string"
    is StoryNumber -> "number"
    is StoryBool -> "bool"
    is StoryList -> "list"
    is StoryActor -> "character"
}
