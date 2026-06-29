package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.docking.*
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlOptions
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import kotlin.math.abs
import kotlin.math.sign

class HollowUiDemoScreen : HollowComposeUiScreen("Hollow UI Demo", DemoStyles) {
    private var selectedTab by mutableStateOf("overview")
    private var popupTooltipVisible by mutableStateOf(false)
    private val freeNodeOffsets = mutableStateMapOf<Int, DemoOffset>()
    private var layoutGlassOffset by mutableStateOf(DemoOffset.Zero)
    private var xmlEventText by mutableStateOf("XML event log is empty")
    private var editorKeyLog by mutableStateOf("F2 is handled by Modifier.onKeyInput")
    private val editorHighlighter = UiSyntaxHighlighter(::highlightEditorDemoText)
    private val dockingState = DockingState().apply {
        open(DockItem("project", "Project"))
        open(DockItem("editor", "Editor"))
        open(DockItem("preview", "Preview"), DockTarget(placement = DockPlacement.RIGHT))
        open(DockItem("console", "Console"), DockTarget(placement = DockPlacement.BOTTOM))
        openFloating(DockItem("inspector", "Inspector"), x = 430f, y = 72f, width = 260f, height = 190f)
    }

    @Composable
    override fun Content() {
        Column(id = "demo-root") {
            Row(id = "tabs", tags = listOf("tabs"), modifier = Modifier.input(scrollable = true)) {
                tab("xml", "XML", "hollowengine:textures/gui/icons/code_editor.svg")
                tab("overview", "Главная", "hollowengine:textures/gui/npc_menu/talk.png")
                tab("widgets", "Виджеты", "hollowengine:textures/gui/npc_menu/quests.png")
                tab("text", "Text", "hollowengine:textures/gui/icons/docs.svg")
                tab("editor", "Editor", "hollowengine:textures/gui/icons/code_editor.svg")
                tab("layout", "Разметка", "hollowengine:textures/gui/npc_menu/trade.png")
                tab("docking", "Docking", "hollowengine:textures/gui/icons/code_editor.svg")
                tab("effects", "Эффекты", "hollowengine:textures/gui/npc_menu/character.png")
                tab("shapes", "Shapes", "hollowengine:textures/gui/icons/code_editor.svg")
            }
            Box(id = "content", tags = listOf("content")) {
                when (selectedTab) {
                    "widgets" -> widgets()
                    "text" -> textAndPopupDemo()
                    "editor" -> editorDemo()
                    "layout" -> layout()
                    "docking" -> docking()
                    "effects" -> effects()
                    "shapes" -> shapesDemo()
                    "xml" -> xmlDemo()
                    else -> overview()
                }
            }
        }
    }

    override fun rebuildEveryFrame(): Boolean = selectedTab == "transforms" || selectedTab == "effects"

    @Composable
    private fun tab(id: String, label: String, icon: String) {
        Row(
            id = "tab-$id", tags = listOf("tab"), modifier = Modifier.then(
                Modifier.input(hoverable = true, clickable = true),
                Modifier.onClick {
                    selectedTab = id
                }
            )
        ) {
            Image(icon, tags = listOf("tab-icon"))
            Text(label, tags = listOf("tab-label"))
        }
    }

    @Composable
    private fun overview() {
        Column(tags = listOf("panel", "scroll-panel"), modifier = Modifier.input(scrollable = true)) {
            Text("Какой-то интерфейс", tags = listOf("title"))
            Text(
                "Здесь могла быть ваша реклама, но у вас нет денег :)",
                tags = listOf("body")
            )
            repeat(18) { index ->
                Row(tags = listOf("row")) {
                    Image("hollowengine:textures/gui/quests/quest_icon.png", tags = listOf("small-icon"))
                    Text(
                        "Какая-то фигня для прокрутки под номером ${index + 1}.",
                        tags = listOf("body")
                    )
                }
            }
        }
    }

