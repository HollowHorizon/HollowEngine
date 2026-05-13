package ru.hollowhorizon.hollowengine.client.ui.screen

import ru.hollowhorizon.hollowengine.client.ui.HollowUi
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.TransitionEasing
import ru.hollowhorizon.hollowengine.client.ui.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiScope
import ru.hollowhorizon.hollowengine.client.ui.UiState
import ru.hollowhorizon.hollowengine.client.ui.UiTransition
import ru.hollowhorizon.hollowengine.client.ui.px
import kotlin.math.abs
import kotlin.math.sign

class HollowUiDemoScreen : HollowUiScreen("Hollow UI Demo", DemoStyles) {
    private var selectedTab = "overview"
    private val freeNodeOffsets = mutableMapOf<Int, DemoOffset>()
    private var layoutGlassOffset = DemoOffset.Zero

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
            Box(
                id = "layout-glass",
                tags = listOf("layout-glass", "glass-card"),
                modifier = Modifier.then(
                    Modifier.position((140 + layoutGlassOffset.x).px, (260 + layoutGlassOffset.y).px),
                    Modifier.input(hoverable = true, draggable = true),
                ),
            ) {
                Text("Drag Glass", tags = listOf("card-title"))
                Text("Backdrop blur over moving free nodes.", tags = listOf("body"))
            }
        }
    }

    private fun UiScope.transforms() {
        Box(tags = listOf("panel-grid")) {
            val rect = layoutRect("tilt-card")
            val hoverRotate = if (rect != null && isHovered("tilt-card")) {
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
                Text("Lifted Paper", tags = listOf("card-title", "paper-title"))
                Text("3D transform keeps the shadow and rounded shape together.", tags = listOf("body", "paper-body"))
            }
            Box(tags = listOf("effect-card", "glass-card"), modifier = Modifier.position(220.px, 168.px)) {
                Text("Backdrop Chain", tags = listOf("card-title"))
                Text("Backdrop filter samples the already rendered target below.", tags = listOf("body"))
            }
            Box(tags = listOf("effect-card", "css-lift-card"), modifier = Modifier.position(420.px, 168.px)) {
                Text("CSS Lift", tags = listOf("card-title", "paper-title"))
                Text("Multiple shadows and 3D rotation from CSS-style references.", tags = listOf("body", "paper-body"))
            }
            Box(
                tags = listOf("effect-card", "soft-focus-card"),
                modifier = Modifier.then(Modifier.position(620.px, 168.px), Modifier.input(hoverable = true)),
            ) {
                Text("Soft Focus", tags = listOf("card-title", "soft-title"))
                Text("Blur fades out on hover without dark transparent corners.", tags = listOf("body", "soft-body"))
            }
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
