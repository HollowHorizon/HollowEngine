import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.MutableUiStyle
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.style.UiTextOverflow
import ru.hollowhorizon.hollowengine.client.ui.style.compileStyleModifier
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.HssScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.UiXmlScriptingAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiIdeLanguageSupportTests {
    @Test
    fun `ui closing completion uses innermost open element`() {
        val text = """
            <box>
                <text>
                    </
                </text>
            </box>
        """.trimIndent()
        val offset = text.indexOf("</") + 2

        val completions = UiXmlScriptingAnalyzer.completions("preview.ui", text, offset)

        assertEquals(listOf("text"), completions.map { it.show })
        assertEquals("text>", completions.single().insert)
    }

    @Test
    fun `ui attribute completion depends on element type`() {
        val text = """<image s"""

        val completions = UiXmlScriptingAnalyzer.completions("preview.ui", text, text.length)
            .map { it.show }

        assertTrue("source" in completions)
        assertTrue("src" in completions)
        assertFalse("item" in completions)
        assertFalse("entity" in completions)
    }

    @Test
    fun `ui attribute completion does not open on empty attribute prefix`() {
        val text = """<box """

        val completions = UiXmlScriptingAnalyzer.completions("preview.ui", text, text.length)

        assertTrue(completions.isEmpty())
    }

    @Test
    fun `ui attribute value completion suggests supported values`() {
        val text = """<box align="cen"""

        val completions = UiXmlScriptingAnalyzer.completions("preview.ui", text, text.length)
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
        compileStyleModifier("text-overflow", "dots")!!.applyTo(style)
        compileStyleModifier("text-align", "justify")!!.applyTo(style)
        compileStyleModifier("font-size", "16px")!!.applyTo(style)

        assertEquals(16f / 9f, style.aspectRatio)
        assertEquals(false, style.textWrap)
        assertEquals(UiTextOverflow.DOTS, style.textOverflow)
        assertEquals(UiTextAlign.JUSTIFY, style.textAlign)
        assertEquals(16f, style.fontSize)
    }

    @Test
    fun `hss compiles fit and fill sizing keywords`() {
        val style = MutableUiStyle()

        compileStyleModifier("size", "fit fill")!!.applyTo(style)

        assertEquals(UiLength.Auto, style.size?.width)
        assertEquals(UiLength.Fill, style.size?.height)
    }

    @Test
    fun `hss compiles pivot shortcuts and numeric pivots`() {
        val shortcut = MutableUiStyle()
        val numeric = MutableUiStyle()

        compileStyleModifier("pivot", "bottom-right")!!.applyTo(shortcut)
        compileStyleModifier("transform-origin", "-50px -25px 0px")!!.applyTo(numeric)

        assertEquals(UiTransformPivot.BottomRight, shortcut.transform?.pivot)
        assertEquals(UiLength.Px(-50f), numeric.transform?.pivot?.x)
        assertEquals(UiLength.Px(-25f), numeric.transform?.pivot?.y)
    }

    @Test
    fun `hss completion suggests pivot and text values`() {
        val pivotText = """
            .card {
                pivot: bott
            }
        """.trimIndent()
        val alignText = """
            .label {
                text-align: ju
            }
        """.trimIndent()

        val pivotCompletions = HssScriptingAnalyzer.completions("style.hss", pivotText, pivotText.indexOf("bott") + 4).map { it.show }
        val alignCompletions = HssScriptingAnalyzer.completions("style.hss", alignText, alignText.indexOf("ju") + 2).map { it.show }

        assertTrue("bottom-right" in pivotCompletions)
        assertTrue("justify" in alignCompletions)
        assertTrue("dots" in HssScriptingAnalyzer.completions(
            "style.hss",
            ".label { text-overflow: do }",
            ".label { text-overflow: do }".indexOf("do") + 2,
        ).map { it.show })
    }
}
