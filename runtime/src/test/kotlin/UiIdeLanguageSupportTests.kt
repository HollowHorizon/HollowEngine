import ru.hollowhorizon.hollowengine.client.ui.MutableUiStyle
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.hss.compileStyleModifier
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
    fun `ui attribute completion does not open on empty attribute prefix`() {
        val text = """<box """

        val completions = UiMarkupScriptingAnalyzer.completions("preview.ui", text, text.length)

        assertTrue(completions.isEmpty())
    }

    @Test
    fun `ui attribute value completion suggests supported values`() {
        val text = """<box align="cen"""

        val completions = UiMarkupScriptingAnalyzer.completions("preview.ui", text, text.length)
            .map { it.show }

        assertTrue("center center" in completions)
        assertFalse("contain" in completions)
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

    @Test
    fun `hss property completion does not open on empty property prefix`() {
        val text = """
            .panel {
                
            }
        """.trimIndent()
        val offset = text.indexOf("\n") + 5

        val completions = HssScriptingAnalyzer.completions("style.hss", text, offset)

        assertTrue(completions.isEmpty())
    }

    @Test
    fun `hss value completion suggests values for current property`() {
        val text = """
            .avatar {
                fit: co
            }
        """.trimIndent()
        val offset = text.indexOf("co") + 2

        val completions = HssScriptingAnalyzer.completions("style.hss", text, offset)
            .map { it.show }

        assertTrue("contain" in completions)
        assertTrue("cover" in completions)
        assertFalse("center center" in completions)
    }

    @Test
    fun `align shorthand stores horizontal and vertical axes`() {
        val style = MutableUiStyle()

        compileStyleModifier("align", "center end")!!.applyTo(style)

        assertEquals(UiAlign.CENTER, style.alignHorizontal)
        assertEquals(UiAlign.END, style.alignVertical)
    }

    @Test
    fun `align items stores child alignment axes`() {
        val style = MutableUiStyle()

        compileStyleModifier("align-items", "center start")!!.applyTo(style)

        assertEquals(UiAlign.CENTER, style.alignItemsHorizontal)
        assertEquals(UiAlign.START, style.alignItemsVertical)
    }

    @Test
    fun `background image accepts direct resource location`() {
        val style = MutableUiStyle()

        compileStyleModifier("background-image", "hollowengine:textures/gui/panel.png")!!.applyTo(style)

        val background = style.background
        assertTrue(background is UiPaint.Image)
        assertEquals("hollowengine:textures/gui/panel.png", background.source.template)
    }

    @Test
    fun `hss compiles aspect ratio and text wrap modifiers`() {
        val style = MutableUiStyle()

        compileStyleModifier("aspect-ratio", "16/9")!!.applyTo(style)
        compileStyleModifier("text-wrap", "nowrap")!!.applyTo(style)

        assertEquals(16f / 9f, style.aspectRatio)
        assertEquals(false, style.textWrap)
    }

    @Test
    fun `hss compiles fit and fill sizing keywords`() {
        val style = MutableUiStyle()

        compileStyleModifier("size", "fit fill")!!.applyTo(style)

        assertEquals(UiLength.Auto, style.size?.width)
        assertEquals(UiLength.Fill, style.size?.height)
    }
}
