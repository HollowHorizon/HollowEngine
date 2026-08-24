package ru.hollowhorizon.hollowengine.common.utils.expressions

/**
 * A type in the expression language.
 *
 * [Primitive] types map onto JVM primitives and are the only ones the bytecode backend can emit.
 * [Struct] types have named members and never exist as a value at runtime: an expression of a struct
 * type is resolved down to its members, so `q.velocity.x` reads one float and no vector is built.
 * [Reference] types are ordinary JVM objects and are available to the interpreter only.
 */
sealed interface ExprType {
    val name: String
    val members: Members

    sealed class Primitive(override val name: String) : ExprType {
        override val members: Members get() = Members.EMPTY

        data object FLOAT : Primitive("float")
        data object BOOL : Primitive("bool")
    }

    class Struct(
        override val name: String,
        override val members: Members,
    ) : ExprType

    class Reference(
        override val name: String,
        members: Members,
        val truthy: ObjectToBool? = null,
        val number: ObjectToFloat? = null,
    ) : ExprType {
        override var members: Members = members
            internal set
    }

    class Dynamic(
        override val name: String,
        val valueType: ExprType,
        val read: (owner: Any?, key: String) -> Any?,
        val write: ((owner: Any?, key: String, value: Any?) -> Unit)? = null,
        val nested: Boolean = false,
    ) : ExprType {
        override val members: Members get() = Members.EMPTY
    }
}

fun interface FloatReader {
    fun read(owner: Any?): Float
}

fun interface BoolReader {
    fun read(owner: Any?): Boolean
}

fun interface ObjectReader {
    fun read(owner: Any?): Any?
}

/** Float functions by arity, so a call passes and returns primitives with no array and no boxing. */
fun interface Float1 {
    fun invoke(a: Float): Float
}

fun interface Float2 {
    fun invoke(a: Float, b: Float): Float
}

fun interface Float3 {
    fun invoke(a: Float, b: Float, c: Float): Float
}

/** The fallback for anything that is not all-float, such as a method taking a list. */
fun interface GenericInvoker {
    fun invoke(owner: Any?, arguments: Array<Any?>): Any?
}

fun interface ObjectToBool {
    fun convert(value: Any?): Boolean
}

fun interface ObjectToFloat {
    fun convert(value: Any?): Float
}

/**
 * What `a + b` means when the operands are not numbers.
 */
class Operator(val type: ExprType, val invoke: (Any?, Any?) -> Any?)

/** Something reachable through `.` on a value of some type. */
sealed interface Member {
    val name: String
    val alias: String?
    val type: ExprType

    val names: List<String> get() = listOfNotNull(name, alias)

    sealed interface Field : Member

    class FloatField(
        override val name: String,
        override val alias: String?,
        val read: FloatReader,
    ) : Field {
        override val type: ExprType get() = ExprType.Primitive.FLOAT
    }

    class BoolField(
        override val name: String,
        override val alias: String?,
        val read: BoolReader,
    ) : Field {
        override val type: ExprType get() = ExprType.Primitive.BOOL
    }

    class ObjectField(
        override val name: String,
        override val type: ExprType,
        override val alias: String?,
        val read: ObjectReader,
    ) : Field

    class Method(
        override val name: String,
        override val type: ExprType,
        val parameters: List<ExprType>,
        override val alias: String?,
        val generic: GenericInvoker,
        val float1: Float1? = null,
        val float2: Float2? = null,
        val float3: Float3? = null,
    ) : Member {
        val isAllFloat: Boolean get() = float1 != null || float2 != null || float3 != null

        val signature: String
            get() = "$name(${parameters.joinToString { it.name }}): ${type.name}"
    }
}

class Members internal constructor(
    private val fields: Map<String, Member.Field>,
    private val methods: Map<String, List<Member.Method>>,
    private val operators: Map<BinaryOp, Operator> = emptyMap(),
) {
    val allFields: Collection<Member.Field> get() = fields.values.distinct()
    val allMethods: Collection<Member.Method> get() = methods.values.flatten().distinct()

    fun field(name: String): Member.Field? = fields[name]

    fun operator(op: BinaryOp): Operator? = operators[op]

    fun methods(name: String): List<Member.Method> = methods[name].orEmpty()

    fun method(name: String, arguments: List<ExprType>, casts: Casts): Overloads {
        val byArity = methods(name).filter { it.parameters.size == arguments.size }
        if (byArity.isEmpty()) return Overloads(null, emptyList())

        val scored = byArity.mapNotNull { candidate ->
            val cost = conversionCost(candidate.parameters, arguments, casts) ?: return@mapNotNull null
            candidate to cost
        }
        if (scored.isEmpty()) return Overloads(null, emptyList())

        val best = scored.minOf { it.second }
        val winners = scored.filter { it.second == best }.map { it.first }
        return if (winners.size == 1) Overloads(winners.single(), emptyList()) else Overloads(null, winners)
    }

    private fun conversionCost(parameters: List<ExprType>, arguments: List<ExprType>, casts: Casts): Int? {
        var cost = 0
        parameters.forEachIndexed { index, parameter ->
            val argument = arguments[index]
            when {
                parameter === argument -> Unit
                casts == Casts.IMPLICIT && parameter.isPrimitive && argument.isPrimitive -> cost++
                else -> return null
            }
        }
        return cost
    }

    class Overloads(val match: Member.Method?, val ambiguous: List<Member.Method>)

    companion object {
        val EMPTY = Members(emptyMap(), emptyMap(), emptyMap())
    }
}

val ExprType.isPrimitive: Boolean get() = this is ExprType.Primitive
