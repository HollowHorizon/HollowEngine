package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.*
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.widgets.ContextMenu
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiKeyInput
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeView
import ru.hollowhorizon.hollowengine.client.utils.lang

/**
 * A hierarchy, that scene window shows.
 */
class SceneTarget(
    val id: String,
    val items: List<UiTreeItem<Any?>>,
    /** `null` when the selection is dropped. */
    val onSelect: (String?) -> Unit,
    val onToggle: (String) -> Unit,
    /** A line under the tree, for a hierarchy that has nothing in it yet. */
    val hint: String? = null,
    /** What a right click offers on the item with this id, or on the empty space under the rows (null). */
    val menu: ((String?) -> List<UiDropdownItem>)? = null,
    /** Keys pressed while the tree has focus, after the window's own; true when one was handled. */
    val onKey: ((UiKeyInput) -> Boolean)? = null,
    /** Puts the dragged item under the one it was dropped on; null when the hierarchy cannot be rearranged. */
    val onMove: ((dragged: String, target: String) -> Boolean)? = null,
    val canMove: (dragged: String, target: String) -> Boolean = { _, _ -> true },
)

/**
 * Whose hierarchy the scene window shows. Last publisher wins, like the inspector.
 */
object IdeScenes {
    var current: SceneTarget? by mutableStateOf(null)
        private set

    private var owner: String? = null

    fun publish(source: String, target: SceneTarget?) {
        if (target == null) {
            release(source)
            return
        }
        owner = source
        current = target
    }

    fun release(source: String) {
        if (owner != source) return
        owner = null
        current = null
    }
}

/**
 * Keeps [target] in the scene window for as long as this editor is composed.
 *
 * Unlike the inspector, the tree itself changes as the file is edited, so [key] normally carries the
 * revision of whatever is being shown.
 */
@Composable
fun PublishScene(source: String, key: Any?, target: () -> SceneTarget?) {
    LaunchedEffect(source, key) { IdeScenes.publish(source, target()) }
    DisposableEffect(source) { onDispose { IdeScenes.release(source) } }
}

/** An item of a scene tree being dragged: which hierarchy it came from, so another cannot take it. */
private data class SceneDrag(val scene: String, val item: String)

/**
 * The scene window: the hierarchy of whatever is open, looked at and worked with the way the project
 * tree is. A right click opens the menu of the item under the pointer, rows drag onto each other, and
 * the selection answers the usual keys.
 */
@Composable
internal fun SceneDock() {
    val target = IdeScenes.current
    var menu by remember { mutableStateOf<SceneMenu?>(null) }

    Column(tags = listOf("ide-panel", "scene-panel"), modifier = Modifier.size(100.percent, 100.percent)) {
        if (target == null) {
            Text("hollowengine.gui.ide.windows.scene_empty".lang, tags = listOf("scene-hint"))
            return@Column
        }

        val openMenu = { item: String?, event: UiEvent ->
            val items = target.menu?.invoke(item).orEmpty()
            menu = if (items.isEmpty()) null else SceneMenu(event.x, event.y, items)
        }
        val move = target.onMove

        UiTreeView(
            items = target.items,
            onToggle = { item -> target.onToggle(item.id) },
            onSelect = { item, event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    if (!item.selected) target.onSelect(item.id)
                    openMenu(item.id, event)
                    return@UiTreeView
                }
                menu = null
                target.onSelect(item.id.takeUnless { item.selected && event.isCtrlDown() })
            },
            onBackgroundClick = {
                menu = null
                target.onSelect(null)
            },
            onBackgroundContextMenu = { event -> openMenu(null, event) },
            fillRowWidth = true,
            dragItem = if (move == null) null else { item ->
                UiDragItem(payload = SceneDrag(target.id, item.id), icon = item.icon, label = item.label)
            },
            onDrop = if (move == null) null else { item, dragged ->
                val drag = dragged.payload as? SceneDrag
                drag != null && move(drag.item, item.id)
            },
            canDrop = { item, dragged ->
                val drag = dragged.payload as? SceneDrag
                drag != null && drag.scene == target.id && drag.item != item.id && target.canMove(drag.item, item.id)
            },
            modifier = Modifier.size(100.percent, 0.px).grow(1f).onKeyInput { input ->
                if (input.key == GLFW.GLFW_KEY_ESCAPE && target.items.any { it.selected }) {
                    target.onSelect(null)
                    input.consume()
                    return@onKeyInput
                }
                if (target.onKey?.invoke(input) == true) input.consume()
            },
        )

        target.hint?.let { Text(it, tags = listOf("scene-hint")) }

        menu?.let { open ->
            ContextMenu(
                id = "scene-context-menu",
                anchorBounds = UiRect(open.x, open.y, 0f, 0f),
                alignment = UiPopupAlignment.Cursor,
                items = open.items,
                onExpandedChange = { if (!it) menu = null },
            )
        }
    }
}

private class SceneMenu(val x: Float, val y: Float, val items: List<UiDropdownItem>)
