import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.ui.BeginLayerCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawBackdropFilterCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawBoxCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawImageCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawShadowCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawTextCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawScrollbarCommand
import ru.hollowhorizon.hollowengine.client.ui.EndLayerCommand
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.HollowUi
import ru.hollowhorizon.hollowengine.client.ui.HollowUiFrame
import ru.hollowhorizon.hollowengine.client.ui.HollowUiRuntime
import ru.hollowhorizon.hollowengine.client.ui.LayoutType
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.ScrollbarOrientation
import ru.hollowhorizon.hollowengine.client.ui.ScriptEventModifier
import ru.hollowhorizon.hollowengine.client.ui.TextNode
import ru.hollowhorizon.hollowengine.client.ui.UiBindingContext
import ru.hollowhorizon.hollowengine.client.ui.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.UiAlign
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import ru.hollowhorizon.hollowengine.client.ui.UiEventPayloadTemplate
import ru.hollowhorizon.hollowengine.client.ui.UiEventSink
import ru.hollowhorizon.hollowengine.client.ui.UiFilterEffect
import ru.hollowhorizon.hollowengine.client.ui.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.UiLength
import ru.hollowhorizon.hollowengine.client.ui.UiNodeKeys
import ru.hollowhorizon.hollowengine.client.ui.UiPaint
import ru.hollowhorizon.hollowengine.client.ui.PushClipCommand
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.UiTextRun
import ru.hollowhorizon.hollowengine.client.ui.dispatch
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import ru.hollowhorizon.hollowengine.client.ui.percent
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.setClosingState
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiClientScript
import ru.hollowhorizon.hollowengine.client.ui.scripting.UiClientScriptRunner
import ru.hollowhorizon.hollowengine.client.ui.xml.UiResourceLoader
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlOptions
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUi
import ru.hollowhorizon.hollowengine.client.ui.bold
import ru.hollowhorizon.hollowengine.client.ui.italic
import ru.hollowhorizon.hollowengine.client.ui.underline
import ru.hollowhorizon.hollowengine.client.ui.strikethrough
import ru.hollowhorizon.hollowengine.client.ui.code
import ru.hollowhorizon.hollowengine.client.ui.link
import ru.hollowhorizon.hollowengine.client.ui.fontSize
import ru.hollowhorizon.hollowengine.client.ui.fontFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiFrameworkTests {
    @Test
    fun `align positions element inside parent on both axes`() {
        lateinit var child: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(40.px, 20.px),
            ),
        ) {
            child = Box(
                modifier = Modifier.then(
                    Modifier.size(10.px, 6.px),
                    Modifier.align(UiAlign.CENTER, UiAlign.CENTER),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 40f, 20f)
        val rect = frame.layout[child].rect

        assertEquals(15f, rect.x)
        assertEquals(7f, rect.y)
    }

    @Test
    fun `align keeps margin as visual offset from aligned edge`() {
        lateinit var child: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(40.px, 20.px),
            ),
        ) {
            child = Box(
                modifier = Modifier.then(
                    Modifier.size(10.px, 6.px),
                    Modifier.margin(5.px, 0.px, 0.px, 0.px),
                    Modifier.align(UiAlign.START, UiAlign.CENTER),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 40f, 20f)
        val rect = frame.layout[child].rect

        assertEquals(5f, rect.x)
        assertEquals(7f, rect.y)
    }

    @Test
    fun `aspect ratio resolves auto side from proportional height`() {
        lateinit var avatar: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(200.px, 100.px),
            ),
        ) {
            avatar = Box(
                modifier = Modifier.then(
                    Modifier.size(UiLength.Auto, 80.percent),
                    Modifier.aspectRatio(1f),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 200f, 100f)
        val rect = frame.layout[avatar].rect

        assertEquals(80f, rect.width)
        assertEquals(80f, rect.height)
    }

    @Test
    fun `row layout keeps flow positions when align items and margins are combined`() {
        lateinit var first: BoxNode
        lateinit var second: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(120.px, 50.px),
                Modifier.padding(10.px),
                Modifier.gap(6.px),
                Modifier.alignItems(UiAlign.CENTER, UiAlign.CENTER),
            ),
        ) {
            first = Box(
                modifier = Modifier.then(
                    Modifier.size(20.px, 10.px),
                    Modifier.margin(4.px, 0.px, 2.px, 0.px),
                ),
            )
            second = Box(modifier = Modifier.size(20.px, 10.px))
        }

        val frame = HollowUiRuntime().frame(root, 120f, 50f)
        val firstRect = frame.layout[first].rect
        val secondRect = frame.layout[second].rect

        assertEquals(38f, firstRect.x)
        assertEquals(20f, firstRect.y)
        assertTrue(firstRect.x + firstRect.width <= secondRect.x, "Row children should not overlap")
    }

    @Test
    fun `column layout keeps flow positions when align items and padding are combined`() {
        lateinit var first: BoxNode
        lateinit var second: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.COLUMN),
                Modifier.size(80.px, 100.px),
                Modifier.padding(10.px),
                Modifier.gap(4.px),
                Modifier.alignItems(UiAlign.END, UiAlign.CENTER),
            ),
        ) {
            first = Box(modifier = Modifier.size(20.px, 10.px))
            second = Box(modifier = Modifier.size(20.px, 10.px))
        }

        val frame = HollowUiRuntime().frame(root, 80f, 100f)
        val firstRect = frame.layout[first].rect
        val secondRect = frame.layout[second].rect

        assertEquals(50f, firstRect.x)
        assertEquals(38f, firstRect.y)
        assertEquals(firstRect.x, secondRect.x)
        assertTrue(firstRect.y + firstRect.height <= secondRect.y, "Column children should not overlap")
    }

    @Test
    fun `child align overrides align items on flex cross axis without breaking main axis flow`() {
        lateinit var first: BoxNode
        lateinit var second: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(80.px, 40.px),
                Modifier.alignItems(UiAlign.CENTER, UiAlign.CENTER),
            ),
        ) {
            first = Box(
                modifier = Modifier.then(
                    Modifier.size(20.px, 10.px),
                    Modifier.align(UiAlign.AUTO, UiAlign.END),
                ),
            )
            second = Box(modifier = Modifier.size(20.px, 10.px))
        }

        val frame = HollowUiRuntime().frame(root, 80f, 40f)
        val firstRect = frame.layout[first].rect
        val secondRect = frame.layout[second].rect

        assertEquals(20f, firstRect.x)
        assertEquals(40f, secondRect.x)
        assertTrue(firstRect.y > secondRect.y, "Child align should override parent cross-axis alignment")
    }

    @Test
    fun `column child align overrides align items on cross axis without breaking main axis flow`() {
        lateinit var first: BoxNode
        lateinit var second: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.COLUMN),
                Modifier.size(80.px, 60.px),
                Modifier.alignItems(UiAlign.CENTER, UiAlign.START),
            ),
        ) {
            first = Box(
                modifier = Modifier.then(
                    Modifier.size(20.px, 10.px),
                    Modifier.align(UiAlign.END, UiAlign.AUTO),
                ),
            )
            second = Box(modifier = Modifier.size(20.px, 10.px))
        }

        val frame = HollowUiRuntime().frame(root, 80f, 60f)
        val firstRect = frame.layout[first].rect
        val secondRect = frame.layout[second].rect

        assertEquals(60f, firstRect.x)
        assertEquals(0f, firstRect.y)
        assertEquals(10f, secondRect.y)
        assertTrue(firstRect.x > secondRect.x, "Child align should override parent cross-axis alignment")
    }

    @Test
    fun `row grow distributes remaining content width after fixed siblings and gaps`() {
        lateinit var fixed: BoxNode
        lateinit var grown: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(100.px, 30.px),
                Modifier.padding(5.px),
                Modifier.gap(10.px),
            ),
        ) {
            fixed = Box(modifier = Modifier.size(20.px, 10.px))
            grown = Box(
                modifier = Modifier.then(
                    Modifier.size(UiLength.Auto, 10.px),
                    Modifier.grow(1f),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 100f, 30f)

        assertEquals(5f, frame.layout[fixed].rect.x)
        assertEquals(35f, frame.layout[grown].rect.x)
        assertEquals(60f, frame.layout[grown].rect.width)
    }

    @Test
    fun `column grow with percent size uses remaining height once`() {
        lateinit var tabs: BoxNode
        lateinit var content: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.COLUMN),
                Modifier.size(300.px, 200.px),
                Modifier.padding(14.px),
                Modifier.gap(10.px),
            ),
        ) {
            tabs = Box(modifier = Modifier.size(UiLength.Auto, 47.px))
            content = Box(
                modifier = Modifier.then(
                    Modifier.size(100.percent, 100.percent),
                    Modifier.grow(1f),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 300f, 200f)

        assertEquals(14f, frame.layout[tabs].rect.y)
        assertEquals(71f, frame.layout[content].rect.y)
        assertEquals(115f, frame.layout[content].rect.height)
    }

    @Test
    fun `row percentage child shrinks to remaining width after fixed siblings`() {
        lateinit var fixed: BoxNode
        lateinit var percent: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(100.px, 30.px),
            ),
        ) {
            fixed = Box(modifier = Modifier.size(20.px, 10.px))
            percent = Box(modifier = Modifier.size(100.percent, 10.px))
        }

        val frame = HollowUiRuntime().frame(root, 100f, 30f)

        assertEquals(0f, frame.layout[fixed].rect.x)
        assertEquals(20f, frame.layout[percent].rect.x)
        assertEquals(80f, frame.layout[percent].rect.width)
    }

    @Test
    fun `column flow stretches auto-width children by default`() {
        lateinit var child: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.COLUMN),
                Modifier.size(120.px, 50.px),
                Modifier.padding(10.px),
            ),
        ) {
            child = Box(modifier = Modifier.size(UiLength.Auto, 10.px))
        }

        val frame = HollowUiRuntime().frame(root, 120f, 50f)

        assertEquals(10f, frame.layout[child].rect.x)
        assertEquals(100f, frame.layout[child].rect.width)
    }

    @Test
    fun `default transform origin is the element center`() {
        lateinit var child: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(100.px, 100.px),
            ),
        ) {
            child = Box(
                modifier = Modifier.then(
                    Modifier.size(20.px, 20.px),
                    Modifier.scale(2f),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 100f, 100f)
        val origin = frame.layout[child].worldTransform.transform(0f, 0f)

        assertEquals(-10f, origin.x)
        assertEquals(-10f, origin.y)
    }

    @Test
    fun `free scrollable container emits horizontal scrollbar for positioned overflow`() {
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(100.px, 60.px),
                Modifier.input(scrollable = true),
            ),
        ) {
            Box(modifier = Modifier.then(Modifier.position(120.px, 0.px), Modifier.size(30.px, 20.px)))
        }

        val frame = HollowUiRuntime().frame(root, 100f, 60f)
        val scrollbar = assertIs<DrawScrollbarCommand>(
            frame.commands.first { it is DrawScrollbarCommand && it.orientation == ScrollbarOrientation.HORIZONTAL }
        )

        assertEquals(root, scrollbar.node)
        assertTrue(frame.layout[root].scrollRange.x > 0f)
    }

    @Test
    fun `dialogue style lays out fit profile and wrapped message exactly`() {
        lateinit var dialogue: BoxNode
        lateinit var profile: BoxNode
        lateinit var nick: TextNode
        lateinit var character: BoxNode
        lateinit var message: TextNode
        val stylesheet = compileHss(
            """
            .root {
                layout: row;
                size: 80% 78px;
                border: 2px #FFFFFF;
                margin: 2%;
                align: center center;
            }

            .character {
                size: 100% 100%;
                background: image("hollowengine:textures/gui/icons/logo.png");
                fit: contain;
                margin: 10px 0px 0px 6px;
            }

            .message {
                scale: 1.5;
                margin: 10px;
                align: end center;
            }
            """.trimIndent()
        )
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(200.px, 100.px),
            ),
        ) {
            dialogue = Box(tags = listOf("root")) {
                profile = Box(
                    modifier = Modifier.then(
                        Modifier.layout(LayoutType.COLUMN),
                        Modifier.size(UiLength.Auto, UiLength.Auto),
                    ),
                ) {
                    nick = Text("Hollow")
                    character = Box(tags = listOf("character"))
                }
                message = Text("Привет, теперь можно делать диалоговые окна!", tags = listOf("message"))
            }
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 200f, 100f)
        val dialogueRect = frame.layout[dialogue].rect
        val profileRect = frame.layout[profile].rect
        val nickRect = frame.layout[nick].rect
        val characterRect = frame.layout[character].rect
        val messageRect = frame.layout[message].rect

        assertEquals(20f, dialogueRect.x)
        assertEquals(11f, dialogueRect.y)
        assertEquals(160f, dialogueRect.width)
        assertEquals(78f, dialogueRect.height)
        assertEquals(22f, profileRect.x)
        assertEquals(13f, profileRect.y)
        assertEquals(38f, profileRect.width)
        assertEquals(52f, profileRect.height)
        assertEquals(profileRect.x, nickRect.x)
        assertEquals(38f, nickRect.width)
        assertEquals(profileRect.x + 6f, characterRect.x)
        assertEquals(profileRect.y + 20f, characterRect.y)
        assertEquals(32f, characterRect.width)
        assertEquals(32f, characterRect.height)
        assertEquals(70f, messageRect.x)
        assertEquals(98f, messageRect.width)
        assertTrue(messageRect.y >= dialogueRect.y + 2f, "Message should stay inside root content vertically")
        assertTrue(messageRect.x + messageRect.width <= dialogueRect.x + dialogueRect.width - 2f)
    }

    @Test
    fun `free layout aligns children inside padded content and respects margins`() {
        lateinit var child: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.FREE),
                Modifier.size(100.px, 80.px),
                Modifier.padding(10.px),
                Modifier.alignItems(UiAlign.END, UiAlign.END),
            ),
        ) {
            child = Box(
                modifier = Modifier.then(
                    Modifier.size(20.px, 10.px),
                    Modifier.margin(0.px, 0.px, 5.px, 7.px),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 100f, 80f)
        val rect = frame.layout[child].rect

        assertEquals(65f, rect.x)
        assertEquals(53f, rect.y)
    }

    @Test
    fun `hss selectors resolve state rules over base rules`() {
        val stylesheet = compileHss(
            """
            .dialogue-box {
                layout: column;
                padding: 12px;
                gap: 8px;
                opacity: 0.5;
                background: rgba(10, 12, 20, 0.85);
            }

            .dialogue-box:hover {
                scale: 1.02;
                opacity: 1.0;
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("dialogue-box")).apply {
            states += UiState.HOVER
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 320f, 180f)
        val style = frame.resolved[root]

        assertEquals(LayoutType.COLUMN, style.layout)
        assertEquals(1f, style.opacity)
        assertEquals(1.02f, style.transform.scale.x)
        assertEquals(12f, (style.padding.left as UiLength.Px).value)
    }

    @Test
    fun `hss attribute selector lists drive transition targets`() {
        val stylesheet = compileHss(
            """
            .box {
                transition: all 100ms linear;
            }

            .box[my-custom-state="opening"],
            .box[my-custom-state="closing"] {
                opacity: 0;
                transform: translateY(-20px);
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("box")).apply {
            attributes["my-custom-state"] = "opening"
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        root.attributes["my-custom-state"] = "ready"
        val start = runtime.frame(root, 100f, 40f, nowMillis = 0L)
        val half = runtime.frame(root, 100f, 40f, nowMillis = 50L)

        assertEquals(0f, start.resolved[root].opacity)
        assertEquals(0.5f, half.resolved[root].opacity, 0.01f)
        assertEquals(-10f, half.resolved[root].transform.translate.y, 0.01f)
    }

    @Test
    fun `xml preserves custom attributes separately from styles and events`() {
        val root = parseUi(
            """
            <box id="panel" tags="box" my-custom-state="opening" width="10px" onClick="consume()" />
            """.trimIndent(),
        )

        assertEquals("opening", root.attributes["my-custom-state"])
        assertFalse("width" in root.attributes)
        assertFalse("onClick" in root.attributes)
    }

    @Test
    fun `inline modifiers override stylesheet rules`() {
        val stylesheet = compileHss(
            """
            #dialog-root {
                opacity: 0.25;
                width: 10px;
            }
            """.trimIndent()
        )
        val root = HollowUi(
            id = "dialog-root",
            modifier = Modifier.then(Modifier.opacity(0.8f), Modifier.size(50.px, 20.px)),
        )

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 100f)

        assertEquals(0.8f, frame.resolved[root].opacity)
        assertEquals(50f, (frame.resolved[root].size.width as UiLength.Px).value)
    }

    @Test
    fun `style modifier applies stylesheet to node and descendants`() {
        val scoped = compileHss(
            """
            box {
                padding: 7px;
            }

            text {
                color: #000000;
            }
            """.trimIndent()
        )
        val root = HollowUi(modifier = Modifier.style(scoped)) {
            Text("Hello")
        }
        val child = root.children.single()

        val frame = HollowUiRuntime().frame(root, 100f, 100f)

        assertEquals(7f, (frame.resolved[root].padding.left as UiLength.Px).value)
        assertEquals(0f, frame.resolved[child].foreground.red)
    }

    @Test
    fun `event modifiers dispatch click and drag payloads`() {
        val events = mutableListOf<CompoundTag>()
        val root = HollowUi(
            id = "button",
            modifier = Modifier.then(
                Modifier.emitOnClick(
                    UiEventPayloadTemplate.parse("{event:\"pressed\",button:<it.button>,id:<it.id>}"),
                    UiEventSink { events += it },
                ),
                Modifier.onDrag { event ->
                    events += CompoundTag().apply {
                        putString("event", "dragged")
                        putFloat("dx", event.deltaX)
                    }
                },
            ),
        )

        root.dispatch(UiEvent(UiEventKind.CLICK, root, button = 1))
        root.dispatch(UiEvent(UiEventKind.DRAG, root, deltaX = 3f))

        assertEquals("pressed", events[0].getString("event"))
        assertEquals(1, events[0].getInt("button"))
        assertEquals("button", events[0].getString("id"))
        assertEquals("dragged", events[1].getString("event"))
        assertEquals(3f, events[1].getFloat("dx"))
    }

    @Test
    fun `ui xml builds builtins imports modifiers and event templates`() {
        val emitted = mutableListOf<CompoundTag>()
        val resources = UiResourceLoader { location ->
            when (location) {
                "hollowengine:ui/elements/my_custom_button.ui" -> "<box id='custom' size='40px 10px'><text>Custom</text></box>"
                else -> error("Unexpected resource $location")
            }
        }

        val root = parseUi(
            """
            <import element="hollowengine:ui/elements/my_custom_button.ui" named="custom_button" />
            <box layout="auto" size="100% 100%">
                <box id="hello" onClick='{event:"pressed";button:"hello";mouse:<it.button>}'>
                    <text>Hello</text>
                </box>
                <custom_button margin="2px" />
            </box>
            """.trimIndent(),
            UiXmlOptions(resources = resources, eventSink = UiEventSink { emitted += it }),
        )

        val frame = HollowUiRuntime().frame(root, 200f, 120f)
        val button = root.children.first { it.id == "hello" }
        val custom = root.children.first { it.id == "custom" }

        button.dispatch(UiEvent(UiEventKind.CLICK, button, button = 2))

        assertEquals(LayoutType.COLUMN, frame.resolved[root].layout)
        assertEquals(1f, (frame.resolved[root].size.width as UiLength.Percent).value)
        assertEquals(2f, (frame.resolved[custom].margin.left as UiLength.Px).value)
        assertEquals("pressed", emitted.single().getString("event"))
        assertEquals("hello", emitted.single().getString("button"))
        assertEquals(2, emitted.single().getInt("mouse"))
    }

    @Test
    fun `ui xml inline event handler executes katari script`() {
        val emitted = mutableListOf<CompoundTag>()
        val root = parseUi(
            """
            <box>
                <box id="accept" onPressed='emit(struct { event: "pressed", button: it.id, mouse: it.mouseButton, value: vars.string("value") })'>
                    <text>Accept</text>
                </box>
            </box>
            """.trimIndent(),
            UiXmlOptions(eventSink = UiEventSink { emitted += it }),
        )

        val button = root.children.single { it.id == "accept" }
        val event = UiEvent(UiEventKind.PRESS, button, button = 1)
        event.variables.putString("value", "from-server")
        UiNodeKeys.assign(root)
        val scripts = button.modifiers.filterIsInstance<ScriptEventModifier>().map { modifier ->
            UiClientScript.Inline(
                modifier.kind,
                modifier.source,
                targetKey = UiNodeKeys.key(button),
                sink = modifier.sink,
            )
        }
        UiClientScriptRunner.prepare(scripts, root, UiEventSink.None, event.variables).dispatch(event, root, event.variables)

        assertEquals("pressed", emitted.single().getString("event"))
        assertEquals("accept", emitted.single().getString("button"))
        assertEquals(1, emitted.single().getInt("mouse"))
        assertEquals("from-server", emitted.single().getString("value"))
    }

    @Test
    fun `ui xml builds structured text children without value attribute formatting`() {
        val root = parseUi(
            """
            <box>
                <text id="dialog">This <b>text</b> has <u>formatting</u>.<pause delay="2s" /> Done.</text>
            </box>
            """.trimIndent(),
        )
        val text = root.children.single() as TextNode

        val command = HollowUiRuntime().frame(root, 240f, 80f).textCommand(text)
        val runs = command.layout.lines.flatMap { it.fragments }.filterIsInstance<UiTextRun>()

        assertEquals("This text has formatting. Done.", command.text)
        assertTrue(runs.first { it.text == "text" }.style.bold)
        assertTrue(runs.first { it.text == "formatting" }.style.underline)
    }

    @Test
    fun `ui xml ignores source newlines inside text unless br is used`() {
        val root = parseUi(
            """
            <box>
                <text id="dialog">Hello
                    world<br/>Next</text>
            </box>
            """.trimIndent(),
        )
        val text = root.children.single() as TextNode

        val command = HollowUiRuntime().frame(root, 240f, 80f).textCommand(text)

        assertEquals("Hello world\nNext", command.text)
        assertEquals(listOf("Hello world", "Next"), command.layout.lines.map { it.text })
    }

    @Test
    fun `typing style reveals text over time and accounts for pauses`() {
        val root = parseUi(
            """
            <box>
                <text id="dialog" typing="auto linear">ab<pause delay="100ms" />cd</text>
            </box>
            """.trimIndent(),
        )
        val text = root.children.single() as TextNode
        val runtime = HollowUiRuntime()

        assertEquals("", runtime.frame(root, 240f, 80f, nowMillis = 0L).textCommand(text).text)
        assertEquals("ab", runtime.frame(root, 240f, 80f, nowMillis = 80L).textCommand(text).text)
        assertEquals("ab", runtime.frame(root, 240f, 80f, nowMillis = 180L).textCommand(text).text)
        assertEquals("abcd", runtime.frame(root, 240f, 80f, nowMillis = 260L).textCommand(text).text)
    }

    @Test
    fun `typing keeps final word wrap while revealing a partial word`() {
        val root = parseUi(
            """
            <box>
                <text id="dialog" size="24px auto" typing="700ms linear">aa bbbb</text>
            </box>
            """.trimIndent(),
        )
        val text = root.children.single() as TextNode
        val runtime = HollowUiRuntime()

        runtime.frame(root, 80f, 80f, nowMillis = 0L)
        val command = runtime.frame(root, 80f, 80f, nowMillis = 400L).textCommand(text)

        assertEquals("aa b", command.text)
        assertEquals(listOf("aa", "b"), command.layout.lines.map { it.text })
    }

    @Test
    fun `ui client script dispatches matching event declarations`() {
        val emitted = mutableListOf<CompoundTag>()
        val root = HollowUi {
            Box(id = "accept", tags = listOf("primary"))
            Box(id = "decline", tags = listOf("secondary"))
        }
        val accept = root.children.single { it.id == "accept" }
        val decline = root.children.single { it.id == "decline" }
        val script = UiClientScript.Resource(
            "handler.ktr",
            """
            onClick(".primary") {
                emit(struct { event: "clicked", button: it.id, mouse: it.mouseButton })
            }
            onClick(".secondary") {
                emit(struct { event: "clicked", button: it.id, mouse: it.mouseButton })
            }
            onRelease {
                emit(struct { event: "released" })
            }
            """.trimIndent(),
        )

        val prepared = UiClientScriptRunner.prepare(listOf(script), root, UiEventSink { emitted += it }, CompoundTag())
        val frame = HollowUiRuntime().frame(root, 200f, 120f)
        prepared.dispatch(UiEvent(UiEventKind.CLICK, decline, button = 2), root, CompoundTag())

        assertTrue(frame.resolved[accept].input.clickable)
        assertTrue(frame.resolved[decline].input.clickable)
        assertEquals(1, emitted.size)
        assertEquals("clicked", emitted.single().getString("event"))
        assertEquals("decline", emitted.single().getString("button"))
        assertEquals(2, emitted.single().getInt("mouse"))
    }

    @Test
    fun `prepared inline script dispatches only for its target node`() {
        val emitted = mutableListOf<CompoundTag>()
        val root = HollowUi {
            Box(id = "accept")
            Box(id = "decline")
        }
        UiNodeKeys.assign(root)
        val accept = root.children.single { it.id == "accept" }
        val decline = root.children.single { it.id == "decline" }
        val script = UiClientScript.Inline(
            UiEventKind.CLICK,
            """emit(struct { event: "clicked", button: it.id })""",
            targetKey = UiNodeKeys.key(accept),
        )
        val prepared = UiClientScriptRunner.prepare(listOf(script), root, UiEventSink { emitted += it }, CompoundTag())

        prepared.dispatch(UiEvent(UiEventKind.CLICK, decline), root, CompoundTag())
        prepared.dispatch(UiEvent(UiEventKind.CLICK, accept), root, CompoundTag())

        assertEquals(1, emitted.size)
        assertEquals("accept", emitted.single().getString("button"))
    }

    @Test
    fun `ui client script can modify node attributes for hss selectors`() {
        lateinit var panel: BoxNode
        val root = HollowUi {
            panel = Box(id = "panel", tags = listOf("box"))
        }
        panel.attributes["my-custom-state"] = "opening"
        val script = UiClientScript.Inline(
            UiEventKind.CLICK,
            """gui.modify("panel", "my-custom-state", "ready")""",
        )
        val prepared = UiClientScriptRunner.prepare(listOf(script), root, UiEventSink.None, CompoundTag())

        prepared.dispatch(UiEvent(UiEventKind.CLICK, panel), root, CompoundTag())

        assertEquals("ready", panel.attributes["my-custom-state"])
    }

    @Test
    fun `free layout places children by explicit position and propagates hit testing`() {
        lateinit var child: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            child = Box(
                id = "quest",
                modifier = Modifier.then(
                    Modifier.position(120.px, 40.percent),
                    Modifier.size(30.px, 20.px),
                    Modifier.input(clickable = true),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 800f, 1000f)
        val rect = frame.layout[child].rect
        val hit = frame.hitTest(121f, 401f)

        assertEquals(120f, rect.x)
        assertEquals(400f, rect.y)
        assertNotNull(hit)
        assertEquals(child, hit.node)
    }

    @Test
    fun `hss bindings resolve compound tag paths in render commands`() {
        val stylesheet = compileHss(
            """
            .portrait {
                background: image("{data.character.icon}");
                size: 32px 32px;
            }
            """.trimIndent()
        )
        val root = HollowUi {
            Box(tags = listOf("portrait"))
        }
        val tag = CompoundTag().apply {
            put("data", CompoundTag().apply {
                put("character", CompoundTag().apply {
                    putString("icon", "hollowengine:textures/gui/npc_menu/character.png")
                })
            })
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 100f, UiBindingContext(tag))
        val draw = assertIs<DrawBoxCommand>(frame.commands.first { it is DrawBoxCommand && it.node.tags.contains("portrait") })
        val paint = assertIs<UiResolvedPaint.Image>(draw.paint)

        assertEquals("hollowengine:textures/gui/npc_menu/character.png", paint.source)
    }

    @Test
    fun `border parser preserves rgba functions with spaces`() {
        val stylesheet = compileHss(
            """
            .card {
                border: 1px rgba(120, 140, 170, 0.38);
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("card"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 100f)

        assertEquals(1f, (frame.resolved[root].border.width.left as UiLength.Px).value)
        assertEquals(120f / 255f, frame.resolved[root].border.color.red)
        assertEquals(0.38f, frame.resolved[root].border.color.alpha)
    }

    @Test
    fun `scrollable nodes emit scrollbars and offset children`() {
        lateinit var scroller: BoxNode
        lateinit var row: BoxNode
        val root = HollowUi(modifier = Modifier.size(120.px, 80.px)) {
            scroller = Box(
                tags = listOf("scroll"),
                modifier = Modifier.then(
                    Modifier.size(100.px, 40.px),
                    Modifier.input(scrollable = true),
                ),
            ) {
                row = Box(modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(80.px, 30.px)))
            }
        }
        val runtime = HollowUiRuntime()

        val initial = runtime.frame(root, 120f, 80f)
        runtime.setScrollImmediate(scroller, y = 24f)
        val scrolled = runtime.frame(root, 120f, 80f)

        assertEquals(true, initial.commands.any { it is DrawScrollbarCommand })
        assertEquals(initial.layout[row].rect.y - 24f, scrolled.layout[row].rect.y)
        assertEquals(scrolled.layout[row].rect.y, scrolled.layout[row].worldTransform.transform(0f, 0f).y)
    }

    @Test
    fun `scrollbars reserve space outside content viewport`() {
        lateinit var scroller: BoxNode
        val root = HollowUi(modifier = Modifier.size(120.px, 80.px)) {
            scroller = Box(
                modifier = Modifier.then(
                    Modifier.size(100.px, 40.px),
                    Modifier.input(scrollable = true),
                ),
            ) {
                Box(modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(80.px, 30.px)))
            }
        }

        val frame = HollowUiRuntime().frame(root, 120f, 80f)
        val layout = frame.layout[scroller]
        val scrollbar = assertIs<DrawScrollbarCommand>(frame.commands.first { it is DrawScrollbarCommand })

        assertTrue(layout.content.width < layout.scrollArea.width, "Expected content viewport to reserve scrollbar space")
        assertTrue(
            scrollbar.track.x >= layout.content.x + layout.content.width,
            "Expected scrollbar track to be outside content viewport",
        )
    }

    @Test
    fun `vertical scrollbar reserve does not force horizontal overflow`() {
        lateinit var scroller: BoxNode
        lateinit var row: BoxNode
        val root = HollowUi(modifier = Modifier.size(120.px, 80.px)) {
            scroller = Box(
                modifier = Modifier.then(
                    Modifier.size(100.px, 40.px),
                    Modifier.input(scrollable = true),
                ),
            ) {
                row = Box(modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(100.percent, 30.px)))
            }
        }

        val frame = HollowUiRuntime().frame(root, 120f, 80f)
        val layout = frame.layout[scroller]
        val scrollbars = frame.commands.filterIsInstance<DrawScrollbarCommand>()

        assertEquals(listOf(ScrollbarOrientation.VERTICAL), scrollbars.map { it.orientation })
        assertTrue(layout.content.width < layout.scrollArea.width, "Expected vertical scrollbar to reserve content width")
        assertEquals(layout.content.width, frame.layout[row].rect.width, 0.01f)
        assertEquals(0f, layout.scrollRange.x)
    }

    @Test
    fun `scroll state survives rebuilt ui tree by stable node id`() {
        data class ScrollTree(
            val root: BoxNode,
            val scroller: BoxNode,
            val row: BoxNode,
        )

        fun tree(): ScrollTree {
            lateinit var scroller: BoxNode
            lateinit var row: BoxNode
            val root = HollowUi(modifier = Modifier.size(120.px, 80.px)) {
                scroller = Box(
                    id = "stable-scroll",
                    modifier = Modifier.then(Modifier.size(100.px, 40.px), Modifier.input(scrollable = true)),
                ) {
                    row = Box(id = "stable-row", modifier = Modifier.then(Modifier.position(0.px, 90.px), Modifier.size(80.px, 30.px)))
                }
            }
            return ScrollTree(root, scroller, row)
        }

        val runtime = HollowUiRuntime()
        val firstTree = tree()
        val first = runtime.frame(firstTree.root, 120f, 80f)
        runtime.setScrollImmediate(firstTree.scroller, y = 24f)
        val secondTree = tree()
        val second = runtime.frame(secondTree.root, 120f, 80f)

        assertEquals(first.layout[firstTree.row].rect.y - 24f, second.layout[secondTree.row].rect.y)
    }

    @Test
    fun `transitions survive rebuilt ui tree by stable node id`() {
        val stylesheet = compileHss(
            """
            #button {
                opacity: 0.2;
                transition: opacity 100ms linear;
            }

            #button:hover {
                opacity: 1.0;
            }
            """.trimIndent()
        )
        fun button(hovered: Boolean) = HollowUi(id = "button").apply {
            if (hovered) states += UiState.HOVER
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(button(false), 100f, 40f, nowMillis = 0L)
        runtime.frame(button(true), 100f, 40f, nowMillis = 50L)
        val half = runtime.frame(button(true), 100f, 40f, nowMillis = 100L)

        assertEquals(0.6f, half.resolved[half.resolved.root].opacity, 0.01f)
    }

    @Test
    fun `transitions retarget from current rendered style`() {
        val stylesheet = compileHss(
            """
            #button {
                opacity: 0.0;
                transition: opacity 100ms linear;
            }

            #button:hover {
                opacity: 1.0;
            }
            """.trimIndent()
        )
        fun button(hovered: Boolean) = HollowUi(id = "button").apply {
            if (hovered) states += UiState.HOVER
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(button(false), 100f, 40f, nowMillis = 0L)
        runtime.frame(button(true), 100f, 40f, nowMillis = 50L)
        val halfIn = runtime.frame(button(true), 100f, 40f, nowMillis = 100L)
        val retargeted = runtime.frame(button(false), 100f, 40f, nowMillis = 100L)
        val halfOut = runtime.frame(button(false), 100f, 40f, nowMillis = 150L)

        assertEquals(0.5f, halfIn.resolved[halfIn.resolved.root].opacity, 0.01f)
        assertEquals(0.5f, retargeted.resolved[retargeted.resolved.root].opacity, 0.01f)
        assertEquals(0.25f, halfOut.resolved[halfOut.resolved.root].opacity, 0.01f)
    }

    @Test
    fun `hit testing remains stable while transform transition runs`() {
        val stylesheet = compileHss(
            """
            #button {
                size: 10px 10px;
                hoverable: true;
                transition: scale 100ms linear;
            }

            #button:hover {
                scale: 2.0;
            }
            """.trimIndent()
        )
        fun button(hovered: Boolean): BoxNode {
            lateinit var button: BoxNode
            HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
                button = Box(id = "button")
            }
            if (hovered) button.states += UiState.HOVER
            return button
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        val initial = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            Node(button(false))
        }
        val hovered = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            Node(button(true))
        }

        runtime.frame(initial, 40f, 40f, nowMillis = 0L)
        val frame = runtime.frame(hovered, 40f, 40f, nowMillis = 50L)

        assertNotNull(frame.hitTest(5f, 5f))
    }

    @Test
    fun `layout position snaps while visual transform transition runs`() {
        val stylesheet = compileHss(
            """
            #node {
                size: 20px 20px;
                transition: scale 200ms linear;
            }

            #node:hover {
                scale: 1.2;
            }
            """.trimIndent()
        )
        fun tree(x: Int, hovered: Boolean): Pair<BoxNode, BoxNode> {
            lateinit var node: BoxNode
            val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
                node = Box(
                    id = "node",
                    modifier = Modifier.position(x.px, 0.px),
                )
            }
            if (hovered) node.states += UiState.HOVER
            return root to node
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)
        val (firstRoot, _) = tree(0, hovered = false)
        runtime.frame(firstRoot, 100f, 40f, nowMillis = 0L)
        val (secondRoot, secondNode) = tree(40, hovered = true)
        val frame = runtime.frame(secondRoot, 100f, 40f, nowMillis = 50L)

        assertEquals(40f, frame.layout[secondNode].rect.x)
    }

    @Test
    fun `runtime style attributes modified by scripts participate in transform transitions`() {
        val root = HollowUi(id = "dialog")
        val runtime = HollowUiRuntime()

        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        root.attributes["rotate"] = "0 0 90"
        runtime.frame(root, 100f, 40f, nowMillis = 50L)
        val frame = runtime.frame(root, 100f, 40f, nowMillis = 100L)
        val z = frame.resolved[root].transform.rotate.z

        assertTrue(z > 0f, "Expected rotate to start animating")
        assertTrue(z < 90f, "Expected rotate transition instead of an immediate snap")
    }

    @Test
    fun `text flex item shrinks and wraps inside rows`() {
        lateinit var text: TextNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.layout(LayoutType.ROW),
                Modifier.size(120.px, 80.px),
                Modifier.gap(8.px),
            ),
        ) {
            Box(modifier = Modifier.size(20.px, 20.px))
            text = Text("This text should wrap instead of forcing horizontal overflow in a row.")
        }

        val frame = HollowUiRuntime().frame(root, 120f, 80f)
        val rect = frame.layout[text].rect

        assertTrue(rect.x + rect.width <= 120f, "Expected wrapped text to stay inside row, got $rect")
    }

    @Test
    fun `hss compiles gradients shadows filters and backface visibility`() {
        val stylesheet = compileHss(
            """
            .fx {
                size: 80px 40px;
                background: linear-gradient(135deg, rgba(20, 40, 80, 0.9), #c8ddff 80%);
                border-radius: 9px;
                shadow: 0px 10px 20px 2px rgba(0, 0, 0, 0.35);
                filter: grayscale(1) blur(2px);
                backdrop-filter: blur(8px) grayscale(0.25);
                backface-visibility: hidden;
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("fx"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 60f)
        val style = frame.resolved[root]
        val paint = assertIs<UiPaint.LinearGradient>(style.background)

        assertEquals(135f, paint.angleDegrees)
        assertEquals(2, paint.stops.size)
        assertEquals(9f, style.border.radius)
        assertEquals(1, style.shadows.size)
        assertEquals(10f, style.shadows.first().offset.y)
        assertTrue(style.filter.effects.any { it is UiFilterEffect.Grayscale })
        assertEquals(8f, style.backdropFilter.blurRadius())
        assertEquals(UiBackfaceVisibility.HIDDEN, style.backfaceVisibility)
    }

    @Test
    fun `hss side-specific margin and padding patch individual edges`() {
        val stylesheet = compileHss(
            """
            .panel {
                margin: 1px;
                margin-left: 6px;
                padding: 2px;
                padding-bottom: 8px;
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("panel"))

        val style = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 60f).resolved[root]

        assertEquals(6f, (style.margin.left as UiLength.Px).value)
        assertEquals(1f, (style.margin.right as UiLength.Px).value)
        assertEquals(8f, (style.padding.bottom as UiLength.Px).value)
        assertEquals(2f, (style.padding.top as UiLength.Px).value)
    }

    @Test
    fun `hss max width longhand does not constrain fit height`() {
        val stylesheet = compileHss(
            """
            .dialog {
                size: fit fit;
                max-width: 50%;
            }
            .message {
                size: fill fit;
            }
            """.trimIndent(),
        )
        lateinit var message: TextNode
        val root = HollowUi(tags = listOf("dialog")) {
            message = Text((1..20).joinToString(" ") { "word" }, tags = listOf("message"))
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 120f, 40f)

        assertEquals(60f, frame.layout[root].rect.width, 0.01f)
        assertEquals(frame.textCommand(message).layout.height, frame.layout[root].rect.height, 0.01f)
        assertTrue(frame.layout[root].rect.height > 40f)
    }

    @Test
    fun `fit row remeasures rich text height after max width constraint`() {
        val stylesheet = compileHss(
            """
            .dialog {
                layout: row;
                size: fit fit;
                max-width: 50%;
                padding: 12px;
                gap: 8px;
            }
            .portrait {
                size: 32px 32px;
            }
            .message {
                grow: 1;
                size: fill fit;
            }
            """.trimIndent(),
        )
        lateinit var message: TextNode
        val root = HollowUi(tags = listOf("dialog")) {
            Box(tags = listOf("portrait"))
            Box(tags = listOf("message")) {
                message = Text(
                    "Текст, длинный текст... Чтобы проверить большие слова. И как они выходят за границы. " +
                            "И выходит ли этот текст за границы вообще? А то щас он похоже нормально вывелся без странностей.",
                )
                message.content = parseUi(
                    """
                    <text>Текст, длинный текст... Чтобы проверить <size value="20">большие</size> слова. И как они выходят за границы. И выходит ли этот текст за границы вообще? А то щас он <size value="10">похоже</size> нормально вывелся без странностей.</text>
                    """.trimIndent(),
                ).children.single().let { (it as TextNode).content }
            }
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 320f, 240f)
        val textLayout = frame.textCommand(message).layout
        val dialogContentHeight = frame.layout[root].content.height

        assertTrue(textLayout.lines.size >= 6)
        assertTrue(dialogContentHeight >= textLayout.height, "Dialog content height $dialogContentHeight should contain text $textLayout")
    }

    @Test
    fun `hss keyframes animation interpolates transform and opacity`() {
        val stylesheet = compileHss(
            """
            @keyframes reveal {
                from {
                    opacity: 0.2;
                    rotate: 0 0 0;
                }
                to {
                    opacity: 1;
                    rotate: 0 0 100;
                }
            }

            .animated {
                animation: reveal 100ms linear forwards;
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("animated"))
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        val half = runtime.frame(root, 100f, 40f, nowMillis = 50L).resolved[root]

        assertEquals(0.6f, half.opacity, 0.01f)
        assertEquals(50f, half.transform.rotate.z, 0.01f)
    }

    @Test
    fun `hss comments are ignored in rules declarations and keyframes`() {
        val stylesheet = compileHss(
            """
            // leading line comment
            .dialog {
                opacity: 1; // declaration line comment
                background: /* inline block comment */ #FFFFFF;
            }

            .dialog:closing {
                opacity: 0 /* trailing block comment */;
            }

            @keyframes fadeOut {
                from { opacity: 1; }
                // keyframe line comment
                to { opacity: 0; }
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("dialog"))
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        val opened = runtime.frame(root, 100f, 40f, nowMillis = 0L).resolved[root]
        root.setClosingState(true)
        val closing = runtime.frame(root, 100f, 40f, nowMillis = 0L).resolved[root]

        assertEquals(1f, opened.opacity)
        assertEquals(0f, closing.opacity)
    }

    @Test
    fun `closing state starts one shot keyframe animation`() {
        val stylesheet = compileHss(
            """
            .dialog {
                opacity: 1;
            }

            .dialog:closing {
                animation: fadeOut 200ms linear forwards;
            }

            @keyframes fadeOut {
                from { opacity: 1; }
                to { opacity: 0; }
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("dialog"))
        val runtime = HollowUiRuntime(stylesheet = stylesheet)
        val opened = runtime.frame(root, 100f, 40f, nowMillis = 0L)

        root.setClosingState(true)
        val closingStart = runtime.frame(root, 100f, 40f, nowMillis = 0L)
        val closingHalf = runtime.frame(root, 100f, 40f, nowMillis = 100L)

        assertEquals(200L, closingStart.motionDurationMillis(opened))
        assertEquals(0.5f, closingHalf.resolved[root].opacity, 0.01f)
    }

    @Test
    fun `closing motion duration includes nested elements`() {
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
        lateinit var dialog: BoxNode
        val root = HollowUi {
            dialog = Box(tags = listOf("dialog"))
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)
        val opened = runtime.frame(root, 100f, 40f, nowMillis = 0L)

        root.setClosingState(true)
        val closing = runtime.frame(root, 100f, 40f, nowMillis = 0L)

        assertEquals(250L, closing.motionDurationMillis(opened))
        assertTrue(closing.resolved[dialog].opacity > 0f)
    }

    @Test
    fun `keyframes do not reset hover scale when animating rotate`() {
        val stylesheet = compileHss(
            """
            .dialog {
                scale: 1;
                transition: scale 200ms linear;
                animation: sway 1000ms linear infinite;
            }

            .dialog:hover {
                scale: 1.2;
            }

            @keyframes sway {
                from {
                    rotate: 0 0 -10;
                }
                to {
                    rotate: 0 0 10;
                }
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("dialog"))
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        root.states += UiState.HOVER
        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        val hovered = runtime.frame(root, 100f, 40f, nowMillis = 100L).resolved[root]

        assertTrue(hovered.transform.scale.x > 1f)
        assertTrue(hovered.transform.scale.x < 1.2f)
        assertTrue(hovered.transform.rotate.z != 0f)
    }

    @Test
    fun `infinite keyframe animation requests continuous frame refresh`() {
        val stylesheet = compileHss(
            """
            .dialog {
                animation: sway 1000ms linear infinite;
            }

            @keyframes sway {
                from { rotate: 0 0 -10; }
                to { rotate: 0 0 10; }
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("dialog"))
        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 40f, nowMillis = 0L)

        assertTrue(frame.requiresContinuousRefresh())
    }

    @Test
    fun `hover exit uses base transitions instead of snapping`() {
        val stylesheet = compileHss(
            """
            .dialog {
                scale: 1;
                transition: scale 200ms linear;
            }

            .dialog:hover {
                scale: 1.2;
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("dialog"))
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        root.states += UiState.HOVER
        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        runtime.frame(root, 100f, 40f, nowMillis = 250L)
        root.states -= UiState.HOVER
        runtime.frame(root, 100f, 40f, nowMillis = 250L)
        val exiting = runtime.frame(root, 100f, 40f, nowMillis = 350L).resolved[root]

        assertTrue(exiting.transform.scale.x > 1f)
        assertTrue(exiting.transform.scale.x < 1.2f)
    }

    @Test
    fun `hover exit uses longer base tint transition`() {
        val stylesheet = compileHss(
            """
            .dialog {
                background: #FFFFFF;
                tint: rgba(255, 255, 255, 0.75);
                transition: tint 2000ms linear;
            }

            .dialog:hover {
                tint: rgba(255, 255, 255, 1.0);
                transition: tint 200ms linear;
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("dialog"))
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        root.states += UiState.HOVER
        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        runtime.frame(root, 100f, 40f, nowMillis = 250L)
        root.states -= UiState.HOVER
        runtime.frame(root, 100f, 40f, nowMillis = 250L)
        val frame = runtime.frame(root, 100f, 40f, nowMillis = 350L)
        val exiting = frame.resolved[root]
        val command = assertIs<DrawBoxCommand>(frame.commands.single { it is DrawBoxCommand })

        assertTrue(exiting.tint.alpha > 0.95f, "Tint should still be close to hover value after 100ms of a 2000ms exit")
        assertTrue(exiting.tint.alpha < 1f)
        assertEquals(exiting.tint.alpha, command.tint.alpha, 0.01f)
    }

    @Test
    fun `transition lists from multiple tags merge by property`() {
        val stylesheet = compileHss(
            """
            .dialog {
                background: #FFFFFF;
                tint: rgba(255, 255, 255, 0.2);
                transition: tint 2000ms linear, rotate 200ms linear;
            }

            .overlay {
                scale: 0.97;
                transition: opacity 180ms ease-out, scale 180ms ease-out;
            }

            .overlay[status="show"] {
                scale: 1;
            }

            .dialog:hover {
                tint: rgba(255, 255, 255, 1.0);
                transition: tint 200ms linear;
            }
            """.trimIndent(),
        )
        val root = HollowUi(tags = listOf("dialog", "overlay")).apply {
            attributes["status"] = "show"
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        root.states += UiState.HOVER
        runtime.frame(root, 100f, 40f, nowMillis = 0L)
        runtime.frame(root, 100f, 40f, nowMillis = 250L)
        root.states -= UiState.HOVER
        runtime.frame(root, 100f, 40f, nowMillis = 250L)
        val exiting = runtime.frame(root, 100f, 40f, nowMillis = 350L).resolved[root]

        assertTrue(exiting.tint.alpha > 0.95f, "Dialog tint transition should survive overlay transition rules")
        assertTrue(exiting.tint.alpha < 1f)
    }

    @Test
    fun `step easing advances transitions in configured jumps`() {
        val stylesheet = compileHss(
            """
            #button {
                opacity: 0;
                transition: opacity 100ms steps(2, start);
            }

            #button:hover {
                opacity: 1;
            }
            """.trimIndent(),
        )
        fun button(hovered: Boolean) = HollowUi(id = "button").apply {
            if (hovered) states += UiState.HOVER
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        runtime.frame(button(false), 100f, 40f, nowMillis = 0L)
        runtime.frame(button(true), 100f, 40f, nowMillis = 0L)
        val stepped = runtime.frame(button(true), 100f, 40f, nowMillis = 25L)

        assertEquals(0.5f, stepped.resolved[stepped.resolved.root].opacity, 0.01f)
    }

    @Test
    fun `gradient paint resolves into draw box commands`() {
        val stylesheet = compileHss(
            """
            .fx {
                size: 40px 20px;
                background: linear-gradient(90deg, black, white);
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("fx"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 80f, 40f)
        val draw = assertIs<DrawBoxCommand>(frame.commands.first { it is DrawBoxCommand })

        assertIs<UiResolvedPaint.LinearGradient>(draw.paint)
    }

    @Test
    fun `background images pass fit mode into draw box commands`() {
        val stylesheet = compileHss(
            """
            .avatar {
                size: 40px 40px;
                background: image("hollowengine:textures/gui/icons/logo.png");
                fit: contain;
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("avatar"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 80f, 80f)
        val draw = assertIs<DrawBoxCommand>(frame.commands.first { it is DrawBoxCommand })

        assertIs<UiResolvedPaint.Image>(draw.paint)
        assertEquals(UiImageFit.CONTAIN, draw.fit)
    }

    @Test
    fun `sliced image fit stores slice settings in draw box commands`() {
        val stylesheet = compileHss(
            """
            .panel {
                size: 80px 40px;
                background: image("hollowengine:textures/gui/panel.png");
                fit: 9-slice 4px 8px;
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("panel"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 60f)
        val draw = assertIs<DrawBoxCommand>(frame.commands.first { it is DrawBoxCommand })

        assertEquals(UiImageFit.NINE_SLICE, draw.fit)
        assertEquals(8f, (draw.slice.right as UiLength.Px).value)
        assertEquals(4f, (draw.slice.top as UiLength.Px).value)
    }

    @Test
    fun `scrollable auto text in row gets viewport scrollbar and wheel range`() {
        val stylesheet = compileHss(
            """
            .root {
                layout: row;
                size: 240px 78px;
            }
            .character {
                size: 44px 44px;
            }
            .message {
                font-size: 12px;
                text-wrap: wrap;
                align: start center;
                margin: 4px;
                border: 1px #FF0000;
                scrollable: true;
            }
            """.trimIndent()
        )
        lateinit var message: TextNode
        val root = HollowUi(tags = listOf("root")) {
            Box(modifier = Modifier.layout(LayoutType.COLUMN)) {
                Text("[HollowHorizon]")
                Box(tags = listOf("character"))
            }
            message = Text(
                "Это **тестовый** диалог для *проверки* разных <color=#FFFF00>штук</color>, " +
                        "в особенности `переноса` [текста](https://t.me/hollowengine) и паддингов, " +
                        "чтобы при переносе всё было <size=24px>верно</size> :) " +
                        "Ну и ещё давай попробуем добавить картинку: ![alt](hollowengine:textures/gui/icons/logo.png)",
                tags = listOf("message"),
            )
        }
        val runtime = HollowUiRuntime(stylesheet = stylesheet)

        val frame = runtime.frame(root, 260f, 100f)
        val layout = frame.layout[message]
        runtime.setScrollImmediate(message, y = 24f)
        val scrolled = runtime.frame(root, 260f, 100f)

        assertTrue(layout.scrollRange.y > 0f)
        assertTrue(layout.content.height < frame.textCommand(message).layout.height)
        assertTrue(frame.commands.any { it is DrawScrollbarCommand && it.node == message && it.orientation == ScrollbarOrientation.VERTICAL })
        assertTrue(scrolled.layout[message].scrollOffset.y > 0f)
    }

    @Test
    fun `scrollbar hss style is emitted into scrollbar command`() {
        val stylesheet = compileHss(
            """
            .scroll {
                size: 100px 32px;
                scrollable: true;
                scrollbar-width: 12px;
                scrollbar-margin: 2px;
                scrollbar-min-thumb: 20px;
                scrollbar-track: #112233;
                scrollbar-thumb: linear-gradient(90deg, #FFFFFF, #000000);
                scrollbar-thumb-radius: 6px;
                scrollbar-thumb-fit: 3-slice-vertical 3px;
            }
            """.trimIndent()
        )
        val root = HollowUi {
            Text((1..8).joinToString("\n") { "line $it" }, tags = listOf("scroll"))
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 140f, 80f)
        val scrollbar = assertIs<DrawScrollbarCommand>(
            frame.commands.first { it is DrawScrollbarCommand && it.orientation == ScrollbarOrientation.VERTICAL }
        )
        val track = assertIs<UiResolvedPaint.Color>(scrollbar.trackPaint)

        assertEquals(12f, scrollbar.track.width)
        assertEquals(0x11 / 255f, track.color.red, 0.01f)
        assertIs<UiResolvedPaint.LinearGradient>(scrollbar.thumbPaint)
        assertEquals(6f, scrollbar.thumbBorder.radius)
        assertEquals(UiImageFit.THREE_SLICE_VERTICAL, scrollbar.thumbFit)
        assertEquals(3f, (scrollbar.thumbSlice.left as UiLength.Px).value)
    }

    @Test
    fun `text wrap style is passed into draw text commands`() {
        val stylesheet = compileHss(
            """
            .label {
                width: 40px;
                text-wrap: nowrap;
            }
            """.trimIndent()
        )
        val root = HollowUi {
            Text("Long label value", tags = listOf("label"))
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 40f)
        val text = assertIs<DrawTextCommand>(frame.commands.first { it is DrawTextCommand })

        assertFalse(text.wrap)
    }

    @Test
    fun `filters render the entire subtree through a layer`() {
        val stylesheet = compileHss(
            """
            .fx {
                size: 80px 40px;
                filter: grayscale(1);
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("fx")) {
            Text("Filtered child")
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 60f)
        val beginLayer = assertIs<BeginLayerCommand>(frame.commands.first { it is BeginLayerCommand })
        val text = assertIs<DrawTextCommand>(frame.commands.first { it is DrawTextCommand })

        assertEquals(1f, beginLayer.filter.grayscaleAmount())
        assertTrue(text.filter.effects.isEmpty(), "Child commands should not pre-apply parent filter before layer compositing")
    }

    @Test
    fun `container opacity renders subtree through a single layer`() {
        val stylesheet = compileHss(
            """
            .fade {
                size: 80px 40px;
                opacity: 0.5;
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("fade")) {
            Text("Faded child")
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 60f)
        val beginLayer = assertIs<BeginLayerCommand>(frame.commands.first { it is BeginLayerCommand })
        val text = assertIs<DrawTextCommand>(frame.commands.first { it is DrawTextCommand })

        assertEquals(0.5f, beginLayer.opacity)
        assertEquals(1f, text.opacity)
    }

    @Test
    fun `image tint is passed into image draw commands`() {
        val stylesheet = compileHss(
            """
            .avatar {
                size: 40px 40px;
                tint: rgba(128, 192, 255, 0.75);
            }
            """.trimIndent()
        )
        val root = HollowUi {
            Image("hollowengine:textures/gui/icons/logo.png", tags = listOf("avatar"))
        }

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 80f, 80f)
        val draw = assertIs<DrawImageCommand>(frame.commands.first { it is DrawImageCommand })

        assertEquals(128f / 255f, draw.tint.red, 0.01f)
        assertEquals(192f / 255f, draw.tint.green, 0.01f)
        assertEquals(0.75f, draw.tint.alpha, 0.01f)
    }

    @Test
    fun `backdrop filter emits a pre render filter command`() {
        val stylesheet = compileHss(
            """
            .glass {
                size: 80px 40px;
                backdrop-filter: blur(6px) grayscale(0.2);
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("glass"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 100f, 60f)
        val command = assertIs<DrawBackdropFilterCommand>(frame.commands.first { it is DrawBackdropFilterCommand })

        assertEquals(6f, command.filter.blurRadius())
        assertEquals(0.2f, command.filter.grayscaleAmount())
    }

    @Test
    fun `shadows render outside transformed element layers`() {
        val stylesheet = compileHss(
            """
            .fx {
                size: 80px 40px;
                rotate: 0deg 20deg 0deg;
                shadow: 0px 10px 20px 2px rgba(0, 0, 0, 0.35);
            }
            """.trimIndent()
        )
        val root = HollowUi(tags = listOf("fx"))

        val frame = HollowUiRuntime(stylesheet = stylesheet).frame(root, 120f, 80f)
        val shadowIndex = frame.commands.indexOfFirst { it is DrawShadowCommand }
        val layerIndex = frame.commands.indexOfFirst { it is BeginLayerCommand }

        assertTrue(shadowIndex >= 0, "Expected a dedicated shadow command")
        assertTrue(layerIndex >= 0, "Expected transformed element to render through a layer")
        assertTrue(shadowIndex < layerIndex, "Shadow should be emitted before the element layer so it is not clipped by that layer")
    }

    @Test
    fun `z rotated widgets render their text through a layer`() {
        lateinit var text: TextNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.size(120.px, 80.px),
                Modifier.rotate(z = 18f),
            ),
        ) {
            text = Text("Rotated label")
        }

        val frame = HollowUiRuntime().frame(root, 160f, 120f)
        val layerStart = frame.commands.indexOfFirst { it is BeginLayerCommand && it.node == root }
        val textIndex = frame.commands.indexOfFirst { it is DrawTextCommand && it.node == text }
        val layerEnd = frame.commands.indexOfFirst { it is EndLayerCommand && it.node == root }

        assertTrue(layerStart >= 0, "Expected rotated widget to render through a layer")
        assertTrue(textIndex in layerStart + 1 until layerEnd, "Text should be captured inside the rotated widget layer")
    }

    @Test
    fun `text with inherited 3d transform is captured by ancestor layer`() {
        lateinit var text: TextNode
        val root = HollowUi(
            modifier = Modifier.then(
                Modifier.size(120.px, 80.px),
                Modifier.rotate(y = 22f),
            ),
        ) {
            Box(modifier = Modifier.size(100.px, 60.px)) {
                text = Text("3D label")
            }
        }

        val frame = HollowUiRuntime().frame(root, 160f, 120f)
        val ownLayerStart = frame.commands.indexOfFirst { it is BeginLayerCommand && it.node == text }
        val layerStart = frame.commands.indexOfFirst { it is BeginLayerCommand && it.node == root }
        val textIndex = frame.commands.indexOfFirst { it is DrawTextCommand && it.node == text }
        val layerEnd = frame.commands.indexOfFirst { it is EndLayerCommand && it.node == root }

        assertEquals(-1, ownLayerStart, "Text inside an existing transformed layer should not allocate another layer")
        assertTrue(layerStart >= 0, "Expected transformed ancestor to render through a layer")
        assertTrue(textIndex in layerStart + 1 until layerEnd, "Text should be captured by the ancestor transform layer")
    }

    @Test
    fun `scrollable node draws own border before clipping content`() {
        lateinit var text: TextNode
        val root = HollowUi {
            text = Text(
                (1..4).joinToString("\n") { "line $it" },
                modifier = Modifier.then(
                    Modifier.size(80.px, 20.px),
                    Modifier.border(1.px, UiColor.White),
                    Modifier.input(scrollable = true),
                ),
            )
        }

        val commands = HollowUiRuntime().frame(root, 120f, 80f).commands
        val boxIndex = commands.indexOfFirst { it is DrawBoxCommand && it.node == text }
        val clipIndex = commands.indexOfFirst { it is PushClipCommand && it.node == text }
        val textIndex = commands.indexOfFirst { it is DrawTextCommand && it.node == text }

        assertTrue(boxIndex >= 0)
        assertTrue(clipIndex > boxIndex)
        assertTrue(textIndex > clipIndex)
    }

    @Test
    fun `text content transform starts after border and padding`() {
        lateinit var text: TextNode
        val root = HollowUi {
            text = Text(
                "line",
                modifier = Modifier.then(
                    Modifier.size(100.px, 40.px),
                    Modifier.border(1.px, UiColor.White),
                    Modifier.padding(4.px),
                ),
            )
        }

        val command = HollowUiRuntime().frame(root, 140f, 80f)
            .commands
            .filterIsInstance<DrawTextCommand>()
            .single { it.node == text }
        val origin = command.transform.transform(0f, 0f)

        assertEquals(5f, origin.x, 0.01f)
        assertEquals(5f, origin.y, 0.01f)
    }

    @Test
    fun `hit testing follows transformed visual bounds`() {
        lateinit var node: BoxNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            node = Box(
                modifier = Modifier.then(
                    Modifier.size(100.px, 40.px),
                    Modifier.rotate(z = 45f),
                    Modifier.input(clickable = true),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 180f, 120f)
        val hit = frame.hitTest(85f, 45f)

        assertEquals(node, hit?.node)
    }

    @Test
    fun `transformed hit testing does not leak into neighboring widgets`() {
        lateinit var front: BoxNode
        lateinit var lifted: BoxNode
        val root = HollowUi(
            modifier = Modifier.then(Modifier.layout(LayoutType.FREE), Modifier.size(320.px, 260.px)),
        ) {
            front = Box(
                modifier = Modifier.then(
                    Modifier.position(100.px, 40.px),
                    Modifier.size(100.px, 60.px),
                    Modifier.input(clickable = true),
                ),
            )
            lifted = Box(
                modifier = Modifier.then(
                    Modifier.position(100.px, 160.px),
                    Modifier.size(100.px, 60.px),
                    Modifier.rotate(x = 51f, z = 43f),
                    Modifier.input(clickable = true),
                ),
            )
        }

        val frame = HollowUiRuntime().frame(root, 320f, 260f)
        val hit = frame.hitTest(125f, 70f)

        assertEquals(front, hit?.node)
        assertTrue(hit?.node != lifted)
    }

    @Test
    fun `text child without input modifiers is transparent to hit testing for parent click`() {
        lateinit var container: BoxNode
        lateinit var text: TextNode
        val root = HollowUi(modifier = Modifier.layout(LayoutType.FREE)) {
            container = Box(
                id = "clickable-box",
                modifier = Modifier.onClick {},
            ) {
                text = Text("Click me",
                    modifier = Modifier.then(
                        Modifier.size(80.px, 20.px),
                        Modifier.foreground(UiColor(1f, 1f, 1f, 1f)),
                    ),
                )
            }
        }

        val frame = HollowUiRuntime().frame(root, 200f, 100f)
        val containerLayout = frame.layout[container]
        val textLayout = frame.layout[text]
        val hitOnText = frame.hitTest(textLayout.rect.x + textLayout.rect.width / 2f, textLayout.rect.y + textLayout.rect.height / 2f)

        assertNotNull(hitOnText, "Hit test should return a hit within visible area")
        assertEquals(container, hitOnText.node, "Hit on transparent text child should reach parent box with onClick")
        assertTrue(containerLayout.rect.contains(textLayout.rect.x + textLayout.rect.width / 2f, textLayout.rect.y + textLayout.rect.height / 2f))
    }
}

private fun HollowUiFrame.textCommand(node: TextNode): DrawTextCommand {
    return assertIs(commands.first { it.node == node && it is DrawTextCommand })
}
