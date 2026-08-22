import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.common.utils.expressions.Casts
import ru.hollowhorizon.hollowengine.common.utils.expressions.Declarations
import ru.hollowhorizon.hollowengine.common.utils.expressions.ExprType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Picking an overload by signature: arity first, then an exact match ahead of a converted one. */
class ExpressionOverloadTests {
    private val float = ExprType.Primitive.FLOAT
    private val bool = ExprType.Primitive.BOOL

    private val math = Declarations<Any?> {
        struct<Any?>("math") {
            method("clamp", float, float, float, float) { _, _ -> 0f }
            method("clamp", float, float, float) { _, _ -> 0f }
            method("pick", float, float) { _, _ -> 0f }
            method("pick", float, bool) { _, _ -> 1f }
        }
    }.types.getValue("math")

    private fun resolve(name: String, vararg arguments: ExprType, casts: Casts = Casts.IMPLICIT) =
        math.members.method(name, arguments.toList(), casts)

    @Test
    fun `arity picks between overloads of the same name`() {
        assertEquals(2, resolve("clamp", float, float).match?.parameters?.size)
        assertEquals(3, resolve("clamp", float, float, float).match?.parameters?.size)
    }

    @Test
    fun `no overload of that arity resolves to nothing`() {
        val result = resolve("clamp", float)

        assertNull(result.match)
        assertTrue(result.ambiguous.isEmpty())
    }

    @Test
    fun `an exact match wins over one that needs a conversion`() {
        val result = resolve("pick", bool)

        assertEquals(listOf(bool), result.match?.parameters)
    }

    @Test
    fun `a conversion is used when nothing matches exactly`() {
        val declarations = Declarations<Any?> {
            struct<Any?>("only") { method("f", float, bool) { _, _ -> 0f } }
        }.types.getValue("only")

        val result = declarations.members.method("f", listOf(float), Casts.IMPLICIT)

        assertEquals(listOf(bool), result.match?.parameters)
    }

    @Test
    fun `explicit casts refuse the converted overload`() {
        val declarations = Declarations<Any?> {
            struct<Any?>("only") { method("f", float, bool) { _, _ -> 0f } }
        }.types.getValue("only")

        val result = declarations.members.method("f", listOf(float), Casts.EXPLICIT)

        assertNull(result.match)
    }

    @Test
    fun `two overloads that fit equally well are reported as ambiguous`() {
        val declarations = Declarations<Any?> {
            struct<Any?>("only") {
                method("f", float, bool, float) { _, _ -> 0f }
                method("f", float, float, bool) { _, _ -> 0f }
            }
        }.types.getValue("only")

        val result = declarations.members.method("f", listOf(float, float), Casts.IMPLICIT)

        assertNull(result.match)
        assertEquals(2, result.ambiguous.size)
    }

    @Test
    fun `an unknown name resolves to nothing rather than throwing`() {
        assertNull(resolve("nope", float).match)
    }

    @Test
    fun `a field and a method can share a name without colliding`() {
        val type = Declarations<Any?> {
            struct<Any?>("mixed") {
                float("value") { 1f }
                method("value", float, float) { _, _ -> 2f }
            }
        }.types.getValue("mixed")

        assertEquals(float, type.members.field("value")?.type)
        assertEquals(1, type.members.methods("value").size)
    }
}
