import ru.hollowhorizon.hollowengine.addons.mcp.docs.DocsLibrary
import ru.hollowhorizon.hollowengine.addons.mcp.docs.DocsPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocsLibraryTest {
    private val states = DocsPage.parse(
        "scripting/node/states",
        """
        ---
        title: States
        order: 3
        ---

        Intro about node states.
        ![A picture](/docs/state.webp)

        ## Switching states
        Call `state("next")` to switch. See [events](/docs/hollowengine/scripting/events).

        ### Timeouts
        A state may time out.

        ```kotlin
        # not a heading inside code
        ```

        ## Saving
        States are saved with the world.
        """.trimIndent(),
    )
    private val library = DocsLibrary(listOf(states, DocsPage.parse("scripting/node/index", "# Nodes\nNode scripts run handlers.")))

    @Test
    fun `sections split at headings but not inside code`() {
        assertEquals("States", states.title)
        assertEquals(listOf("", "switching-states", "timeouts", "saving"), states.sections.map { it.anchor })
        assertTrue("# not a heading inside code" in states.section("timeouts")!!)
    }

    @Test
    fun `a section includes the deeper ones under it and stops at its own level`() {
        val section = assertNotNull(states.section("switching-states"))
        assertTrue("A state may time out." in section)
        assertFalse("saved with the world" in section)
    }

    @Test
    fun `site links become page ids and images are dropped`() {
        assertTrue("[events](scripting/events)" in states.text)
        assertFalse("state.webp" in states.text)
    }

    @Test
    fun `folders and site paths resolve to their pages`() {
        assertEquals("scripting/node/index", library.page("scripting/node")?.id)
        assertEquals("scripting/node/states", library.page("/docs/hollowengine/scripting/node/states")?.id)
    }

    @Test
    fun `search needs every word and ranks headings first`() {
        assertEquals(listOf("timeouts"), library.search("state time out", 10).map { it.section.anchor })
        assertEquals("saving", library.search("saving", 10).first().section.anchor)
        assertTrue(library.search("nonexistent words", 10).isEmpty())
    }
}
