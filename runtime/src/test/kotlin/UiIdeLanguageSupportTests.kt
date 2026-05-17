import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.HssScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.UiMarkupScriptingAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiIdeLanguageSupportTests {
    @Test
    fun `ui closing completion uses innermost open element`() {
        val text = """
            <box>
                <button>
                    </
                </button>
            </box>
        """.trimIndent()
        val offset = text.indexOf("</") + 2

        val completions = UiMarkupScriptingAnalyzer.completions("preview.ui", text, offset)

        assertEquals(listOf("button"), completions.map { it.show })
        assertEquals("button>", completions.single().insert)
    }

    @Test
    fun `ui attribute completion depends on element type`() {
        val text = """<image s"""

        val completions = UiMarkupScriptingAnalyzer.completions("preview.ui", text, text.length)
            .map { it.show }

        assertTrue("source" in completions)
        assertTrue("src" in completions)
        assertFalse("item" in completions)
        assertFalse("entity" in completions)
    }

    @Test
    fun `hss property completion is only offered inside declaration blocks`() {
        val text = """
            .panel {
                back
            }
        """.trimIndent()
        val offset = text.indexOf("back") + 4

        val completions = HssScriptingAnalyzer.completions("style.hss", text, offset).map { it.show }

        assertTrue("background" in completions)
        assertTrue("backdrop-filter" in completions)
        assertFalse("box" in completions)
    }
}
