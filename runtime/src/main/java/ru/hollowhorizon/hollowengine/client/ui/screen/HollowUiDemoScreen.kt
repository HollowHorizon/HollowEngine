package ru.hollowhorizon.hollowengine.client.ui.screen

import androidx.compose.runtime.*
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlContent
import ru.hollowhorizon.hollowengine.client.ui.xml.UiXmlOptions
import ru.hollowhorizon.hollowengine.client.ui.xml.parseUiXml
import kotlin.math.abs
import kotlin.math.sign

class HollowUiDemoScreen : HollowComposeUiScreen("Hollow UI Demo", DemoStyles) {
    private var selectedTab by mutableStateOf("overview")
    private val freeNodeOffsets = mutableStateMapOf<Int, DemoOffset>()
    private var layoutGlassOffset by mutableStateOf(DemoOffset.Zero)
    private var xmlEventText by mutableStateOf("XML event log is empty")

    @Composable
    override fun Content() {
        Box(id = "demo-root") {
            Box(id = "tabs", tags = listOf("tabs"), modifier = Modifier.input(scrollable = true)) {
                tab("xml", "XML", "hollowengine:textures/gui/icons/code_editor.svg")
                tab("overview", "Главная", "hollowengine:textures/gui/npc_menu/talk.png")
                tab("widgets", "Виджеты", "hollowengine:textures/gui/npc_menu/quests.png")
                tab("layout", "Разметка", "hollowengine:textures/gui/npc_menu/trade.png")
                tab("transforms", "3D", "hollowengine:textures/gui/icons/dialogue.png")
                tab("effects", "Эффекты", "hollowengine:textures/gui/npc_menu/character.png")
            }
            Box(id = "content", tags = listOf("content")) {
                when (selectedTab) {
                    "widgets" -> widgets()
                    "layout" -> layout()
                    "transforms" -> transforms()
                    "effects" -> effects()
                    "xml" -> xmlDemo()
                    else -> overview()
                }
            }
        }
    }

    override fun onNodeClicked(node: UiNode, button: Int): Boolean {
        if (button != 0) return false
        val id = node.id ?: return false
        if (!id.startsWith("tab-")) return false
        selectedTab = id.removePrefix("tab-")
        invalidateUi(immediate = true)
        return true
    }

    override fun onNodeDragged(nodeKey: String, deltaX: Float, deltaY: Float): Boolean {
        if (nodeKey == "layout-glass") {
            layoutGlassOffset = DemoOffset(layoutGlassOffset.x + deltaX, layoutGlassOffset.y + deltaY)
            return true
        }
        val index = nodeKey.removePrefix("free-node-").toIntOrNull() ?: return false
        val current = freeNodeOffsets[index] ?: DemoOffset.Zero
        freeNodeOffsets[index] = DemoOffset(current.x + deltaX, current.y + deltaY)
        return true
    }

    override fun rebuildEveryFrame(): Boolean = selectedTab == "transforms" || selectedTab == "effects"

    @Composable
    private fun tab(id: String, label: String, icon: String) {
        Box(id = "tab-$id", tags = listOf("tab"), modifier = Modifier.input(hoverable = true, clickable = true)) {
            Image(icon, tags = listOf("tab-icon"))
            Text(label, tags = listOf("tab-label"))
        }
    }

