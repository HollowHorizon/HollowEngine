package ru.hollowhorizon.hollowengine.common.utils.expressions

/**
 * Lowers [Ir] into closures.
 *
 * Every node is turned into a call chain once, at bake time, so evaluating walks closures rather than
 * the tree. Primitive nodes lower into [FloatExpression] and [BoolExpression].
 */
internal object Interpreter {
    fun floats(ir: Ir): FloatExpression<Any?> = when (ir) {
        is Ir.Const -> Coercions.toFloat(ir.constant).let { value -> FloatExpression { value } }

        is Ir.FieldRead -> when (val field = ir.member) {
            is Member.FloatField -> {
                val owner = ir.owner?.let(::objects)
                if (owner == null) FloatExpression { field.read.read(it) }
                else FloatExpression { field.read.read(owner.eval(it)) }
            }

            is Member.BoolField -> {
                val bool = bools(ir)
                FloatExpression { if (bool.eval(it)) 1f else 0f }
            }

            is Member.ObjectField -> toFloat(field.type, objects(ir))
        }

        is Ir.MethodCall -> floatCall(ir)

        is Ir.Negate -> {
            val operand = floats(ir.operand)
            FloatExpression { -operand.eval(it) }
        }

        is Ir.Arithmetic -> {
            val a = floats(ir.left)
            val b = floats(ir.right)
            when (ir.op) {
                BinaryOp.ADD -> FloatExpression { a.eval(it) + b.eval(it) }
                BinaryOp.SUBTRACT -> FloatExpression { a.eval(it) - b.eval(it) }
                BinaryOp.MULTIPLY -> FloatExpression { a.eval(it) * b.eval(it) }
                BinaryOp.DIVIDE -> FloatExpression { a.eval(it) / b.eval(it) }
                else -> FloatExpression { a.eval(it) % b.eval(it) }
            }
        }

        is Ir.Conditional -> {
            val condition = bools(ir.condition)
            val ifTrue = floats(ir.ifTrue)
            val ifFalse = floats(ir.ifFalse)
            FloatExpression { if (condition.eval(it)) ifTrue.eval(it) else ifFalse.eval(it) }
        }

        is Ir.Sequence -> {
            val leading = ir.statements.dropLast(1).map(::objects)
            val last = floats(ir.statements.last())
            FloatExpression { context ->
                leading.forEach { it.eval(context) }
                last.eval(context)
            }
        }

        is Ir.Convert -> when (ir.value.type) {
            ExprType.Primitive.BOOL -> {
                val value = bools(ir.value)
                FloatExpression { if (value.eval(it)) 1f else 0f }
            }

            else -> toFloat(ir.value.type, objects(ir.value))
        }

        else -> if (ir.type === ExprType.Primitive.BOOL) {
            val value = bools(ir)
            FloatExpression { if (value.eval(it)) 1f else 0f }
        } else {
            toFloat(ir.type, objects(ir))
        }
    }

