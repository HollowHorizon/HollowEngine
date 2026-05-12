package ru.hollowhorizon.hollowengine.client.ui.screen

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss

class HollowUiDemoScreen : HollowUiScreen("Hollow UI Demo", DemoStyles) {
    private var selectedTab = "overview"
    private val freeNodeOffsets = mutableMapOf<Int, DemoOffset>()

    override fun buildUi(): UiNode = HollowUi(id = "demo-root") {
        Box(id = "tabs", tags = listOf("tabs")) {
            tab("overview", "Overview", "hollowengine:textures/gui/npc_menu/talk.png")
            tab("widgets", "Widgets", "hollowengine:textures/gui/npc_menu/quests.png")
            tab("layout", "Layout", "hollowengine:textures/gui/npc_menu/trade.png")
            tab("transforms", "3D", "hollowengine:textures/gui/icons/dialogue.png")
        }
        Box(id = "content", tags = listOf("content")) {
            when (selectedTab) {
                "widgets" -> widgets()
                "layout" -> layout()
                "transforms" -> transforms()
                else -> overview()
            }
        }
    }

    override fun onNodeClicked(node: UiNode, button: Int): Boolean {
        if (button != 0) return false
        val id = node.id ?: return false
        if (!id.startsWith("tab-")) return false
        selectedTab = id.removePrefix("tab-")
        invalidateUi()
        return true
    }

    override fun onNodeDragged(node: UiNode, deltaX: Float, deltaY: Float): Boolean {
        val index = node.id?.removePrefix("free-node-")?.toIntOrNull() ?: return false
        val current = freeNodeOffsets[index] ?: DemoOffset.Zero
        freeNodeOffsets[index] = DemoOffset(current.x + deltaX, current.y + deltaY)
        return true
    }

    override fun mouseMoved(mouseX: Double, mouseY: Double) {
        super.mouseMoved(mouseX, mouseY)
        if (selectedTab == "transforms") invalidateUi()
    }

    private fun UiScope.tab(id: String, label: String, icon: String) {
        val tab = Box(id = "tab-$id", tags = listOf("tab"), modifier = Modifier.input(hoverable = true, clickable = true)) {
            Image(icon, tags = listOf("tab-icon"))
            Text(label, tags = listOf("tab-label"))
        }
        if (selectedTab == id) tab.states += UiState.SELECTED
    }

    private fun UiScope.overview() {
        Box(tags = listOf("panel", "scroll-panel"), modifier = Modifier.input(scrollable = true)) {
            Text("Hollow UI", tags = listOf("title"))
            Text("DSL tree -> HSS selectors -> computed style -> Taffy/free layout -> command renderer.", tags = listOf("body"))
            repeat(18) { index ->
                Box(tags = listOf("row")) {
                    Image("hollowengine:textures/gui/quests/quest_icon.png", tags = listOf("small-icon"))
                    Text("Scrollable row ${index + 1}: clipping, hover, styles and layout stay in the same pipeline.", tags = listOf("body"))
                }
            }
        }
    }

