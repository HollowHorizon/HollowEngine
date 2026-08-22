package ru.hollowhorizon.hollowengine.common.utils.expressions

/**
 * The typed tree the resolver produces and both backends consume.
 */
internal sealed interface Ir {
    val type: ExprType

    /** The value of this subtree when it does not depend on the context, otherwise null. */
    val constant: Any? get() = null

    class Const(override val type: ExprType, override val constant: Any?) : Ir

    class FieldRead(val owner: Ir?, val member: Member.Field) : Ir {
        override val type: ExprType get() = member.type
    }

    class MethodCall(val owner: Ir?, val method: Member.Method, val arguments: List<Ir>) : Ir {
        override val type: ExprType get() = method.type
    }

    class BagRead(val owner: Ir, val bag: ExprType.Dynamic, val key: String) : Ir {
        override val type: ExprType get() = bag.valueType
    }

    class BagWrite(val owner: Ir, val bag: ExprType.Dynamic, val key: String, val value: Ir) : Ir {
        override val type: ExprType get() = value.type
    }

    class Negate(val operand: Ir) : Ir {
        override val type: ExprType get() = ExprType.Primitive.FLOAT
    }

    class Not(val operand: Ir) : Ir {
        override val type: ExprType get() = ExprType.Primitive.BOOL
    }

    class Arithmetic(val op: BinaryOp, val left: Ir, val right: Ir) : Ir {
        override val type: ExprType get() = ExprType.Primitive.FLOAT
    }

    class Compare(val op: BinaryOp, val left: Ir, val right: Ir) : Ir {
        override val type: ExprType get() = ExprType.Primitive.BOOL
    }

    class Logical(val op: BinaryOp, val left: Ir, val right: Ir) : Ir {
        override val type: ExprType get() = ExprType.Primitive.BOOL
    }

    class ObjectEquals(val left: Ir, val right: Ir, val negate: Boolean) : Ir {
        override val type: ExprType get() = ExprType.Primitive.BOOL
    }

    /** `a + b` where the type decides what that means. */
    class Operation(val op: BinaryOp, val left: Ir, val right: Ir, val operator: Operator) : Ir {
        override val type: ExprType get() = operator.type
    }

    class Coalesce(val left: Ir, val right: Ir) : Ir {
        override val type: ExprType get() = right.type
    }

    class Conditional(val condition: Ir, val ifTrue: Ir, val ifFalse: Ir, override val type: ExprType) : Ir

    class Sequence(val statements: List<Ir>) : Ir {
        override val type: ExprType get() = statements.last().type
    }

    class ListOf(
        val items: List<Ir>,
        override val type: ExprType,
        val build: ((List<Any?>) -> Any?)? = null,
    ) : Ir

    class Index(val target: Ir, val index: Ir, override val type: ExprType) : Ir

    class Convert(val value: Ir, override val type: ExprType) : Ir
}

internal fun Ir.constantFloat(): Float? = (constant as? Number)?.toFloat()

internal fun Ir.constantBool(): Boolean? = when (val value = constant) {
    is Boolean -> value
    is Number -> value.toFloat() != 0f
    else -> null
}

private fun constFloat(value: Float) = Ir.Const(ExprType.Primitive.FLOAT, value)
private fun constBool(value: Boolean) = Ir.Const(ExprType.Primitive.BOOL, value)