    @Composable
    private fun widgets() {
        Box(tags = listOf("panel-grid"), modifier = Modifier.input(scrollable = true)) {
            Column(tags = listOf("card"), modifier = Modifier.position(0.px, 0.px)) {
                Text("Текст", tags = listOf("card-title"))
                Text(
                    "Не ожидали? Да, это реально текст и в нём правда есть буквы! И их тут многа...",
                    tags = listOf("body")
                )
            }
            Column(tags = listOf("card"), modifier = Modifier.position(184.px, 0.px)) {
                Text("Картинка", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/npc_menu/character.png", tags = listOf("preview-image"))
            }
            Column(tags = listOf("card"), modifier = Modifier.position(368.px, 0.px)) {
                Text("Предмет", tags = listOf("card-title"))
                Item("minecraft:diamond_block", tags = listOf("item-preview"))
            }
            Column(tags = listOf("card"), modifier = Modifier.position(0.px, 136.px)) {
                Text("Сущность", tags = listOf("card-title"))
                Entity("player", tags = listOf("entity-preview"))
            }
            Column(tags = listOf("card"), modifier = Modifier.position(184.px, 136.px)) {
                Text("Холст", tags = listOf("card-title"))
                Canvas("demo-wave", tags = listOf("canvas-preview"))
            }
        }
    }

    @Composable
    private fun textAndPopupDemo() {
        val inlineContent = UiTextContent(
            listOf(
                UiTextSegment.Text("Inline text can host ".bound()),
                UiTextSegment.inlineWidget("inline-chip", align = UiInlineAlign.MIDDLE),
                UiTextSegment.Text(" measured widgets and keep wrapping/justify consistent.".bound()),
            )
        )
        val slotContent = UiTextContent(
            listOf(
                UiTextSegment.inlineWidget("inline-note", align = UiInlineAlign.TOP),
                UiTextSegment.Text(
                    "Measured slots now travel through the same inline line builder as text and images, so wrapping uses one layout path.".bound(),
                ),
            )
        )

        Box(tags = listOf("text-demo-stage"), modifier = Modifier.input(scrollable = true)) {
            Column(tags = listOf("text-demo-card"), modifier = Modifier.position(0.px, 0.px)) {
                Text("Inline widget", tags = listOf("card-title"))
                Text(
                    textContent = inlineContent,
                    tags = listOf("text-demo-copy"),
                    modifier = Modifier.then(Modifier.size(320.px, 64.px), Modifier.textAlign(UiTextAlign.JUSTIFY)),
                ) {
                    InlineWidget("inline-chip", tags = listOf("text-inline-chip")) {
                        Text(
                            "AUTO",
                            tags = listOf("text-inline-chip-label"),
                            modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER),
                        )
                    }
                }
            }

            Column(tags = listOf("text-demo-card", "text-slot-card"), modifier = Modifier.position(352.px, 0.px)) {
                Text("Inline slot", tags = listOf("card-title"))
                Text(
                    textContent = slotContent,
                    tags = listOf("text-demo-copy"),
                    modifier = Modifier.size(330.px, 116.px),
                ) {
                    InlineWidget("inline-note", tags = listOf("text-slot-note")) {
                        Text("Measured\nslot", tags = listOf("text-slot-note-label"))
                    }
                }
            }

            Column(tags = listOf("text-demo-card", "popup-demo-card"), modifier = Modifier.position(0.px, 164.px)) {
                Text("Popups", tags = listOf("card-title"))
                Row(
                    id = "popup-anchor",
                    tags = listOf("popup-anchor"),
                    modifier = Modifier.then(
                        Modifier.input(hoverable = true),
                        Modifier.onEnter {
                            popupTooltipVisible = true
                        },
                        Modifier.onExit {
                            popupTooltipVisible = false
                        },
                    ),
                ) {
                    Text("Hover for tooltip", tags = listOf("popup-anchor-label"))
                }
                Popup(
                    id = "popup-near-anchor",
                    anchor = UiPopupAnchor.Node("popup-anchor"),
                    alignment = UiPopupAlignment(offsetY = 6f),
                    tags = listOf("popup-panel"),
                ) {
                    Text("Node anchored popup", tags = listOf("popup-title"))
                    Text("Below-start with offset.", tags = listOf("popup-body"))
                }
                if (popupTooltipVisible) {
                    Popup(
                        id = "popup-near-cursor",
                        anchor = UiPopupAnchor.Cursor(),
                        alignment = UiPopupAlignment.Cursor,
                        tags = listOf("popup-panel", "cursor-popup"),
                        modifier = Modifier.layer(100),
                    ) {
                        Text("Tooltip follows cursor", tags = listOf("popup-title"))
                    }
                }
            }
        }
    }

    @Composable
    private fun editorDemo() {
        HollowUiEditorDemo(
            keyLog = editorKeyLog,
            onKeyLog = { editorKeyLog = it },
            highlighter = editorHighlighter,
        )
    }

