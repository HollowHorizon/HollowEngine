package ru.hollowhorizon.hollowengine.client.ui.inspector

import androidx.compose.runtime.Composable
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCompletionContributor
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextCompletion
import ru.hollowhorizon.hollowengine.client.utils.IconHelper


private const val AssetCompletionLimit = 60

/** Opens host's asset picker for an `@EditorAsset` field; absent when the host has no picker. */
@Composable
internal fun AssetPickerButton(extensions: List<String>, current: String, label: String, onPick: (String) -> Unit) {
    val host = LocalInspectorHost.current ?: return
    if (!host.canPickAssets) return

    InspectorIconButton(
        InspectorIcons.FOLDER,
        InspectorLang.pick,
        tags = listOf("insp-pick-button"),
    ) {
        host.pickAsset(InspectorAssetRequest(label, host.assets(extensions), current, onPick))
    }
}

internal fun assetCompletions(candidates: List<String>): UiCompletionContributor = UiCompletionContributor { context ->
    val prefix = context.text.take(context.caret).trim()
    candidates.asSequence().filter { prefix.isBlank() || it.contains(prefix, ignoreCase = true) }
        .take(AssetCompletionLimit).map { candidate ->
            UiTextCompletion(
                label = candidate,
                insertText = candidate,
                icon = IconHelper.forPath(candidate).toString(),
                wordChars = ":/._-",
            )
        }.toList()
}

/**
 * Where an `@EditorAsset` field's suggestions come from.
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
            manager.listResources(root) { it.path.endsWith(extension) }.keys.map { it.toString() }.sorted()
        }.getOrDefault(emptyList())
    }
}
