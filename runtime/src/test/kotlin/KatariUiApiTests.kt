import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_NODE_NAME
import com.sunnychung.lib.multiplatform.kotlite.model.XML_TEXT_VALUE_ATTRIBUTE
import ru.hollowhorizon.hollowengine.client.ui.BaseUiNode
import ru.hollowhorizon.hollowengine.client.ui.TextNode
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlBuilder
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUi
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariUiDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KatariUiApiTests {
    @Test
    fun `ui xml supports boxed text composition`() {
        val root = parseUi(
            """
            <box>
                <box id="continue">
                    <text>Continue</text>
                </box>
            </box>
            """.trimIndent(),
        )

        val container = assertIs<BaseUiNode>(root.children.single())
        val text = assertIs<TextNode>(container.children.single())

        assertEquals("box", container.type)
        assertEquals("Continue", text.text.template)
    }

    @Test
    fun `ui document inserts into tagged container`() {
        val document = KatariUiDocument(
            id = "test",
            root = UiXmlTree("box", mapOf("tags" to "container")),
        )

        document.insertAt(
            "container",
            UiXmlTree(
                "box",
                children = listOf(
                    UiXmlTree(
                        "text",
                        children = listOf(UiXmlTree(XML_TEXT_NODE_NAME, mapOf(XML_TEXT_VALUE_ATTRIBUTE to "Extra"))),
                    ),
                ),
            ),
        )

        val root = UiXmlBuilder().build(document.root)
        val container = assertIs<BaseUiNode>(root.children.single())
        val text = assertIs<TextNode>(container.children.single())

        assertEquals("box", container.type)
        assertEquals("Extra", text.text.template)
    }

    @Test
    fun `katari analyzer accepts scripted ui literals and mutations`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic(
            "ui.ktr",
            """
            val gui = ui(
                <box tags="container">
                    <box><text>Continue</text></box>
                    <box><text>Cancel</text></box>
                </box>
            )
            gui.insertAt("container", <box><text>Extra</text></box>)
            """.trimIndent(),
        )

        assertEquals(emptyList(), diagnostics)
    }

    @Test
    fun `katari analyzer accepts ui await payload as struct`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic(
            "ui_await.ktr",
            """
            val gui = ui(<box />)
            val anyPayload = gui.await()
            val playerPayload = gui.await(player)
            anyPayload.getString("event")
            playerPayload.getString("event")
            """.trimIndent(),
        )

        assertEquals(emptyList(), diagnostics)
    }
}