    @Composable
    private fun overview() {
        Box(tags = listOf("panel", "scroll-panel"), modifier = Modifier.input(scrollable = true)) {
            Text("Какой-то интерфейс", tags = listOf("title"))
            Text(
                "Здесь могла быть ваша реклама, но у вас нет денег :)",
                tags = listOf("body")
            )
            repeat(18) { index ->
                Box(tags = listOf("row")) {
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
            Box(tags = listOf("card"), modifier = Modifier.position(0.px, 0.px)) {
                Text("Текст", tags = listOf("card-title"))
                Text(
                    "Не ожидали? Да, это реально текст и в нём правда есть буквы! И их тут многа...",
                    tags = listOf("body")
                )
            }
            Box(tags = listOf("card"), modifier = Modifier.position(184.px, 0.px)) {
                Text("Картинка", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/npc_menu/character.png", tags = listOf("preview-image"))
            }
            Box(tags = listOf("card"), modifier = Modifier.position(368.px, 0.px)) {
                Text("Предмет", tags = listOf("card-title"))
                Item("minecraft:diamond_block", tags = listOf("item-preview"))
            }
            Box(tags = listOf("card"), modifier = Modifier.position(0.px, 136.px)) {
                Text("Сущность", tags = listOf("card-title"))
                Entity("player", tags = listOf("entity-preview"))
            }
            Box(tags = listOf("card"), modifier = Modifier.position(184.px, 136.px)) {
                Text("Холст", tags = listOf("card-title"))
                Canvas("demo-wave", tags = listOf("canvas-preview"))
            }
        }
    }

    @Composable
    private fun layout() {
        Box(tags = listOf("free-stage"), modifier = Modifier.input(scrollable = true)) {
            repeat(12) { index ->
                val offset = freeNodeOffsets[index] ?: DemoOffset.Zero
                Box(
                    id = "free-node-$index",
                    tags = listOf("free-node"),
                    modifier = Modifier.then(
                        Modifier.position((40 + index * 96 + offset.x).px, (40 + index % 4 * 82 + offset.y).px),
                        Modifier.input(hoverable = true, draggable = true),
                    ),
                ) {
                    Text("Нода ${index + 1}", tags = listOf("free-label"))
                }
            }
            Box(
                id = "layout-glass",
                tags = listOf("layout-glass", "glass-card"),
                modifier = Modifier.then(
                    Modifier.position((140 + layoutGlassOffset.x).px, (260 + layoutGlassOffset.y).px),
                    Modifier.input(hoverable = true, draggable = true),
                ),
            ) {
                Text("Стекляшка", tags = listOf("card-title"))
                Text("Ну и нафиг я это делал? Кому вообещ нужно размывать чате по центру...", tags = listOf("body"))
            }
        }
    }

    @Composable
    private fun transforms() {
        Box(tags = listOf("panel-grid")) {
            // TODO: Rewrite compose-like
            val rect = layoutRect("tilt-card")
            val hoverRotate = if (rect != null && isHovered("tilt-card")) {
                val centerX = rect.x + rect.width * 0.5f
                val centerY = rect.y + rect.height * 0.5f
                val halfWidth = rect.width * 0.5f
                val halfHeight = rect.height * 0.5f
                val distanceX = ((mouseX - centerX) / halfWidth.coerceAtLeast(1f)).coerceIn(-1f, 1f).easeOutSigned()
                val distanceY = ((mouseY - centerY) / halfHeight.coerceAtLeast(1f)).coerceIn(-1f, 1f).easeOutSigned()
                val maxTilt = 18f
                val x = distanceY * maxTilt
                val y = -distanceX * maxTilt
                Modifier.then(
                    Modifier.rotate(x = x, y = y),
                    Modifier.transition(
                        UiTransition("rotate", 0L, TransitionEasing.LINEAR),
                        UiTransition("scale", 90L, TransitionEasing.EASE_OUT),
                        UiTransition("background", 120L, TransitionEasing.EASE_OUT),
                    ),
                )
            } else {
                Modifier.rotate(x = 0f, y = 0f)
            }
            Box(
                id = "tilt-card",
                tags = listOf("card", "tilted-x"),
                modifier = Modifier.then(Modifier.position(20.px, 20.px), hoverRotate, Modifier.input(hoverable = true))
            ) {
                Text("Фыреим буфыр гы-гы", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/quests/quest.png", tags = listOf("preview-image"))
            }
            Box(
                tags = listOf("card", "scaled"),
                modifier = Modifier.then(
                    Modifier.position(220.px, 20.px),
                    Modifier.input(hoverable = true)
                )
            ) {
                Text("Zoom? Скайп? Дискорд?", tags = listOf("card-title"))
                Text("Нет блин макс, иди на.", tags = listOf("body"))
            }
        }
    }

    @Composable
    private fun effects() {
        Box(tags = listOf("effects-stage"), modifier = Modifier.input(scrollable = true)) {
            Box(tags = listOf("effect-card", "gradient-card"), modifier = Modifier.position(20.px, 18.px)) {
                Text("Градиент", tags = listOf("card-title"))
                Text("Ну типа карточка, но с градиентом.", tags = listOf("body"))
            }
            Box(
                tags = listOf("effect-card", "grayscale-card"),
                modifier = Modifier.then(Modifier.position(220.px, 18.px), Modifier.input(hoverable = true)),
            ) {
                Text("ЧБ фильтр", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/npc_menu/character.png", tags = listOf("preview-image"))
                Text("Работает даже на картинки")
            }
            val flipHovered = isHovered("flip-zone")
            val frontRotation = if (flipHovered) 180f else 0f
            val backRotation = if (flipHovered) 0f else -180f
            Box(
                id = "flip-zone",
                tags = listOf("flip-zone"),
                modifier = Modifier.then(Modifier.position(420.px, 18.px), Modifier.input(hoverable = true)),
            ) {
                Box(
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
                Box(
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
            Box(
                tags = listOf("effect-card", "paper-card"),
                modifier = Modifier.then(Modifier.position(20.px, 168.px), Modifier.input(hoverable = true)),
            ) {
                Text("Карточка в 3В", tags = listOf("card-title", "paper-title"))
                Text("3D на удивление можно делать всякие интересные штуки.", tags = listOf("body", "paper-body"))
            }
            Box(tags = listOf("effect-card", "glass-card"), modifier = Modifier.position(220.px, 168.px)) {
                Text("Размытие фона", tags = listOf("card-title"))
                Text("Только размывать-то нечего?", tags = listOf("body"))
            }
            Box(tags = listOf("effect-card", "css-lift-card"), modifier = Modifier.position(420.px, 168.px)) {
                Text("Карта Халвы", tags = listOf("card-title", "paper-title"))
                Text(
                    "За неё ничего нельзя купить... Но зато она прикольно леветирует",
                    tags = listOf("body", "paper-body")
                )
                Item("minecraft:diamond")
            }
            Box(
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
                <box id="xml-demo-accept" tags="xml-button" onClick='{event:"xml_demo";button:"accept";mouse:<it.button>}'>
                    <text>Accept</text>
                </box>
                <box id="xml-demo-cancel" tags="xml-button secondary" onClick='{event:"xml_demo";button:"cancel";mouse:<it.button>}'>
                    <text>Cancel</text>
                </box>
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

        Box(modifier = Modifier.layout(LayoutType.COLUMN)) {
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
