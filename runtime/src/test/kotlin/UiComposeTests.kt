import androidx.compose.runtime.mutableStateOf
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.CompiledHss
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlTree
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun `text field applies insertion to multiple carets`() {
        val field = TextFieldNode("ac", mode = UiTextFieldMode.MULTI_LINE, multiCaret = true)

        field.setCaretRanges(listOf(UiTextCaret(1), UiTextCaret(2)))
        field.insert("b")

        assertEquals("abcb", field.value)
        assertEquals(listOf(2, 4), field.carets)
    }

    @Test
    fun `text field can delete whole words around the caret`() {
        val field = TextFieldNode("alpha beta gamma")

        field.moveCaret("alpha beta".length)
        field.backspace(word = true)
        assertEquals("alpha  gamma", field.value)

        field.moveCaret("alpha  ".length)
        field.deleteForward(word = true)
        assertEquals("alpha  ", field.value)
    }

    @Test
    fun `text completion replaces prefix and moves caret inside template`() {
        val field = TextFieldNode(
            "Text",
            completionContributor = UiCompletionContributor {
                listOf(UiTextCompletion("TextField", "TextField(value = \"\")", caretOffset = "TextField(value = \"".length))
            },
        )

        field.moveCaret(4)
        field.openCompletions()
        field.acceptCompletion()

        assertEquals("TextField(value = \"\")", field.value)
        assertEquals("TextField(value = \"".length, field.caret)
    }

    @Test
    fun `text selection fills line gaps and empty lines`() {
        val layout = UiTextLayouter.layout(
            "a\n\nb",
            80f,
            Float.POSITIVE_INFINITY,
            false,
            UiTextAlign.LEFT,
            12f,
            preserveWhitespace = true,
            lineSpacing = 4f,
        )

        val rects = layout.selectionRects(0, 4, 12f, fillLineGaps = true)

        assertTrue(rects.any { it.height > 12f })
        assertTrue(rects.any { it.width == layout.width })
    }

    @Test
    fun `text field default keymap is a modifier fallback`() {
        var intercepted = false

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    TextField(
                        value = "abc",
                        id = "field",
                        modifier = Modifier.onKeyInput { input ->
                            intercepted = input.key == GLFW.GLFW_KEY_LEFT
                            intercepted
                        },
                    )
                },
                width = 120f,
                height = 32f,
            )
            val field = frame.resolved.styles.keys.filterIsInstance<TextFieldNode>().single()
            field.moveCaret(3)

            val event = UiEvent(UiEventKind.KEY_PRESSED, field, frame = frame, key = GLFW.GLFW_KEY_LEFT)
            assertTrue(field.dispatch(event))

            assertTrue(intercepted)
            assertEquals(3, field.caret)
        }
    }

    @Test
    fun `lazy column keeps scroll range but places only visible children`() {
        val root = BoxNode(layout = UiLayout.LazyColumn, modifiers = listOf(Modifier.size(100.px, 30.px), Modifier.input(scrollable = true)))
        repeat(10) { index ->
            root.children += BoxNode(id = "row-$index", modifiers = listOf(Modifier.size(100.px, 10.px)))
        }

        UiNodeKeys.assign(root)
        val resolved = UiStyleResolver().resolve(root, animate = false)
        val layout = UiLayoutEngine().compute(resolved, width = 100f, height = 30f)

        assertTrue(layout[root].scrollRange.y > 0f)
        assertTrue(root.children.count { it in layout.nodes } < root.children.size)
    }

    @Test
    fun `scroll target ignores virtualized lazy children without layout nodes`() {
        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    LazyColumn(
                        id = "lazy",
                        modifier = Modifier.then(
                            Modifier.size(100.px, 30.px),
                            Modifier.input(scrollable = true),
                        ),
                    ) {
                        repeat(10) { index ->
                            Box(id = "row-$index", modifier = Modifier.size(100.px, 10.px))
                        }
                    }
                },
                width = 100f,
                height = 30f,
            )
            val target = frame.scrollTargetAt(8f, 8f)

            assertEquals("lazy", target?.id)
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
    fun `text places inline widget children from text layout`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("Status ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" ready".bound()),
            )
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(textContent = content, modifier = Modifier.fontSize(10f)) {
                        InlineWidget("badge", modifier = Modifier.size(24.px, 10.px))
                    }
                },
                width = 160f,
                height = 40f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val badge = frame.resolved.styles.keys.single { it.id == "badge" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetRun = command.layout.lines
                .flatMap { line -> line.fragments.map { line to it } }
                .single { (_, fragment) -> fragment is UiInlineWidgetRun }
            val line = widgetRun.first
            val fragment = widgetRun.second as UiInlineWidgetRun

            assertEquals(24f, frame.layout[badge].rect.width)
            assertEquals(10f, frame.layout[badge].rect.height)
            assertEquals(frame.layout[text].content.x + line.x + fragment.x, frame.layout[badge].rect.x)
            assertEquals(frame.layout[text].content.y + line.y + fragment.y, frame.layout[badge].rect.y)
        }
    }

    @Test
    fun `text inline widget follows justified line offsets`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("left ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" right side keeps going enough to wrap".bound()),
            )
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(
                            Modifier.size(180.px, 60.px),
                            Modifier.fontSize(10f),
                            Modifier.textAlign(UiTextAlign.JUSTIFY),
                        ),
                    ) {
                        InlineWidget("badge", modifier = Modifier.size(24.px, 10.px))
                    }
                },
                width = 200f,
                height = 80f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val badge = frame.resolved.styles.keys.single { it.id == "badge" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val line = command.layout.lines.single { line -> line.fragments.any { it is UiInlineWidgetRun } }
            val fragment = line.fragments.filterIsInstance<UiInlineWidgetRun>().single()

            assertTrue(line.justify)
            assertEquals(frame.layout[text].content.x + line.x + fragment.x, frame.layout[badge].rect.x)
        }
    }

    @Test
    fun `text fixed width is constrained by parent before wrapping`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("Inline text can host ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" measured widgets and keep wrapping consistent.".bound()),
            )
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Column(
                        id = "constrained-parent",
                        modifier = Modifier.then(
                            Modifier.size(330.px, 120.px),
                            Modifier.padding(12.px),
                        ),
                    ) {
                        Text(
                            textContent = content,
                            id = "constrained-text",
                            modifier = Modifier.then(
                                Modifier.size(320.px, 80.px),
                                Modifier.fontSize(10f),
                                Modifier.textAlign(UiTextAlign.JUSTIFY),
                            ),
                        ) {
                            InlineWidget("badge", modifier = Modifier.size(58.px, 22.px)) {
                                Text("AUTO", modifier = Modifier.fontSize(9f))
                            }
                        }
                    }
                },
                width = 360f,
                height = 140f,
            )
            val parent = frame.resolved.styles.keys.single { it.id == "constrained-parent" }
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single { it.id == "constrained-text" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }

            assertEquals(306f, frame.layout[parent].content.width)
            assertEquals(306f, command.rect.width)
            command.layout.lines.forEach { line ->
                val right = line.fragments.maxOfOrNull { it.x + it.width } ?: 0f
                assertTrue(line.x + right <= command.rect.width + 0.01f)
            }
        }
    }

    @Test
    fun `text line after tall inline widget starts below widget bottom`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("A ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" B C D E F G H".bound()),
            )
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(Modifier.size(64.px, 90.px), Modifier.fontSize(10f)),
                    ) {
                        InlineWidget("badge", modifier = Modifier.size(20.px, 28.px))
                    }
                },
                width = 80f,
                height = 100f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetLine = command.layout.lines.single { line -> line.fragments.any { it is UiInlineWidgetRun } }
            val widget = widgetLine.fragments.filterIsInstance<UiInlineWidgetRun>().single()
            val nextLine = command.layout.lines.first { it.y > widgetLine.y }

            assertTrue(nextLine.y >= widgetLine.y + widget.y + widget.height)
        }
    }

    @Test
    fun `text line after inline widget includes configured spacing from widget bottom`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.Text("A ".bound()),
                UiTextSegment.inlineWidget("badge", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" B C D E F G H".bound()),
            )
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(
                            Modifier.size(64.px, 100.px),
                            Modifier.fontSize(10f),
                            Modifier.lineSpacing(5f),
                        ),
                    ) {
                        InlineWidget("badge", modifier = Modifier.size(20.px, 28.px))
                    }
                },
                width = 80f,
                height = 110f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetLine = command.layout.lines.single { line -> line.fragments.any { it is UiInlineWidgetRun } }
            val widget = widgetLine.fragments.filterIsInstance<UiInlineWidgetRun>().single()
            val nextLine = command.layout.lines.first { it.y > widgetLine.y }

            assertEquals(widgetLine.y + widget.y + widget.height + 5f, nextLine.y)
        }
    }

    @Test
    fun `text flow widget reserves measured space for wrapped text`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.flowWidget("aside"),
                UiTextSegment.Text("Flow text wraps beside the measured widget before using the full line width below it.".bound()),
            )
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(Modifier.size(140.px, 90.px), Modifier.fontSize(10f)),
                    ) {
                        InlineWidget("aside", modifier = Modifier.size(42.px, 28.px))
                    }
                },
                width = 160f,
                height = 110f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val aside = frame.resolved.styles.keys.single { it.id == "aside" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val flowRun = command.layout.lines
                .flatMap { line -> line.fragments.map { line to it } }
                .single { (_, fragment) -> fragment is UiInlineWidgetRun }
            val firstTextRun = command.layout.lines.first().fragments.filterIsInstance<UiTextRun>().first()

            assertEquals(frame.layout[text].content.x, frame.layout[aside].rect.x)
            assertEquals(frame.layout[text].content.y, frame.layout[aside].rect.y)
            assertEquals(frame.layout[text].content.x + flowRun.first.x + flowRun.second.x, frame.layout[aside].rect.x)
            assertTrue(command.layout.lines.first().x >= 48f)
            assertTrue(command.layout.lines.count { it.y < 28f && it.x >= 48f } > 1)
            assertTrue(firstTextRun.x >= 0f)
        }
    }

    @Test
    fun `text flow widget keeps wrapped lines beside widget until widget bottom`() {
        val content = UiTextContent(
            listOf(
                UiTextSegment.flowWidget("aside"),
                UiTextSegment.Text(
                    "One two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen.".bound(),
                ),
            )
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        textContent = content,
                        modifier = Modifier.then(Modifier.size(126.px, 110.px), Modifier.fontSize(10f)),
                    ) {
                        InlineWidget("aside", modifier = Modifier.size(42.px, 36.px))
                    }
                },
                width = 140f,
                height = 120f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val fullWidthLine = command.layout.lines.firstOrNull { it.x < 42f }

            assertTrue(command.layout.lines.count { it.y < 36f && it.x >= 48f } > 1)
            assertTrue(fullWidthLine == null || fullWidthLine.y >= 36f)
        }
    }

    @Test
    fun `text line spacing increases distance between lines`() {
        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        "one two three four",
                        modifier = Modifier.then(
                            Modifier.size(36.px, 80.px),
                            Modifier.fontSize(10f),
                            Modifier.lineSpacing(6f),
                        ),
                    )
                },
                width = 50f,
                height = 90f,
            )
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single()
            val first = command.layout.lines[0]
            val second = command.layout.lines[1]

            assertEquals(first.height + 6f, second.y - first.y)
        }
    }

    @Test
    fun `text space width can be configured`() {
        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Text(
                        "A B",
                        modifier = Modifier.then(
                            Modifier.fontSize(10f),
                            Modifier.spaceWidth(18f),
                        ),
                    )
                },
                width = 100f,
                height = 40f,
            )
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single()
            val space = command.layout.lines.single().fragments.filterIsInstance<UiTextSpaceRun>().single()

            assertEquals(18f, space.width)
        }
    }

    @Test
    fun `xml text uses hss line spacing and space width`() {
        val stylesheet = compileHss(
            """
            .spaced-text {
                line-spacing: 5px;
                space-width: 16px;
            }
            """.trimIndent(),
        )
        val xml = parseUiXml(
            """
            <text tags="spaced-text" font-size="10" width="42px" height="80px">
                A B C D
            </text>
            """.trimIndent(),
        )

        HollowComposeUiRuntime(stylesheet = stylesheet).use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 70f,
                height = 90f,
            )
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single()
            val first = command.layout.lines[0]
            val second = command.layout.lines[1]
            val space = command.layout.lines.flatMap { it.fragments }.filterIsInstance<UiTextSpaceRun>().first()

            assertEquals(first.height + 5f, second.y - first.y)
            assertEquals(16f, space.width)
        }
    }

    @Test
    fun `xml text places nested widget children from measured layout`() {
        val xml = parseUiXml(
            """
            <text font-size="10">
                Message with <box id="badge" width="24px" height="10px" /> widget
            </text>
            """.trimIndent(),
        )

        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 180f,
                height = 40f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val badge = frame.resolved.styles.keys.single { it.id == "badge" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }
            val widgetRun = command.layout.lines
                .flatMap { line -> line.fragments.map { line to it } }
                .single { (_, fragment) -> fragment is UiInlineWidgetRun }

            assertEquals(24f, frame.layout[badge].rect.width)
            assertEquals(10f, frame.layout[badge].rect.height)
            assertEquals(frame.layout[text].content.x + widgetRun.first.x + widgetRun.second.x, frame.layout[badge].rect.x)
        }
    }

    @Test
    fun `xml text flow widget uses hss sized child for wrapping`() {
        val stylesheet = compileHss(
            """
            .flow-aside {
                size: 36px 24px;
            }
            """.trimIndent(),
        )
        val xml = parseUiXml(
            """
            <text font-size="10" width="130px" height="80px">
                <box id="aside" tags="flow-aside" flow="start" />
                Text wraps around the HSS-sized child and then continues below it.
            </text>
            """.trimIndent(),
        )

        HollowComposeUiRuntime(stylesheet = stylesheet).use { runtime ->
            val frame = runtime.frame(
                content = { UiXmlContent(xml) },
                width = 160f,
                height = 100f,
            )
            val text = frame.resolved.styles.keys.filterIsInstance<TextNode>().single()
            val aside = frame.resolved.styles.keys.single { it.id == "aside" }
            val command = frame.commands.filterIsInstance<DrawTextCommand>().single { it.node == text }

            assertEquals(36f, frame.layout[aside].rect.width)
            assertEquals(24f, frame.layout[aside].rect.height)
            assertEquals(frame.layout[text].content.x, frame.layout[aside].rect.x)
            assertTrue(command.layout.lines.first().x >= 42f)
            assertTrue(command.layout.lines.count { it.y < 24f && it.x >= 42f } > 1)
        }
    }

    @Test
    fun `popup is positioned relative to anchor node alignment`() {
        HollowComposeUiRuntime().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Box(modifier = Modifier.size(200.px, 120.px)) {
                        Box(id = "anchor", modifier = Modifier.then(Modifier.position(50.px, 10.px), Modifier.size(40.px, 20.px)))
                        Popup(
                            id = "popup",
                            anchor = UiPopupAnchor.Node("anchor"),
                            alignment = UiPopupAlignment(offsetY = 4f),
                            modifier = Modifier.size(60.px, 18.px),
                        )
                    }
                },
                width = 200f,
                height = 120f,
            )
            val popup = frame.resolved.styles.keys.single { it.id == "popup" }

            assertEquals(50f, frame.layout[popup].rect.x)
            assertEquals(34f, frame.layout[popup].rect.y)
        }
    }

    @Test
    fun `cursor popup follows pointer bindings`() {
        HollowComposeUiRuntime().use { runtime ->
            runtime.setContent {
                Box(modifier = Modifier.size(200.px, 120.px)) {
                    Popup(
                        id = "popup",
                        anchor = UiPopupAnchor.Cursor(),
                        alignment = UiPopupAlignment.Cursor,
                        modifier = Modifier.size(30.px, 10.px),
                    )
                }
            }
            val first = runtime.frame(200f, 120f, bindings = UiBindingContext().withPointer(10f, 20f))
            val firstPopup = first.resolved.styles.keys.single { it.id == "popup" }
            val second = runtime.frame(200f, 120f, bindings = UiBindingContext().withPointer(50f, 60f))
            val secondPopup = second.resolved.styles.keys.single { it.id == "popup" }

            assertEquals(18f, first.layout[firstPopup].rect.x)
            assertEquals(28f, first.layout[firstPopup].rect.y)
            assertEquals(58f, second.layout[secondPopup].rect.x)
            assertEquals(68f, second.layout[secondPopup].rect.y)
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