    fun bools(ir: Ir): BoolExpression<Any?> = when (ir) {
        is Ir.Const -> when (val value = ir.constant) {
            is Boolean -> BoolExpression { value }
            is Number -> (value.toFloat() != 0f).let { constant -> BoolExpression { constant } }
            else -> Coercions.toBool(value).let { constant -> BoolExpression { constant } }
        }

        is Ir.FieldRead -> when (val field = ir.member) {
            is Member.BoolField -> {
                val owner = ir.owner?.let(::objects)
                if (owner == null) BoolExpression { field.read.read(it) }
                else BoolExpression { field.read.read(owner.eval(it)) }
            }

            is Member.FloatField -> {
                val value = floats(ir)
                BoolExpression { value.eval(it) != 0f }
            }

            is Member.ObjectField -> toBool(field.type, objects(ir))
        }

        is Ir.Not -> {
            val operand = bools(ir.operand)
            BoolExpression { !operand.eval(it) }
        }

        is Ir.Compare -> {
            val a = floats(ir.left)
            val b = floats(ir.right)
            when (ir.op) {
                BinaryOp.EQ -> BoolExpression { a.eval(it) == b.eval(it) }
                BinaryOp.NEQ -> BoolExpression { a.eval(it) != b.eval(it) }
                BinaryOp.LT -> BoolExpression { a.eval(it) < b.eval(it) }
                BinaryOp.GT -> BoolExpression { a.eval(it) > b.eval(it) }
                BinaryOp.LTE -> BoolExpression { a.eval(it) <= b.eval(it) }
                else -> BoolExpression { a.eval(it) >= b.eval(it) }
            }
        }

        is Ir.Logical -> {
            val a = bools(ir.left)
            val b = bools(ir.right)
            if (ir.op == BinaryOp.AND) BoolExpression { a.eval(it) && b.eval(it) }
            else BoolExpression { a.eval(it) || b.eval(it) }
        }

        is Ir.ObjectEquals -> {
            val a = objects(ir.left)
            val b = objects(ir.right)
            val negate = ir.negate
            BoolExpression { (a.eval(it) == b.eval(it)) != negate }
        }

        is Ir.Conditional -> {
            val condition = bools(ir.condition)
            val ifTrue = bools(ir.ifTrue)
            val ifFalse = bools(ir.ifFalse)
            BoolExpression { if (condition.eval(it)) ifTrue.eval(it) else ifFalse.eval(it) }
        }

        is Ir.Sequence -> {
            val leading = ir.statements.dropLast(1).map(::objects)
            val last = bools(ir.statements.last())
            BoolExpression { context ->
                leading.forEach { it.eval(context) }
                last.eval(context)
            }
        }

        is Ir.Convert -> when (ir.value.type) {
            ExprType.Primitive.FLOAT -> {
                val value = floats(ir.value)
                BoolExpression { value.eval(it) != 0f }
            }

            else -> toBool(ir.value.type, objects(ir.value))
        }

        else -> if (ir.type === ExprType.Primitive.FLOAT) {
            val value = floats(ir)
            BoolExpression { value.eval(it) != 0f }
        } else {
            toBool(ir.type, objects(ir))
        }
    }

    fun objects(ir: Ir): ObjectExpression<Any?> = when (ir) {
        is Ir.Const -> ir.constant.let { value -> ObjectExpression { value } }

        is Ir.FieldRead -> when (val field = ir.member) {
            is Member.ObjectField -> {
                val owner = ir.owner?.let(::objects)
                if (owner == null) ObjectExpression { field.read.read(it) }
                else ObjectExpression { field.read.read(owner.eval(it)) }
            }

            is Member.FloatField -> floats(ir).let { value -> ObjectExpression { value.eval(it) } }
            is Member.BoolField -> bools(ir).let { value -> ObjectExpression { value.eval(it) } }
        }

        is Ir.MethodCall -> if (ir.type === ExprType.Primitive.FLOAT) {
            floats(ir).let { value -> ObjectExpression { value.eval(it) } }
        } else {
            genericCall(ir)
        }

        is Ir.BagRead -> {
            val owner = objects(ir.owner)
            val bag = ir.bag
            val key = ir.key
            ObjectExpression { bag.read(owner.eval(it), key) }
        }

        is Ir.BagWrite -> {
            val owner = objects(ir.owner)
            val value = objects(ir.value)
            val bag = ir.bag
            val write = bag.write!!
            val key = ir.key
            ObjectExpression { context ->
                val evaluated = value.eval(context)
                write(owner.eval(context), key, evaluated)
                evaluated
            }
        }

        is Ir.ListOf -> {
            val items = ir.items.map(::objects)
            val build = ir.build
            if (build == null) ObjectExpression { context -> items.map { it.eval(context) } }
            else ObjectExpression { context -> build(items.map { it.eval(context) }) }
        }

        is Ir.Index -> {
            val target = objects(ir.target)
            val index = floats(ir.index)
            ObjectExpression { context ->
                (target.eval(context) as? List<*>)?.getOrNull(index.eval(context).toInt())
            }
        }

        is Ir.Coalesce -> {
            val a = objects(ir.left)
            val b = objects(ir.right)
            ObjectExpression { a.eval(it) ?: b.eval(it) }
        }

        is Ir.Operation -> {
            val a = objects(ir.left)
            val b = objects(ir.right)
            val invoke = ir.operator.invoke
            ObjectExpression { invoke(a.eval(it), b.eval(it)) }
        }

        is Ir.Conditional -> {
            val condition = bools(ir.condition)
            val ifTrue = objects(ir.ifTrue)
            val ifFalse = objects(ir.ifFalse)
            ObjectExpression { if (condition.eval(it)) ifTrue.eval(it) else ifFalse.eval(it) }
        }

        is Ir.Sequence -> {
            val statements = ir.statements.map(::objects)
            ObjectExpression { context ->
                var result: Any? = null
                statements.forEach { result = it.eval(context) }
                result
            }
        }

        is Ir.Negate, is Ir.Arithmetic -> floats(ir).let { value -> ObjectExpression { value.eval(it) } }
        is Ir.Not, is Ir.Compare, is Ir.Logical, is Ir.ObjectEquals ->
            bools(ir).let { value -> ObjectExpression { value.eval(it) } }

        is Ir.Convert -> when (ir.type) {
            ExprType.Primitive.FLOAT -> floats(ir).let { value -> ObjectExpression { value.eval(it) } }
            ExprType.Primitive.BOOL -> bools(ir).let { value -> ObjectExpression { value.eval(it) } }
            else -> objects(ir.value)
        }
    }

