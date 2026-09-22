package ru.hollowhorizon.hollowengine.client.ui.style

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `@keyframes` selectors and playback, from the report in issue #100/#105: `50%` used to parse as
 * offset 50 (clamped to 1.0), which collapsed every percentage animation onto its own midpoint.
 */
class HssKeyframesTest {
    private fun keyframes(source: String): UiKeyframes =
        compileHss(source).keyframes.values.single()

    private fun offsets(source: String): List<Float> = keyframes(source).frames.map { it.offset }.sorted()

    @Test
    fun `percentage selectors are fractions of the iteration`() {
        assertEquals(
            listOf(0f, 0.5f, 1f),
            offsets(
                """
                    @keyframes anim {
                        0% { translate-y: -5px; }
                        50% { translate-y: 5px; }
                        100% { translate-y: -5px; }
                    }
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `fractional percentages keep their precision`() {
        assertEquals(listOf(0.125f, 0.335f), offsets("@keyframes a { 12.5% { opacity: 0; } 33.5% { opacity: 1; } }"))
    }

    @Test
    fun `from and to are the edges of the iteration`() {
        assertEquals(listOf(0f, 1f), offsets("@keyframes a { from { opacity: 0; } to { opacity: 1; } }"))
    }

    @Test
    fun `a selector list expands into one frame per offset`() {
        val frames = keyframes(
            """
                @keyframes anim {
                    from, to { translate-y: -5px; }
                    50% { translate-y: 5px; }
                }
            """.trimIndent(),
        ).frames
        assertEquals(listOf(0f, 0.5f, 1f), frames.map { it.offset }.sorted())
        val edges = frames.filter { it.offset != 0.5f }
        assertEquals(2, edges.size)
        assertEquals(edges.first().style, edges.last().style)
    }

    @Test
    fun `percent selector lists are accepted too`() {
        assertEquals(listOf(0f, 0.5f, 1f), offsets("@keyframes a { 0%, 100% { opacity: 0; } 50% { opacity: 1; } }"))
    }

    private fun translateYAt(source: String, timeMillis: Long): Float {
        val stylesheet = compileHss(source)
        val node = BoxNode(id = "example")
        val resolver = UiModifierResolver(stylesheet = stylesheet)
        resolver.resolve(node, nowMillis = 0L)
        resolver.resolve(node, nowMillis = timeMillis)
        return node.resolvedSnapshot.translate.y
    }

    private val bounce = """
        #example { animation: anim 1s linear infinite; }

        @keyframes anim {
            0% { translate-y: -5px; }
            50% { translate-y: 5px; }
            100% { translate-y: -5px; }
        }
    """.trimIndent()

    @Test
    fun `a three stop animation runs out and back over one full iteration`() {
        assertEquals(-5f, translateYAt(bounce, 0L), 0.01f)
        assertEquals(0f, translateYAt(bounce, 250L), 0.01f)
        assertEquals(5f, translateYAt(bounce, 500L), 0.01f)
        assertEquals(0f, translateYAt(bounce, 750L), 0.01f)
    }

    @Test
    fun `an infinite animation wraps back to the first frame`() {
        assertEquals(-5f, translateYAt(bounce, 1000L), 0.01f)
        assertEquals(5f, translateYAt(bounce, 1500L), 0.01f)
    }

    @Test
    fun `a from-to selector list animates instead of standing still`() {
        val source = """
            #example { animation: anim 1s linear infinite; }

            @keyframes anim {
                from, to { translate-y: -5px; }
                50% { translate-y: 5px; }
            }
        """.trimIndent()
        assertEquals(-5f, translateYAt(source, 0L), 0.01f)
        assertEquals(5f, translateYAt(source, 500L), 0.01f)
        assertEquals(-5f, translateYAt(source, 1000L), 0.01f)
    }

    @Test
    fun `a missing edge frame falls back to the base style`() {
        val source = """
            #example { translate-y: 0px; animation: anim 1s linear infinite; }

            @keyframes anim {
                50% { translate-y: 10px; }
            }
        """.trimIndent()
        assertEquals(0f, translateYAt(source, 0L), 0.01f)
        assertEquals(5f, translateYAt(source, 250L), 0.01f)
        assertEquals(10f, translateYAt(source, 500L), 0.01f)
        assertEquals(5f, translateYAt(source, 750L), 0.01f)
    }

    @Test
    fun `offsets outside the iteration are clamped`() {
        assertEquals(listOf(0f, 1f), offsets("@keyframes a { -20% { opacity: 0; } 140% { opacity: 1; } }"))
    }

    @Test
    fun `a malformed selector is reported`() {
        val errors = HssParser("@keyframes a { half { opacity: 0; } }").parseRecovering().errors
        assertTrue(errors.any { "from" in it.message.orEmpty() }, "got $errors")
    }
}