    @Composable
    private fun layout() {
        Box(tags = listOf("free-stage"), modifier = Modifier.input(scrollable = true)) {
            repeat(12) { index ->
                val offset = freeNodeOffsets[index] ?: DemoOffset.Zero
                Column(
                    id = "free-node-$index",
                    tags = listOf("free-node"),
                    modifier = Modifier.then(
                        Modifier.position((40 + index * 96 + offset.x).px, (40 + index % 4 * 82 + offset.y).px),
                        Modifier.input(hoverable = true, draggable = true),
                        Modifier.onDrag {
                            val nodeKey = it.node.id
                            val index = nodeKey?.removePrefix("free-node-")?.toIntOrNull() ?: return@onDrag
                            val current = freeNodeOffsets[index] ?: DemoOffset.Zero
                            freeNodeOffsets[index] = DemoOffset(current.x + it.deltaX, current.y + it.deltaY)
                            it.consume()
                        }
                    ),
                ) {
                    Text("Нода ${index + 1}", tags = listOf("free-label"))
                }
            }
            Column(
                id = "layout-glass",
                tags = listOf("layout-glass", "glass-card"),
                modifier = Modifier.then(
                    Modifier.position((140 + layoutGlassOffset.x).px, (260 + layoutGlassOffset.y).px),
                    Modifier.input(hoverable = true, draggable = true),
                    Modifier.onDrag {
                        layoutGlassOffset = DemoOffset(layoutGlassOffset.x + it.deltaX, layoutGlassOffset.y + it.deltaY)
                        it.consume()
                    }
                ),
            ) {
                Text("Стекляшка", tags = listOf("card-title"))
                Text("Ну и нафиг я это делал? Кому вообещ нужно размывать чате по центру...", tags = listOf("body"))
            }
        }
    }

    @Composable
    private fun docking() {
        DockSpace(
            state = dockingState,
            id = "demo-dock-space",
            modifier = Modifier.size(100.percent, 100.percent),
        ) { item ->
            Column(
                id = "dock-demo-${item.id}",
                tags = listOf("panel"),
                modifier = Modifier.then(
                    Modifier.padding(12.px),
                    Modifier.gap(8.px),
                ),
            ) {
                Text(item.title, tags = listOf("title"))
                when (item.id) {
                    "project" -> dockDemoRows("Scenes", "Scripts", "Assets", "Dialogues", "Cutscenes")
                    "editor" -> dockDemoRows("fun main() {", "    scene(\"intro\")", "    character(\"Ada\")", "}")
                    "preview" -> dockPreview()
                    "console" -> dockDemoRows(
                        "[info] Runtime started",
                        "[info] UI hot reload ready",
                        "[warn] Missing optional icon"
                    )

                    "inspector" -> dockDemoRows("Transform", "Position: 0, 0, 0", "Rotation: 0, 0, 0", "Scale: 1, 1, 1")
                    else -> Text("Empty panel", tags = listOf("body"))
                }
            }
        }
    }

    @Composable
    private fun dockDemoRows(vararg rows: String) {
        rows.forEach { row ->
            Row(tags = listOf("row")) {
                Text(row, tags = listOf("body"))
            }
        }
    }

    @Composable
    private fun dockPreview() {
        Box(
            modifier = Modifier.then(
                Modifier.size(100.percent, 100.percent),
                Modifier.background(UiColor(0.08f, 0.1f, 0.13f, 1f)),
                Modifier.border(1.px, UiColor(0.24f, 0.28f, 0.34f, 1f), 6f),
            ),
        ) {
            Text("Viewport", tags = listOf("body"), modifier = Modifier.align(UiAlign.CENTER, UiAlign.CENTER))
        }
    }

