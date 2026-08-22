import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.models.bedrock.BedrockContext
import ru.hollowhorizon.hollowengine.client.models.bedrock.BedrockExpressions
import ru.hollowhorizon.hollowengine.client.models.bedrock.Query
import ru.hollowhorizon.hollowengine.client.models.bedrock.VariablesMap
import kotlin.test.assertEquals

/**
 * The Molang dialect that Bedrock models and particles are written in. It replaced a second compiler
 * with its own parser and classloader, so what matters here is that the names those files use still
 * resolve and that batching a file does not change what its expressions mean.
 */
class BedrockExpressionTests {
    private class TestQuery(
        override val ground_speed: Float = 4f,
        override val health: Float = 12f,
        override val is_sneaking: Boolean = true,
    ) : Query

    private fun context(): BedrockContext = BedrockContext(TestQuery(), VariablesMap())

    private fun eval(source: String, context: BedrockContext = context()): Float =
        BedrockExpressions.parse(source).eval(context)

    @Test
    fun `queries resolve with and without the prefix`() {
        assertEquals(4f, eval("q.ground_speed"))
        assertEquals(4f, eval("query.ground_speed"))
        assertEquals(4f, eval("ground_speed"))
        assertEquals(12f, eval("q.health"))
    }

    /** Content writes `q.is_sneaking` where a number is wanted; a bool has to read as 1. */
    @Test
    fun `a bool query reads as a number and as a condition`() {
        assertEquals(1f, eval("q.is_sneaking"))
        assertEquals(10f, eval("q.is_sneaking ? 10 : 20"))
        assertEquals(1f, eval("q.is_sneaking != 0.0"))
    }

    @Test
    fun `variables persist and temporaries are separate`() {
        val context = context()

        assertEquals(0f, eval("v.speed", context))
        assertEquals(6f, eval("v.speed = q.ground_speed + 2", context))
        assertEquals(6f, eval("v.speed", context))
        assertEquals(0f, eval("t.speed", context))
    }

    @Test
    fun `several statements run in order`() {
        assertEquals(7f, eval("v.a = 3; v.b = 4; v.a + v.b"))
    }

    @Test
    fun `math is the Molang one, in degrees`() {
        assertEquals(1f, eval("math.sin(90)"), 0.0001f)
        assertEquals(5f, eval("math.clamp(9, 1, 5)"))
        assertEquals(3.1415927f, eval("math.pi"), 0.0001f)
        assertEquals(4f, eval("math.lerp(0, 8, 0.5)"))
    }

    /** A pack names queries this engine never heard of; the model still has to load. */
    @Test
    fun `an unknown name reads as zero`() {
        assertEquals(0f, eval("q.made_up_query"))
        assertEquals(1f, eval("q.made_up_query + 1"))
    }

    /**
     * One file becomes one generated class, and the swap from interpreted to compiled happens under
     * expressions that are already held by the loaded file.
     */
    @Test
    fun `batching compiles without changing what an expression means`() {
        val context = context()
        val interpreted = BedrockExpressions.parse("q.ground_speed * 2 + math.abs(-1)").eval(context)

        val compiled = BedrockExpressions.batch {
            BedrockExpressions.parse("q.ground_speed * 2 + math.abs(-1)")
        }

        assertEquals(interpreted, compiled.eval(context))
        assertEquals(9f, compiled.eval(context))
    }

    @Test
    fun `a batch keeps every expression of the file working`() {
        val context = context()
        val expressions = BedrockExpressions.batch {
            listOf("q.health", "v.x = 2; v.x * 3", "q.is_sneaking ? 1 : 0").map(BedrockExpressions::parse)
        }

        assertEquals(listOf(12f, 6f, 1f), expressions.map { it.eval(context) })
    }

    /** A broken expression costs that expression, not the file it sits in. */
    @Test
    fun `a batch survives an expression that does not parse`() {
        val context = context()
        val expressions = BedrockExpressions.batch {
            listOf("q.health", "q.health +").map(BedrockExpressions::parse)
        }

        assertEquals(12f, expressions[0].eval(context))
        assertEquals(0f, expressions[1].eval(context))
    }
}
