package ru.hollowhorizon.hollowengine.common.utils.expressions

/** Parsed expression, before resolved names or declarations. */
sealed interface Ast {
    val span: Span

    data class NumberLit(val value: Float, override val span: Span) : Ast
    data class BoolLit(val value: Boolean, override val span: Span) : Ast
    data class StringLit(val value: String, override val span: Span) : Ast
    data class ListLit(val items: List<Ast>, override val span: Span) : Ast
    data class Name(val name: String, override val span: Span) : Ast
    data class Access(val target: Ast, val name: String, val nameSpan: Span, override val span: Span) : Ast
    data class Index(val target: Ast, val index: Ast, override val span: Span) : Ast
    data class Call(val target: Ast?, val name: String, val arguments: List<Ast>, override val span: Span) : Ast
    data class Unary(val op: UnaryOp, val operand: Ast, override val span: Span) : Ast
    data class Binary(val op: BinaryOp, val left: Ast, val right: Ast, override val span: Span) : Ast
    data class Conditional(val condition: Ast, val ifTrue: Ast, val ifFalse: Ast, override val span: Span) : Ast
    data class Assign(val target: Ast, val value: Ast, override val span: Span) : Ast
    /** List of statements: `a = 1; b = 2; a + b`. */
    data class Sequence(val statements: List<Ast>, override val span: Span) : Ast
}

enum class UnaryOp(val symbol: String) {
    NOT("!"),
    NEGATE("-"),
}

enum class BinaryOp(val symbol: String) {
    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    REMAINDER("%"),
    EQ("=="),
    NEQ("!="),
    LT("<"),
    GT(">"),
    LTE("<="),
    GTE(">="),
    AND("&&"),
    OR("||"),
    COALESCE("??"),

    /** Never parsed as an infix symbol: `a[i]` looks this up so indexing is declarable like any operator. */
    INDEX("[]"),
}

/** Visits every node, parents before children. Editors use it to find what a name refers to. */
fun Ast.walk(action: (Ast) -> Unit) {
    action(this)
    when (this) {
        is Ast.Unary -> operand.walk(action)
        is Ast.Binary -> {
            left.walk(action)
            right.walk(action)
        }

        is Ast.ListLit -> items.forEach { it.walk(action) }
        is Ast.Index -> {
            target.walk(action)
            index.walk(action)
        }

        is Ast.Access -> target.walk(action)
        is Ast.Call -> {
            this.target?.walk(action)
            arguments.forEach { it.walk(action) }
        }

        is Ast.Conditional -> {
            condition.walk(action)
            ifTrue.walk(action)
            ifFalse.walk(action)
        }

        is Ast.Assign -> {
            target.walk(action)
            value.walk(action)
        }

        is Ast.Sequence -> statements.forEach { it.walk(action) }
        is Ast.NumberLit, is Ast.BoolLit, is Ast.StringLit, is Ast.Name -> Unit
    }
}