    @Composable
    private fun effects() {
        Box(tags = listOf("effects-stage"), modifier = Modifier.input(scrollable = true)) {
            Column(tags = listOf("effect-card", "gradient-card"), modifier = Modifier.position(20.px, 18.px)) {
                Text("Градиент", tags = listOf("card-title"))
                Text("Ну типа карточка, но с градиентом.", tags = listOf("body"))
            }
            Column(
                tags = listOf("effect-card", "grayscale-card"),
                modifier = Modifier.then(Modifier.position(220.px, 18.px), Modifier.input(hoverable = true)),
            ) {
                Text("ЧБ фильтр", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/npc_menu/character.png", tags = listOf("preview-image"))
                Text("Работает даже на картинки")
            }
            var flipHovered by remember { mutableStateOf(false) }
            val frontRotation = if (flipHovered) 180f else 0f
            val backRotation = if (flipHovered) 0f else -180f
            Box(
                id = "flip-zone",
                tags = listOf("flip-zone"),
                modifier = Modifier.then(
                    Modifier.position(420.px, 18.px),
                    Modifier.input(hoverable = true),
                    Modifier.onEnter { flipHovered = true },
                    Modifier.onExit { flipHovered = false },
                ),
            ) {
                Column(
                    tags = listOf("effect-card", "flip-face", "flip-front"),
                    modifier = Modifier.then(
                        Modifier.position(0.px, 0.px),
                        Modifier.rotate(y = frontRotation, z = frontRotation / 4f),
                        Modifier.backfaceVisibility(UiBackfaceVisibility.HIDDEN),
                    ),
                ) {
                    Text("Лицевая сторона", tags = listOf("card-title"))
                    Text("Положи сюда курсор", tags = listOf("body"))
                }
                Column(
                    tags = listOf("effect-card", "flip-face", "flip-back"),
                    modifier = Modifier.then(
                        Modifier.position(0.px, 0.px),
                        Modifier.rotate(y = backRotation, z = frontRotation / 4f),
                        Modifier.backfaceVisibility(UiBackfaceVisibility.HIDDEN),
                    ),
                ) {
                    Text("Задняя сторона", tags = listOf("card-title"))
                    Text("А тут тоже чёта есть.", tags = listOf("body"))
                }
            }
            Column(
                tags = listOf("effect-card", "paper-card"),
                modifier = Modifier.then(Modifier.position(20.px, 168.px), Modifier.input(hoverable = true)),
            ) {
                Text("Карточка в 3В", tags = listOf("card-title", "paper-title"))
                Text("3D на удивление можно делать всякие интересные штуки.", tags = listOf("body", "paper-body"))
            }
            Column(tags = listOf("effect-card", "glass-card"), modifier = Modifier.position(220.px, 168.px)) {
                Text("Размытие фона", tags = listOf("card-title"))
                Text("Только размывать-то нечего?", tags = listOf("body"))
            }
            Column(tags = listOf("effect-card", "css-lift-card"), modifier = Modifier.position(420.px, 168.px)) {
                Text("Карта Халвы", tags = listOf("card-title", "paper-title"))
                Text(
                    "За неё ничего нельзя купить... Но зато она прикольно леветирует",
                    tags = listOf("body", "paper-body")
                )
                Item("minecraft:diamond")
            }
            Column(
                tags = listOf("effect-card", "soft-focus-card"),
                modifier = Modifier.then(Modifier.position(620.px, 168.px), Modifier.input(hoverable = true)),
            ) {
                Text("Карточка с размытием", tags = listOf("card-title", "soft-title"))
                Text("А теперь её видно нормально!", tags = listOf("body", "soft-body"))
            }
        }
    }

    @Composable
    private fun xmlDemo() {

        val demo = parseUiXml(
            """
            <import element="hollowengine:ui/elements/xml_demo_badge.ui" named="demo_badge" />

            <box id="xml-demo" style="hollowengine:ui/styles/xml_demo.hss">
                <text tags="xml-title">XML + HSS resource</text>
                <text tags="xml-body">This panel is built from XML-like markup. The root imports xml_demo.hss from assets.</text>
                <text tags="xml-rich-text">
                    XML text with <box id="xml-inline-chip" tags="xml-inline-chip"><text>inline</text></box> measured slot.
                </text>
                <text tags="xml-slot-text">
                    <box id="xml-slot-note" tags="xml-slot-note"><text>slot</text></box>
                    This paragraph contains a measured inline widget whose size comes from HSS.
                </text>
                <column id="xml-popup-anchor" tags="xml-popup-anchor">
                    <text>Popup anchor</text>
                </column>
                <popup id="xml-popup" anchor="xml-popup-anchor" placement="below-end" offset-y="6px" tags="xml-popup">
                    <text>XML popup</text>
                </popup>
                <column id="xml-demo-accept" tags="xml-button" onClick='{event:"xml_demo";button:"accept";mouse:<it.button>}'>
                    <text>Accept</text>
                </column>
                <column id="xml-demo-cancel" tags="xml-button secondary" onClick='{event:"xml_demo";button:"cancel";mouse:<it.button>}'>
                    <text>Cancel</text>
                </column>
                <demo_badge />
            </box>
            """.trimIndent()
        )
        val options = UiXmlOptions(
            eventSink = UiEventSink { tag ->
                val event = tag.getString("event")
                val button = tag.getString("button")
                val mouse = tag.getInt("mouse")
                xmlEventText = "event=$event button=$button mouse=$mouse"
            },
        )

        Column {
            UiXmlContent(demo, options)
            Text(UiTextContent.plain(xmlEventText.bound()), tags = listOf("xml-log"))
        }
    }
}

private fun Float.easeOutSigned(): Float {
    val magnitude = abs(this)
    return sign(this) * (1f - (1f - magnitude) * (1f - magnitude))
}

private data class DemoOffset(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Zero = DemoOffset(0f, 0f)
    }
}
