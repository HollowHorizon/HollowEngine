package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.*
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorColors
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorStylesheet
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeView
import ru.hollowhorizon.hollowengine.client.utils.lang

/**
 * A hierarchy, that scene window shows.
 */
class SceneTarget(
    val id: String,
    val title: String,
    val items: List<UiTreeItem<Any?>>,
    /** `null` when the selection is dropped. */
    val onSelect: (String?) -> Unit,
    val onToggle: (String) -> Unit,
    /** What to say when the tree is empty. */
    val empty: String,
    /** Buttons the owner wants above the tree, such as adding and removing. */
    val actions: HollowUiContent? = null,
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

/**
 * The scene window, hierarchy of whatever is open, or nothing.
 */
@Composable
internal fun SceneDock() {
    val target = IdeScenes.current

    Column(
        modifier = Modifier.size(100.percent, 100.percent).style(AnimatorStylesheet).background(AnimatorColors.Panel)
            .padding(6.px).gap(4.px),
    ) {
        if (target == null) {
            Text(
                "hollowengine.gui.ide.windows.scene_empty".lang,
                modifier = Modifier.fontSize(9f).foreground(AnimatorColors.Muted),
            )
            return@Column
        }

        Row(modifier = Modifier.size(100.percent).gap(4.px)) {
            Text(
                target.title,
                modifier = Modifier.fontSize(10f).foreground(AnimatorColors.Muted).grow(1f),
            )
            target.actions?.invoke()
        }

        if (target.items.isEmpty()) {
            Text(target.empty, modifier = Modifier.fontSize(9f).foreground(AnimatorColors.Muted))
            return@Column
        }

        UiTreeView(
            items = target.items,
            onToggle = { item -> target.onToggle(item.id) },
            onSelect = { item, event -> target.onSelect(item.id.takeUnless { item.selected && event.isCtrlDown() }) },
            onBackgroundClick = { target.onSelect(null) },
            fillRowWidth = true,
            modifier = Modifier.size(100.percent, 0.px).grow(1f).scrollable(horizontal = false)
                .onKeyInput { input ->
                    if (input.key != GLFW.GLFW_KEY_ESCAPE || target.items.none { it.selected }) return@onKeyInput
                    target.onSelect(null)
                    input.consume()
                }
        )
    }
}