internal object Optimizer {
    fun arithmetic(op: BinaryOp, left: Ir, right: Ir): Ir {
        val a = left.constantFloat()
        val b = right.constantFloat()
        if (a != null && b != null) return constFloat(apply(op, a, b))

        if (b != null) when (op) {
            BinaryOp.ADD, BinaryOp.SUBTRACT -> if (b == 0f) return left
            BinaryOp.MULTIPLY -> if (b == 1f) return left else if (b == 0f) return constFloat(0f)
            BinaryOp.DIVIDE -> if (b == 1f) return left
            else -> Unit
        }
        if (a != null) when (op) {
            BinaryOp.ADD -> if (a == 0f) return right
            BinaryOp.MULTIPLY -> if (a == 1f) return right else if (a == 0f) return constFloat(0f)
            else -> Unit
        }

        if (b != null && left is Ir.Arithmetic && left.right.constantFloat() != null) {
            val inner = left.right.constantFloat()!!
            if (op == BinaryOp.DIVIDE && left.op == BinaryOp.DIVIDE) {
                return Ir.Arithmetic(BinaryOp.DIVIDE, left.left, constFloat(inner * b))
            }
            if (op == BinaryOp.MULTIPLY && left.op == BinaryOp.MULTIPLY) {
                return Ir.Arithmetic(BinaryOp.MULTIPLY, left.left, constFloat(inner * b))
            }
        }

        return Ir.Arithmetic(op, left, right)
    }

    fun compare(op: BinaryOp, left: Ir, right: Ir): Ir {
        val a = left.constantFloat()
        val b = right.constantFloat()
        if (a != null && b != null) {
            return constBool(
                when (op) {
                    BinaryOp.EQ -> a == b
                    BinaryOp.NEQ -> a != b
                    BinaryOp.LT -> a < b
                    BinaryOp.GT -> a > b
                    BinaryOp.LTE -> a <= b
                    else -> a >= b
                }
            )
        }
        return Ir.Compare(op, left, right)
    }

    fun logical(op: BinaryOp, left: Ir, right: Ir): Ir {
        val a = left.constantBool()
        val b = right.constantBool()
        if (a != null && b != null) return constBool(if (op == BinaryOp.AND) a && b else a || b)

        if (a != null) {
            return if (op == BinaryOp.AND) {
                if (a) right else constBool(false)
            } else {
                if (a) constBool(true) else right
            }
        }
        if (b != null) {
            if (op == BinaryOp.AND && b) return left
            if (op == BinaryOp.OR && !b) return left
        }
        return Ir.Logical(op, left, right)
    }

    fun not(operand: Ir): Ir = operand.constantBool()?.let { constBool(!it) } ?: Ir.Not(operand)

    fun negate(operand: Ir): Ir = operand.constantFloat()?.let { constFloat(-it) } ?: Ir.Negate(operand)

    fun conditional(condition: Ir, ifTrue: Ir, ifFalse: Ir, type: ExprType): Ir =
        when (condition.constantBool()) {
            true -> ifTrue
            false -> ifFalse
            null -> Ir.Conditional(condition, ifTrue, ifFalse, type)
        }

    fun convert(value: Ir, type: ExprType): Ir {
        if (value.type === type) return value
        return when (type) {
            ExprType.Primitive.FLOAT -> value.constantFloat()?.let(::constFloat) ?: Ir.Convert(value, type)
            ExprType.Primitive.BOOL -> value.constantBool()?.let(::constBool) ?: Ir.Convert(value, type)
            else -> Ir.Convert(value, type)
        }
    }

    fun call(method: Member.Method, owner: Ir?, arguments: List<Ir>): Ir {
        if (method.isAllFloat && method.isPure && arguments.all { it.constantFloat() != null }) {
            val values = arguments.map { it.constantFloat()!! }
            val result = when (values.size) {
                1 -> method.float1?.invoke(values[0])
                2 -> method.float2?.invoke(values[0], values[1])
                3 -> method.float3?.invoke(values[0], values[1], values[2])
                else -> null
            }
            if (result != null) return constFloat(result)
        }
        return Ir.MethodCall(owner, method, arguments)
    }

    private fun apply(op: BinaryOp, a: Float, b: Float): Float = when (op) {
        BinaryOp.ADD -> a + b
        BinaryOp.SUBTRACT -> a - b
        BinaryOp.MULTIPLY -> a * b
        BinaryOp.DIVIDE -> a / b
        else -> a % b
    }
}

internal val Member.Method.isPure: Boolean get() = !name.startsWith("random")
