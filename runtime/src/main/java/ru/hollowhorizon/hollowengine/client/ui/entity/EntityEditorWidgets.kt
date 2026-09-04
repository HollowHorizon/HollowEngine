package ru.hollowhorizon.hollowengine.client.ui.entity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCompletionContributor
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextCompletion
import ru.hollowhorizon.hollowengine.client.ui.widgets.tooltipOnHover
import ru.hollowhorizon.hollowengine.client.utils.IconHelper
import ru.hollowhorizon.hollowengine.client.utils.lang

internal object EntityEditorIcons {
    const val ADD = "hollowengine:textures/gui/icons/add.svg"
    const val REMOVE = "hollowengine:textures/gui/icons/remove.svg"
    const val SEARCH = "hollowengine:textures/gui/icons/search.svg"
    const val RELOAD = "hollowengine:textures/gui/icons/reload.svg"
    const val ZOOM_RESET = "hollowengine:textures/gui/icons/zoom_out.svg"
    const val FOLDER = "hollowengine:textures/gui/icons/folder.svg"
    const val SCRIPT = "hollowengine:textures/gui/icons/file_kts.svg"
    const val COMPONENT = "hollowengine:textures/gui/icons/box.svg"
    const val CLOSE = "hollowengine:textures/gui/icons/cross.svg"
    const val STATE = "hollowengine:textures/gui/icons/state.svg"
}

internal object EntityEditorLang {
    private const val ROOT = "hollowengine.gui.entity_editor."

    val title: String get() = (ROOT + "title").lang
    val components: String get() = (ROOT + "components").lang
    val scripts: String get() = (ROOT + "scripts").lang
    val addComponent: String get() = (ROOT + "add_component").lang
    val add: String get() = (ROOT + "add").lang
    val remove: String get() = (ROOT + "remove").lang
    val refresh: String get() = (ROOT + "refresh").lang
    val search: String get() = (ROOT + "search").lang
    val searchHint: String get() = (ROOT + "search_hint").lang
    val notSet: String get() = (ROOT + "not_set").lang
    val emptyList: String get() = (ROOT + "empty_list").lang
    val nothingFound: String get() = (ROOT + "nothing_found").lang
    val virtual: String get() = (ROOT + "virtual").lang
    val virtualHint: String get() = (ROOT + "virtual_hint").lang
    val attached: String get() = (ROOT + "attached").lang
    val available: String get() = (ROOT + "available").lang
    val noScripts: String get() = (ROOT + "no_scripts").lang
    val noSuitableScripts: String get() = (ROOT + "no_suitable_scripts").lang
    val attach: String get() = (ROOT + "attach").lang
    val detach: String get() = (ROOT + "detach").lang
    val pick: String get() = (ROOT + "pick").lang
    val noPreview: String get() = (ROOT + "no_preview").lang
    val allAdded: String get() = (ROOT + "all_added").lang
    val close: String get() = (ROOT + "close").lang
    val inventory: String get() = (ROOT + "inventory").lang
    val inventoryHint: String get() = (ROOT + "inventory_hint").lang
    val equipment: String get() = (ROOT + "equipment").lang
    val carried: String get() = (ROOT + "carried").lang
    val statComponents: String get() = (ROOT + "stat_components").lang
    val statScripts: String get() = (ROOT + "stat_scripts").lang
    val statHealth: String get() = (ROOT + "stat_health").lang
    val autoRotate: String get() = (ROOT + "auto_rotate").lang
    val resetView: String get() = (ROOT + "reset_view").lang
    val busy: String get() = (ROOT + "busy").lang
    val playerItems: String get() = (ROOT + "player_items").lang

    fun unsupported(type: String): String = (ROOT + "unsupported").lang.format(type)
}

@Composable
internal fun PillFlow(content: HollowUiContent) {
    Layout(
        content = content,
        modifier = Modifier.size(100.percent, UiLength.Fit).gap(3.px).lineSpacing(3f).textWrap(),
        measurePolicy = UiMeasurePolicies.InlineFlow,
    )
}

@Composable
internal fun EditorPill(label: String, active: Boolean, onClick: () -> Unit) {
    InlineWidget(
        id = "ee-pill-$label",
        tags = if (active) listOf("ee-pill", "active") else listOf("ee-pill"),
        modifier = Modifier
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    ) {
        Text(label, tags = listOf("ee-pill-label"))
    }
}

@Composable
internal fun BusySpinner() {
    val frame by produceState(0) {
        while (true) {
            withFrameNanos { }
            value++
        }
    }
    Image(
        EntityEditorIcons.RELOAD,
        tags = listOf("ee-busy"),
        modifier = Modifier.rotate(0f, 0f, (frame * 4f) % 360f).tooltipOnHover(EntityEditorLang.busy),
    )
}

@Composable
internal fun EditorArrow(expanded: Boolean) {
    Box(
        tags = listOf("ee-arrow"),
        attributes = mapOf("expanded" to if (expanded) "true" else "false"),
    )
}

@Composable
internal fun EditorIconButton(
    icon: String,
    tooltip: String,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    Image(
        icon,
        tags = listOf("ee-icon-button") + tags,
        modifier = modifier
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .tooltipOnHover(tooltip)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    )
}

@Composable
internal fun EditorButton(
    label: String,
    icon: String? = null,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    Row(
        tags = listOf("ee-button") + tags,
        modifier = modifier
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .onClick { event ->
                if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT) onClick()
                event.consume()
            },
    ) {
        icon?.let { Image(it, tags = listOf("ee-button-icon")) }
        Text(label, tags = listOf("ee-button-label"))
    }
}

@Composable
internal fun AssetPickerButton(extensions: List<String>, current: String, label: String, onPick: (String) -> Unit) {
    val session = LocalEntityEditorSession.current ?: return
    EditorIconButton(EntityEditorIcons.FOLDER, EntityEditorLang.pick, tags = listOf("ee-pick-button")) {
        session.pendingPicker = AssetPickerRequest(label, session.assets(extensions), current, onPick)
    }
}

internal fun assetCompletions(candidates: List<String>): UiCompletionContributor = UiCompletionContributor { context ->
    val prefix = context.text.take(context.caret).trim()
    candidates.asSequence()
        .filter { prefix.isBlank() || it.contains(prefix, ignoreCase = true) }
        .take(AssetCompletionLimit)
        .map { candidate ->
            UiTextCompletion(
                label = candidate,
                insertText = candidate,
                icon = IconHelper.forPath(candidate).toString(),
                wordChars = ":/._-",
            )
        }
        .toList()
}

private const val AssetCompletionLimit = 60

/**
 * Where an `@EditorAsset` field's suggestions come from.
 *
 * Sources are registered rather than hardcoded so an addon that ships a new kind of asset can offer it
 * in the editor without this file knowing about it.
 */
object EditorAssetSources {
    private val providers = LinkedHashMap<String, () -> List<String>>()

    fun register(vararg extensions: String, provider: () -> List<String>) {
        extensions.forEach {
            providers[it] = provider
        }
    }

    fun list(extension: String): List<String> =
        providers[extension]?.invoke()?.sorted() ?: resourcesEndingWith(extension)

    private fun resourcesEndingWith(extension: String): List<String> {
        val manager = Minecraft.getInstance().resourceManager ?: return emptyList()
        val root = when {
            extension.endsWith("png") -> "textures"
            extension.endsWith("ogg") || extension.endsWith("wav") -> "sounds"
            else -> "models"
        }
        return runCatching {
            manager.listResources(root) { it.path.endsWith(extension) }.keys
                .map { it.toString() }
                .sorted()
        }.getOrDefault(emptyList())
    }
}
