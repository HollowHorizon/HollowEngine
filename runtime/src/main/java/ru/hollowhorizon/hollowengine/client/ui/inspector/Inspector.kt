package ru.hollowhorizon.hollowengine.client.ui.inspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.HollowUiContent
import ru.hollowhorizon.hollowengine.client.ui.Image
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.Row
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.scrollable
import ru.hollowhorizon.hollowengine.client.ui.style

/** The one stylesheet every inspector pulls in; editors add their own chrome on top. */
const val InspectorStylesheet = "hollowengine:ui/styles/inspector.hss"

/**
 * One thing an inspector shows.
 *
 * HollowEngine has several inspectors - entity components, the animator, the rig, the cutscene
 * timeline, the model and sound editors, and this is all they have in common: heading and body
 * of fields.
 */
class InspectorTarget(
    val id: String,
    val title: String,
    val icon: String? = null,
    val subtitle: String? = null,
    val styles: List<String> = emptyList(),
    val content: HollowUiContent,
)

/**
 * What the fields need from whatever opened them.
 */
interface InspectorHost {
    /** Paths an `@EditorAsset` field can offer for [extensions]. */
    fun assets(extensions: List<String>): List<String> =
        extensions.flatMap(EditorAssetSources::list).distinct().sorted()

    /** Whether [pickAsset] opens anything; hosts without a picker leave the browse button out. */
    val canPickAssets: Boolean get() = false

    fun pickAsset(request: InspectorAssetRequest) = Unit
}

class InspectorAssetRequest(
    val title: String,
    val candidates: List<String>,
    val current: String,
    val onPick: (String) -> Unit,
)

val LocalInspectorHost: ProvidableCompositionLocal<InspectorHost?> = compositionLocalOf { null }

/**
 * The panel itself: heading, then the target's own fields under a scrollbar. [leading] is for chrome
 * an editor wants beside the title, such as a back arrow or a layer switcher.
 */
@Composable
fun InspectorPanel(
    target: InspectorTarget?,
    modifier: Modifier = Modifier,
    empty: String = "",
    leading: HollowUiContent? = null,
    trailing: HollowUiContent? = null,
) {
    val styled = target?.styles.orEmpty()
        .fold(modifier.style(InspectorStylesheet)) { chain, sheet -> chain.style(sheet) }

    Column(
        id = "inspector-${target?.id ?: "empty"}",
        tags = listOf("insp-panel"),
        modifier = styled.scrollable(horizontal = false),
    ) {
        if (leading != null || trailing != null || target != null) {
            Row(tags = listOf("insp-head")) {
                leading?.invoke()
                target?.icon?.let { Image(it, tags = listOf("insp-icon")) }
                target?.let { Text(it.title, tags = listOf("insp-title")) }
                target?.subtitle?.let { Text(it, tags = listOf("insp-subtitle")) }
                trailing?.invoke()
            }
        }

        if (target == null) {
            if (empty.isNotEmpty()) Text(empty, tags = listOf("insp-empty"))
            return@Column
        }

        Column(tags = listOf("insp-body")) { target.content() }
    }
}
