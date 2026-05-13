package ru.hollowhorizon.hollowengine.client.ui.screen

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.compileHss
import kotlin.math.abs
import kotlin.math.sign

class HollowUiDemoScreen : HollowUiScreen("Hollow UI Demo", DemoStyles) {
    private var selectedTab = "overview"
    private val freeNodeOffsets = mutableMapOf<Int, DemoOffset>()

    override fun buildUi(): UiNode = HollowUi(id = "demo-root") {
        Box(id = "tabs", tags = listOf("tabs")) {
            tab("overview", "Overview", "hollowengine:textures/gui/npc_menu/talk.png")
            tab("widgets", "Widgets", "hollowengine:textures/gui/npc_menu/quests.png")
            tab("layout", "Layout", "hollowengine:textures/gui/npc_menu/trade.png")
            tab("transforms", "3D", "hollowengine:textures/gui/icons/dialogue.png")
            tab("effects", "Effects", "hollowengine:textures/gui/npc_menu/character.png")
        }
        Box(id = "content", tags = listOf("content")) {
            when (selectedTab) {
                "widgets" -> widgets()
                "layout" -> layout()
                "transforms" -> transforms()
                "effects" -> effects()
                else -> overview()
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
        val index = nodeKey.removePrefix("free-node-").toIntOrNull() ?: return false
        val current = freeNodeOffsets[index] ?: DemoOffset.Zero
        freeNodeOffsets[index] = DemoOffset(current.x + deltaX, current.y + deltaY)
        return true
    }

    override fun rebuildEveryFrame(): Boolean = selectedTab == "transforms" || selectedTab == "effects"

    private fun UiScope.tab(id: String, label: String, icon: String) {
        val tab =
            Box(id = "tab-$id", tags = listOf("tab"), modifier = Modifier.input(hoverable = true, clickable = true)) {
                Image(icon, tags = listOf("tab-icon"))
                Text(label, tags = listOf("tab-label"))
            }
        if (selectedTab == id) tab.states += UiState.SELECTED
    }

    private fun UiScope.overview() {
        Box(tags = listOf("panel", "scroll-panel"), modifier = Modifier.input(scrollable = true)) {
            Text("Hollow UI", tags = listOf("title"))
            Text(
                "DSL tree -> HSS selectors -> computed style -> Taffy/free layout -> command renderer.",
                tags = listOf("body")
            )
            repeat(18) { index ->
                Box(tags = listOf("row")) {
                    Image("hollowengine:textures/gui/quests/quest_icon.png", tags = listOf("small-icon"))
                    Text(
                        "Scrollable row ${index + 1}: clipping, hover, styles and layout stay in the same pipeline.",
                        tags = listOf("body")
                    )
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
            val rect = layoutRect("tilt-card")
            val hoverRotate = if (rect?.contains(mouseX, mouseY) == true) {
                val centerX = rect.x + rect.width * 0.5f
                val centerY = rect.y + rect.height * 0.5f
                val halfWidth = rect.width * 0.5f
                val halfHeight = rect.height * 0.5f
                val distanceX = ((mouseX - centerX) / halfWidth.coerceAtLeast(1f)).coerceIn(-1f, 1f).easeOutSigned()
                val distanceY = ((mouseY - centerY) / halfHeight.coerceAtLeast(1f)).coerceIn(-1f, 1f).easeOutSigned()
                val maxTilt = 18f
                val x = -distanceY * maxTilt
                val y = distanceX * maxTilt
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
                Text("FBO X/Y", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/quests/quest.png", tags = listOf("preview-image"))
            }
            Box(
                tags = listOf("card", "scaled"),
                modifier = Modifier.then(
                    Modifier.position(220.px, 20.px),
                    Modifier.scale(1.08f),
                    Modifier.input(hoverable = true)
                )
            ) {
                Text("Scale", tags = listOf("card-title"))
                Text("Hover and transforms share hit testing.", tags = listOf("body"))
            }
        }
    }

    private fun UiScope.effects() {
        Box(tags = listOf("effects-stage"), modifier = Modifier.input(scrollable = true)) {
            Box(tags = listOf("effect-card", "gradient-card"), modifier = Modifier.position(20.px, 18.px)) {
                Text("Rounded Gradient", tags = listOf("card-title"))
                Text("Rounded rect, gradient background and matching soft shadow.", tags = listOf("body"))
            }
            Box(
                tags = listOf("effect-card", "grayscale-card"),
                modifier = Modifier.then(Modifier.position(220.px, 18.px), Modifier.input(hoverable = true)),
            ) {
                Text("Grayscale Filter", tags = listOf("card-title"))
                Image("hollowengine:textures/gui/npc_menu/character.png", tags = listOf("preview-image"))
            }
            val flipHovered = layoutRect("flip-zone")?.contains(mouseX, mouseY) == true
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
                        Modifier.rotate(y = frontRotation),
                        Modifier.backfaceVisibility(UiBackfaceVisibility.HIDDEN),
                    ),
                ) {
                    Text("Front Face", tags = listOf("card-title"))
                    Text("Hover rotates this side away.", tags = listOf("body"))
                }
                Box(
                    tags = listOf("effect-card", "flip-face", "flip-back"),
                    modifier = Modifier.then(
                        Modifier.position(0.px, 0.px),
                        Modifier.rotate(y = backRotation),
                        Modifier.backfaceVisibility(UiBackfaceVisibility.HIDDEN),
                    ),
                ) {
                    Text("Back Face", tags = listOf("card-title"))
                    Text("This is a separate node on the reverse side.", tags = listOf("body"))
                }
            }
            Box(
                tags = listOf("effect-card", "paper-card"),
                modifier = Modifier.then(Modifier.position(20.px, 168.px), Modifier.input(hoverable = true)),
            ) {
                Text("Lifted Paper", tags = listOf("card-title"))
                Text("3D transform keeps the shadow and rounded shape together.", tags = listOf("body"))
            }
            Box(tags = listOf("effect-card", "glass-card"), modifier = Modifier.position(220.px, 168.px)) {
                Text("Backdrop Chain", tags = listOf("card-title"))
                Text("Backdrop filter samples the already rendered target below.", tags = listOf("body"))
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
        width: 104px;
        background: rgba(28, 32, 42, 0.92);
        border-radius: 8px;
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
        border-radius: 12px;
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
    }

    .row {
        layout: row;
        gap: 8px;
        padding: 8px;
        height: 48px;
        background: rgba(32, 36, 46, 0.78);
        border-radius: 8px;
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
        border-radius: 10px;
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

    .tilted-x {
        transition:
            rotate 180ms ease-out,
            scale 90ms ease-out,
            background 120ms ease-out;
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
        border-radius: 10px;
        scrollable: true;
    }

    .free-node {
        padding: 8px;
        size: 92px 42px;
        background: rgba(48, 62, 88, 0.95);
        border-radius: 8px;
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

    .effects-stage {
        layout: free;
        size: 100% 100%;
        min-size: 0px 0px;
        scrollable: true;
        background: linear-gradient(135deg, rgba(12, 18, 28, 0.98), rgba(28, 34, 48, 0.94), rgba(18, 42, 48, 0.92));
        border-radius: 10px;
    }

    .effect-card {
        layout: column;
        gap: 8px;
        padding: 12px;
        size: 178px 126px;
        border-radius: 14px;
        border: 2px rgba(232, 238, 255, 0.34);
        background: rgba(32, 39, 54, 0.9);
        foreground: rgba(238, 242, 250, 0.96);
        shadow: 0px 12px 26px 0px rgba(0, 0, 0, 0.34);
        transition:
            filter 180ms ease-out,
            rotate 240ms ease-out,
            translate 180ms ease-out,
            shadow 180ms ease-out,
            scale 140ms ease-out,
            background 160ms ease-out;
    }

    .gradient-card {
        background: linear-gradient(135deg, rgba(70, 132, 218, 0.96), rgba(142, 83, 184, 0.94), rgba(34, 172, 148, 0.9));
        shadow: 0px 16px 34px 2px rgba(22, 54, 92, 0.38);
    }

    .grayscale-card {
        filter: grayscale(1);
        background: linear-gradient(145deg, rgba(86, 112, 152, 0.94), rgba(42, 52, 72, 0.96));
        hoverable: true;
    }

    .grayscale-card:hover {
        filter: grayscale(0);
        scale: 1.04;
    }

    .flip-zone {
        layout: free;
        size: 178px 126px;
        hoverable: true;
        perspective: 680px;
    }

    .flip-face {
        backface-visibility: hidden;
        perspective: 680px;
        transition:
            rotate 260ms ease-out,
            shadow 180ms ease-out;
    }

    .flip-front {
        background: linear-gradient(135deg, rgba(58, 102, 156, 0.97), rgba(36, 48, 74, 0.97));
    }

    .flip-back {
        background: linear-gradient(135deg, rgba(38, 154, 130, 0.96), rgba(38, 62, 78, 0.98));
        border: 2px rgba(174, 255, 228, 0.44);
    }

    .paper-card {
        hoverable: true;
        perspective: 720px;
        background: linear-gradient(180deg, rgba(242, 235, 212, 0.96), rgba(206, 218, 230, 0.94));
        foreground: rgba(22, 28, 36, 0.98);
        border: 2px rgba(255, 255, 255, 0.62);
        shadow: 0px 10px 22px 1px rgba(0, 0, 0, 0.22);
    }

    .paper-card:hover {
        rotate: -12deg 10deg 0deg;
        translate: 0px -8px 18px;
        shadow: 0px 22px 36px 4px rgba(0, 0, 0, 0.28);
    }

    .glass-card {
        background: rgba(34, 42, 58, 0.42);
        border: 2px rgba(232, 246, 255, 0.38);
        backdrop-filter: blur(8px) grayscale(0.15);
        shadow: 0px 16px 32px 2px rgba(0, 0, 0, 0.24);
    }
    """.trimIndent()
)

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
