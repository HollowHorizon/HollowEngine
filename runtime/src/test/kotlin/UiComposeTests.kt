import androidx.compose.runtime.mutableStateOf
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class UiComposeTests {
    @Test
    fun `compose updates existing ui nodes on state change`() {
        val label = mutableStateOf("First")

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                Column(id = "panel") {
                    Text(label.value, id = "label")
                }
            }
            val panel = root.children.single() as BoxNode
            val text = panel.children.single() as TextNode

            label.value = "Second"
            composition.applyPendingChanges()

            val recomposedPanel = root.children.single() as BoxNode
            val recomposedText = recomposedPanel.children.single() as TextNode
            assertSame(panel, recomposedPanel)
            assertSame(text, recomposedText)
            assertEquals("Second", recomposedText.text.template)
        }
    }

    @Test
    fun `compose preserves text field state across unrelated recomposition`() {
        val title = mutableStateOf("Title")
        val serverValue = mutableStateOf("server")

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                Column {
                    Text(title.value, id = "title")
                    TextField(serverValue.value, id = "field")
                }
            }
            val field = root.textField()
            field.insert("!")

            title.value = "Changed title"
            composition.applyPendingChanges()

            assertSame(field, root.textField())
            assertEquals("server!", root.textField().value)

            serverValue.value = "remote"
            composition.applyPendingChanges()

            assertSame(field, root.textField())
            assertEquals("remote", root.textField().value)
        }
    }

    @Test
    fun `compose custom attributes do not erase widget attributes`() {
        val status = mutableStateOf("idle")

        HollowUiComposition().use { composition ->
            val root = composition.setContent {
                TextField(
                    value = "value",
                    id = "field",
                    attributes = mapOf("status" to status.value),
                )
            }

            status.value = "ready"
            composition.applyPendingChanges()

            val field = root.children.single() as TextFieldNode
            assertEquals("value", field.attributes["value"])
            assertEquals("ready", field.attributes["status"])
        }
    }

    @Test
    fun `compose runtime produces layout frames from composed nodes`() {
        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Row(
                        modifier = Modifier.then(
                            Modifier.size(120.px, 40.px),
                            Modifier.gap(8.px),
                        ),
                    ) {
                        Box(id = "fixed", modifier = Modifier.size(20.px, 10.px))
                        Box(id = "grown", modifier = Modifier.then(Modifier.size(100.percent, 10.px), Modifier.grow(1f)))
                    }
                },
                width = 120f,
                height = 40f,
            )
            val fixed = frame.resolved.styles.keys.first { it.id == "fixed" }
            val grown = frame.resolved.styles.keys.first { it.id == "grown" }

            assertEquals(0f, frame.layout[fixed].rect.x)
            assertEquals(28f, frame.layout[grown].rect.x)
            assertEquals(92f, frame.layout[grown].rect.width)
        }
    }

    @Test
    fun `custom layout policy measures and places children explicitly`() {
        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Layout(
                        content = {
                            Box(id = "avatar", modifier = Modifier.size(40.px, 40.px))
                            Box(id = "text-box", modifier = Modifier.size(100.px, 30.px))
                        },
                    ) { measurables, constraints ->
                        val avatar = measurables[0].measure(constraints)
                        val overlap = 18f
                        val topPadding = 4f
                        val text = measurables[1].measure(
                            constraints.copy(maxWidth = constraints.maxWidth - avatar.width + overlap)
                        )
                        val textWithPadding = text.height + topPadding
                        val width = avatar.width + text.width - overlap
                        val height = maxOf(avatar.height, textWithPadding)
                        layout(width, height) {
                            avatar.place(0, 0)
                            text.place(avatar.width - overlap, topPadding)
                        }
                    }
                },
                width = 200f,
                height = 80f,
            )
            val avatar = frame.resolved.styles.keys.first { it.id == "avatar" }
            val text = frame.resolved.styles.keys.first { it.id == "text-box" }

            assertEquals(0f, frame.layout[avatar].rect.x)
            assertEquals(22f, frame.layout[text].rect.x)
            assertEquals(4f, frame.layout[text].rect.y)
        }
    }

    @Test
    fun `compose runtime keeps scroll state across recomposition`() {
        val label = mutableStateOf("before")

        HollowComposeUiRuntime().use { runtime ->
            runtime.setContent {
                Column {
                    Text(label.value)
                    Box(
                        id = "scroll",
                        modifier = Modifier.then(Modifier.size(80.px, 30.px), Modifier.input(scrollable = true)),
                    ) {
                        Box(id = "row", modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(50.px, 20.px)))
                    }
                }
            }
            val initial = runtime.frame(120f, 80f)
            val scroller = initial.resolved.styles.keys.first { it.id == "scroll" }
            val initialRow = initial.resolved.styles.keys.first { it.id == "row" }
            runtime.setScrollImmediate(scroller, y = 24f)

            label.value = "after"
            val scrolled = runtime.frame(120f, 80f)
            val row = scrolled.resolved.styles.keys.first { it.id == "row" }

            assertEquals(initial.layout[initialRow].rect.y - 24f, scrolled.layout[row].rect.y)
        }
    }

    @Test
    fun `compose xml content produces layout frames`() {
        val xml = parseUiXml(
            """
            <row width="120px" height="40px" gap="8px">
                <box id="fixed" width="20px" height="10px" />
                <box id="grown" width="100%" height="10px" grow="1" />
            </row>
            """.trimIndent(),
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 120f,
                height = 40f,
            )
            val fixed = frame.resolved.styles.keys.first { it.id == "fixed" }
            val grown = frame.resolved.styles.keys.first { it.id == "grown" }

            assertEquals(0f, frame.layout[fixed].rect.x)
            assertEquals(28f, frame.layout[grown].rect.x)
            assertEquals(92f, frame.layout[grown].rect.width)
        }
    }

    @Test
    fun `compose xml content preserves text field state across server sibling update`() {
        fun tree(title: String, value: String) = UiXmlTree(
            "column",
            children = listOf(
                UiXmlTree("text", mapOf("id" to "title", "text" to title)),
                UiXmlTree("text-field", mapOf("id" to "field", "value" to value)),
            ),
        )

        val serverTree = mutableStateOf(tree("Before", "server"))

        HollowComposeUiRuntime().use { runtime ->
            val root = runtime.setContent {
                UiXmlContent(serverTree.value)
            }
            val field = root.textField()
            field.insert("!")

            serverTree.value = tree("After", "server")
            runtime.frame(120f, 80f)

            assertSame(field, root.textField())
            assertEquals("server!", root.textField().value)

            serverTree.value = tree("After", "remote")
            runtime.frame(120f, 80f)

            assertSame(field, root.textField())
            assertEquals("remote", root.textField().value)
        }
    }

    @Test
    fun `compose closing without closing motion ignores active hover transition`() {
        val stylesheet = compileHss(
            """
            .dialog {
                scale: 1;
                transition: scale 1000ms linear;
            }

            .dialog:hover {
                scale: 1.2;
            }
            """.trimIndent(),
        )

        HollowComposeUiRuntime(stylesheet = stylesheet).use { runtime ->
            runtime.setContent {
                Box(id = "dialog", tags = listOf("dialog"))
            }
            runtime.frame(100f, 40f, nowMillis = 0L)
            val dialog = runtime.root.child("dialog")
            dialog.states += UiState.HOVER
            runtime.frame(100f, 40f, nowMillis = 0L)

            dialog.states -= UiState.HOVER
            val closeBase = runtime.frame(100f, 40f, nowMillis = 0L)
            runtime.root.setClosingState(true)
            val closing = runtime.frame(100f, 40f, nowMillis = 0L)

            assertEquals(0L, closing.motionDurationMillis(closeBase))
        }
    }

    @Test
    fun `compose closing transition contributes close motion duration`() {
        val stylesheet = compileHss(
            """
            .dialog {
                opacity: 1;
                transition: opacity 250ms linear;
            }

            .dialog:closing {
                opacity: 0;
            }
            """.trimIndent(),
        )

        HollowComposeUiRuntime(stylesheet = stylesheet).use { runtime ->
            runtime.setContent {
                Box(id = "dialog", tags = listOf("dialog"))
            }
            val opened = runtime.frame(100f, 40f, nowMillis = 0L)

            runtime.root.setClosingState(true)
            val closing = runtime.frame(100f, 40f, nowMillis = 0L)

            assertEquals(250L, closing.motionDurationMillis(opened))
        }
    }

    @Test
    fun `compose resource stylesheet reloads only when loader revision changes`() {
        val loader = VersionedHssLoader(
            """
            .panel {
                opacity: 1;
            }
            """.trimIndent(),
        )

        HollowComposeUiRuntime().use { runtime ->
            runtime.setContent {
                Box(id = "panel", tags = listOf("panel"), modifier = Modifier.style("test:panel.hss", loader))
            }

            val first = runtime.frame(100f, 40f, nowMillis = 0L)
            val second = runtime.frame(100f, 40f, nowMillis = 1L)
            assertEquals(1f, first.resolved[first.resolved.node("panel")].opacity)
            assertEquals(1f, second.resolved[second.resolved.node("panel")].opacity)
            assertEquals(1, loader.loadCount)

            loader.update(
                """
                .panel {
                    opacity: 0.5;
                }
                """.trimIndent(),
            )
            val updated = runtime.frame(100f, 40f, nowMillis = 2L)

            assertEquals(0.5f, updated.resolved[updated.resolved.node("panel")].opacity)
            assertEquals(2, loader.loadCount)
        }
    }

    private fun BoxNode.textField(): TextFieldNode {
        return children
            .flatMap { child -> if (child is BoxNode) child.children else listOf(child) }
            .filterIsInstance<TextFieldNode>()
            .single()
    }

    private fun BoxNode.child(id: String): BoxNode {
        return children.filterIsInstance<BoxNode>().single { it.id == id }
    }

    private fun ResolvedUiTree.node(id: String): BoxNode {
        return styles.keys.filterIsInstance<BoxNode>().single { it.id == id }
    }

    private class VersionedHssLoader(
        private var source: String,
    ) : HssResourceLoader {
        var loadCount = 0
            private set
        private var revision = 0L

        override fun load(location: String): CompiledHss {
            loadCount += 1
            return compileHss(source)
        }

        override fun version(location: String): Long = revision

        fun update(source: String) {
            this.source = source
            revision += 1L
        }
    }
}
