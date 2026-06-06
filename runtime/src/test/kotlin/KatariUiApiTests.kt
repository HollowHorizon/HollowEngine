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
    fun `ui document modifies custom attributes on matching target`() {
        val document = KatariUiDocument(
            id = "test",
            root = UiXmlTree(
                "box",
                children = listOf(UiXmlTree("box", mapOf("id" to "panel", "my-custom-state" to "opening"))),
            ),
        )

        document.modify("panel", attribute = "my-custom-state", value = "ready")

        assertEquals("ready", document.root.children.single().attributes["my-custom-state"])
    }

    @Test
    fun `ui document replaceAt replaces matching node and assigns attributes`() {
        val document = KatariUiDocument(
            id = "test",
            root = UiXmlTree(
                "box",
                children = listOf(
                    UiXmlTree(
                        "box",
                        mapOf("id" to "message"),
                        children = listOf(
                            UiXmlTree("box", mapOf("tags" to "avatar-block")),
                            UiXmlTree(
                                "box",
                                mapOf("tags" to "nickname-block"),
                                children = listOf(UiXmlTree("text", mapOf("id" to "dialog-nick", "text" to "1234567"))),
                            ),
                            UiXmlTree(
                                "box",
                                mapOf("tags" to "message-block"),
                                children = listOf(
                                    UiXmlTree(
                                        "text",
                                        mapOf("id" to "dialog-message", "tags" to "message-text", "text" to "123456789"),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        document.replaceAt(
            "dialog-message",
            UiXmlTree("text", mapOf("text" to "New")),
            mapOf("tags" to "message-text", "status" to "active"),
        )

        val message = document.root.children.single()
        val messageBlock = message.children.last()
        val text = messageBlock.children.single()

        assertEquals("text", text.name)
        assertEquals("New", text.attributes["text"])
        assertEquals("message-text", text.attributes["tags"])
        assertEquals("active", text.attributes["status"])
        assertEquals(emptyList(), text.children)
    }

    @Test
    fun `ui document replaces target contents explicitly`() {
        val document = KatariUiDocument(
            id = "test",
            root = UiXmlTree(
                "box",
                children = listOf(
                    UiXmlTree(
                        "box",
                        mapOf("id" to "dialog-message"),
                        children = listOf(UiXmlTree("text", mapOf("text" to "Old"))),
                    ),
                ),
            ),
        )

        document.replaceChildrenAt(
            "dialog-message",
            UiXmlTree("text", mapOf("text" to "New")),
            mapOf("tags" to "message-text", "status" to "active"),
        )

        val message = document.root.children.single()
        val text = message.children.single()

        assertEquals("text", text.name)
        assertEquals("New", text.attributes["text"])
        assertEquals("message-text", text.attributes["tags"])
        assertEquals("active", text.attributes["status"])
    }

    @Test
    fun `ui document clears removes attributes and modifies all matches`() {
        val document = KatariUiDocument(
            id = "test",
            root = UiXmlTree(
                "box",
                children = listOf(
                    UiXmlTree("box", mapOf("tags" to "line", "status" to "old"), children = listOf(UiXmlTree("text"))),
                    UiXmlTree("box", mapOf("tags" to "line", "status" to "old"), children = listOf(UiXmlTree("text"))),
                ),
            ),
        )

        val count = document.modifyAll("line", "status", "new")
        document.removeAttribute("line", "status")
        document.clear("line")

        assertEquals(2, count)
        assertEquals(null, document.root.children.first().attributes["status"])
        assertEquals("new", document.root.children.last().attributes["status"])
        assertEquals(emptyList(), document.root.children.first().children)
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
            gui.modify("container", attribute = "my-custom-state", value = "ready")
            gui.replaceAt("container", <text>Line</text>, struct { tags: "message-text" })
            gui.replaceChildrenAt("container", <text>Line</text>, struct { tags: "message-text" })
            gui.modify("container", struct { status: "show" })
            gui.modifyAll("container", struct { status: "close" })
            gui.updateOverlay(player)
            gui.clear("container")
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