    private fun toFloat(type: ExprType, value: ObjectExpression<Any?>): FloatExpression<Any?> {
        val convert = (type as? ExprType.Reference)?.number
            ?: return FloatExpression { Coercions.toFloat(value.eval(it)) }
        return FloatExpression { convert.convert(value.eval(it)) }
    }

    private fun toBool(type: ExprType, value: ObjectExpression<Any?>): BoolExpression<Any?> {
        val convert = (type as? ExprType.Reference)?.truthy
            ?: return BoolExpression { Coercions.toBool(value.eval(it)) }
        return BoolExpression { convert.convert(value.eval(it)) }
    }

    private fun floatCall(ir: Ir.MethodCall): FloatExpression<Any?> {
        val method = ir.method
        val arguments = ir.arguments.map(::floats)
        method.float1?.let { body ->
            val a = arguments[0]
            return FloatExpression { body.invoke(a.eval(it)) }
        }
        method.float2?.let { body ->
            val a = arguments[0]
            val b = arguments[1]
            return FloatExpression { body.invoke(a.eval(it), b.eval(it)) }
        }
        method.float3?.let { body ->
            val a = arguments[0]
            val b = arguments[1]
            val c = arguments[2]
            return FloatExpression { body.invoke(a.eval(it), b.eval(it), c.eval(it)) }
        }
        return toFloat(method.type, genericCall(ir))
    }

    private fun genericCall(ir: Ir.MethodCall): ObjectExpression<Any?> {
        val owner = ir.owner?.let(::objects)
        val arguments = ir.arguments.map(::objects)
        val invoke = ir.method.generic
        return ObjectExpression { context ->
            val values = arrayOfNulls<Any?>(arguments.size)
            arguments.forEachIndexed { index, argument -> values[index] = argument.eval(context) }
            invoke.invoke(owner?.eval(context), values)
        }
    }
}

internal object Coercions {
    @JvmStatic
    fun toFloat(value: Any?): Float = when (value) {
        is Number -> value.toFloat()
        is Boolean -> if (value) 1f else 0f
        else -> 0f
    }

    @JvmStatic
    fun toBool(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toFloat() != 0f
        is CharSequence -> value.isNotEmpty()
        is Collection<*> -> value.isNotEmpty()
        else -> value != null
    }
}