    private fun UiScope.widgets() {
        Box(tags = listOf("panel-grid"), modifier = Modifier.input(scrollable = true)) {
            Box(tags = listOf("card"), modifier = Modifier.position(0.px, 0.px)) {
                Text("Text", tags = listOf("card-title"))
                Text("Foreground, inherited color and HSS state rules.", tags = listOf("body"))
            }
            Box(tags = listOf("card"), modifier = Modifier.position(184.px, 0.px)) {
                Text("Image", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/npc_menu/character.png", tags = listOf("preview-image"))
            }
            Box(tags = listOf("card"), modifier = Modifier.position(368.px, 0.px)) {
                Text("Item", tags = listOf("card-title"))
                Item("minecraft:diamond_sword", tags = listOf("item-preview"))
            }
            Box(tags = listOf("card"), modifier = Modifier.position(0.px, 136.px)) {
                Text("Entity", tags = listOf("card-title"))
                Entity("player", tags = listOf("entity-preview"))
            }
            Box(tags = listOf("card"), modifier = Modifier.position(184.px, 136.px)) {
                Text("Canvas", tags = listOf("card-title"))
                Canvas("demo-wave", tags = listOf("canvas-preview"))
            }
        }
    }

    private fun UiScope.layout() {
        Box(tags = listOf("free-stage"), modifier = Modifier.input(scrollable = true)) {
            repeat(12) { index ->
                val offset = freeNodeOffsets[index] ?: DemoOffset.Zero
                Box(
                    id = "free-node-$index",
                    tags = listOf("free-node"),
                    modifier = Modifier.then(
                        Modifier.position((40 + index * 96 + offset.x).px, (40 + (index % 4) * 82 + offset.y).px),
                        Modifier.input(hoverable = true, draggable = true),
                    ),
                ) {
                    Text("Node ${index + 1}", tags = listOf("free-label"))
                }
            }
        }
    }

    private fun UiScope.transforms() {
        Box(tags = listOf("panel-grid")) {
            val hoverRotate = if (isHovered("tilt-card")) {
                val x = ((mouseY / height.coerceAtLeast(1)) - 0.5f) * -18f
                val y = ((mouseX / width.coerceAtLeast(1)) - 0.5f) * 18f
                Modifier.rotate(x = x, y = y)
            } else {
                Modifier.rotate(x = 0f, y = 0f)
            }
            Box(id = "tilt-card", tags = listOf("card", "tilted-x"), modifier = Modifier.then(Modifier.position(20.px, 20.px), hoverRotate, Modifier.input(hoverable = true))) {
                Text("FBO X/Y", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/quests/quest.png", tags = listOf("preview-image"))
            }
            Box(tags = listOf("card", "scaled"), modifier = Modifier.then(Modifier.position(220.px, 20.px), Modifier.scale(1.08f), Modifier.input(hoverable = true))) {
                Text("Scale", tags = listOf("card-title"))
                Text("Hover and transforms share hit testing.", tags = listOf("body"))
            }
        }
    }
}

private val DemoStyles = compileHss(
    """
    #demo-root {
        layout: column;
        size: 100% 100%;
        min-size: 0px 0px;
        padding: 14px;
        gap: 10px;
        background: rgba(8, 10, 14, 0.92);
    }

    .tabs {
        layout: row;
        gap: 8px;
        height: 34px;
        min-size: 0px 34px;
    }

    .tab {
        layout: row;
        padding: 6px 10px;
        gap: 6px;
        width: 116px;
        background: rgba(28, 32, 42, 0.92);
        border: 1px rgba(120, 140, 170, 0.38);
        hoverable: true;
        clickable: true;
        transition:
            scale 140ms ease-out,
            background 140ms ease-out;
    }

    .tab:hover {
        background: rgba(42, 48, 62, 0.96);
        scale: 1.02;
    }

    .tab:selected {
        background: rgba(54, 72, 108, 0.98);
        border: 1px rgba(145, 178, 230, 0.9);
    }

    .tab-icon {
        size: 18px 18px;
        fit: contain;
    }

    .tab-label {
        foreground: white;
    }

    .content {
        grow: 1;
        size: 100% 100%;
        min-size: 0px 0px;
        background: rgba(18, 20, 27, 0.88);
        border: 1px rgba(110, 125, 150, 0.32);
        padding: 12px;
        clip: true;
    }

    .panel {
        layout: column;
        gap: 10px;
        size: 100% 100%;
        min-size: 0px 0px;
    }

    .scroll-panel {
        scrollable: true;
        size: 100% 100%;
        min-size: 0px 0px;
    }

    .title {
        foreground: #c8ddff;
        height: 18px;
    }

    .body {
        foreground: rgba(226, 230, 238, 0.92);
        height: 48px;
    }

    .row {
        layout: row;
        gap: 8px;
        padding: 8px;
        height: 48px;
        background: rgba(32, 36, 46, 0.78);
    }

    .small-icon {
        size: 20px 20px;
    }

    .panel-grid {
        layout: free;
        size: 100% 100%;
        min-size: 0px 0px;
        gap: 10px;
        scrollable: true;
    }

    .card {
        layout: column;
        gap: 8px;
        padding: 10px;
        size: 168px 118px;
        background: rgba(30, 34, 44, 0.9);
        border: 1px rgba(120, 140, 170, 0.35);
        transition:
            scale 140ms ease-out,
            rotate 140ms ease-out,
            background 140ms ease-out;
    }

    .card:hover {
        background: rgba(42, 48, 62, 0.96);
        scale: 1.03;
    }

    .card-title {
        foreground: #c8ddff;
        height: 16px;
    }

    .preview-image {
        size: 72px 72px;
        image-fit: contain;
    }

    .item-preview {
        size: 52px 52px;
    }

    .entity-preview {
        size: 72px 72px;
    }

    .canvas-preview {
        size: 120px 54px;
    }

    .free-stage {
        layout: free;
        size: 100% 100%;
        min-size: 0px 0px;
        background: rgba(12, 16, 23, 0.9);
        scrollable: true;
    }

    .free-node {
        padding: 8px;
        size: 92px 42px;
        background: rgba(48, 62, 88, 0.95);
        border: 1px rgba(136, 174, 230, 0.65);
        transition:
            scale 180ms ease-out,
            background 180ms ease-out;
    }

    .free-node:hover {
        scale: 1.03;
        background: rgba(62, 78, 110, 0.98);
    }

    .free-node:dragging {
        scale: 1.08;
        background: rgba(78, 96, 134, 1.0);
    }

    .free-label {
        foreground: white;
        height: 16px;
    }
    """.trimIndent()
)

private data class DemoOffset(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Zero = DemoOffset(0f, 0f)
    }
}
