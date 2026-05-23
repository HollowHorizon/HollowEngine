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
    fun `ui xml supports button text attribute`() {
        val root = parseUi(
            """
            <box>
                <button text="Continue" />
            </box>
            """.trimIndent(),
        )

        val button = assertIs<BaseUiNode>(root.children.single())
        val text = assertIs<TextNode>(button.children.single())

        assertEquals("button", button.type)
        assertEquals("Continue", text.text.template)
    }

    @Test
    fun `ui document inserts into tagged container`() {
        val document = KatariUiDocument(
            id = "test",
            root = UiXmlTree("box", mapOf("tags" to "container")),
        )

        document.insertAt("container", UiXmlTree("button", mapOf("text" to "Extra")))

        val root = UiXmlBuilder().build(document.root)
        val button = assertIs<BaseUiNode>(root.children.single())
        val text = assertIs<TextNode>(button.children.single())

        assertEquals("button", button.type)
        assertEquals("Extra", text.text.template)
    }

    @Test
    fun `katari analyzer accepts scripted ui literals and mutations`() {
        val diagnostics = KatariScriptingAnalyzer.diagnostic(
            "ui.ktr",
            """
            val gui = ui(
                <box tags="container">
                    <button text="Continue" />
                    <button text="Cancel" />
                </box>
            )
            gui.insertAt("container", <button text="Extra" />)
            """.trimIndent(),
        )

        assertEquals(emptyList(), diagnostics)
    }
}
