package ru.hollowhorizon.hollowengine.client.ui.ide.files.rig

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.models.internal.rig.*
import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.client.models.internal.v2.walk
import ru.hollowhorizon.hollowengine.client.render.DebugSkeletonRenderer
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeOpenFile
import ru.hollowhorizon.hollowengine.client.ui.ide.PublishScene
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeRigDocument
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorIconButton
import ru.hollowhorizon.hollowengine.client.ui.ide.files.animator.AnimatorStylesheet
import ru.hollowhorizon.hollowengine.client.ui.inspector.PublishInspector
import ru.hollowhorizon.hollowengine.client.ui.widgets.Model
import ru.hollowhorizon.hollowengine.client.ui.widgets.ModelViewerState
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import kotlin.time.Duration.Companion.milliseconds

private const val AutoSaveDelayMillis = 900L

private const val PhysicsIcon = "hollowengine:textures/gui/icons/timeline/play.svg"
private const val StopIcon = "hollowengine:textures/gui/icons/timeline/pause.svg"
private const val SkeletonIcon = "hollowengine:textures/gui/icons/rig/skeleton.svg"
private const val ColliderIcon = "hollowengine:textures/gui/icons/rig/colliders.svg"
private const val GenerateIcon = "hollowengine:textures/gui/icons/reload.svg"

/**
 * The editor for a `.rig` file.
 *
 * The bones go to the scene window and what hangs on the selected one to the inspector; the editor
 * itself is the preview, like the effect editor's.
 */
@Composable
internal fun RigEditorPanel(file: HollowIdeOpenFile) {
    val document = file.document as HollowIdeRigDocument
    val state = remember(document) { document.editorState { RigEditorState(file.path.toModelId()) } }
    val viewer = state.viewer
    val model by viewer.modelFlow.collectAsState()
    var preview by remember(document) { mutableStateOf<RigPreview?>(null) }

    LaunchedEffect(document.revision) {
        viewer.attachment.rig = document.rig
        file.updateDirty(document.isModified)
        if (!document.isModified) return@LaunchedEffect
        delay(AutoSaveDelayMillis.milliseconds)
        if (document.isModified) file.save()
    }

    LaunchedEffect(preview) {
        while (true) withFrameNanos { }
    }

    DisposableEffect(document) {
        onDispose {
            preview?.let(RigPreview::close)
            preview = null
        }
    }

    viewer.debugDraw = { lines ->
        if (state.showSkeleton) DebugSkeletonRenderer.draw(viewer.attachment, lines, state.selected)
        if (state.showColliders) {
            RigOverlays.all.forEach { it.draw(viewer.attachment, lines, state.selected) }
            preview?.draw(lines)
        }
    }

    val bones = remember(model) { viewer.nodes.flatMap { node -> node.walk().map(RuntimeNode::name) } }

    PublishInspector(source = "rig-${file.path}", key = state.selected to bones) {
        state.selected?.let { bone -> rigInspectorTarget(document, bone, bones) }
    }

    PublishScene(
        source = "rig-${file.path}",
        key = listOf(
            document.revision, model, state.selected, state.expanded.toList(), state.rootExpanded,
            viewer.nodeVisibilityRevision,
        ),
    ) {
        rigSceneTarget(document, state, file.path.substringAfterLast('/').substringBeforeLast('.'))
    }

    Column(
        tags = listOf("ide-file-panel"),
        modifier = Modifier.style(AnimatorStylesheet).focusScope(),
    ) {
        Box(mode = UiBoxMode.STACK, modifier = Modifier.size(100.percent, 0.px).grow(1f)) {
            RigViewport(
                viewer = viewer,
                preview = preview,
                onPick = { candidates ->
                    val index = candidates.indexOfFirst { it.name == state.selected }
                    val next = (index + 1) % candidates.size.coerceAtLeast(1)
                    state.selected = candidates.getOrNull(next)?.name
                },
            )
            Toolbar(document, state, preview) { preview = preview.toggled(viewer) }
        }
    }
}

@Composable
private fun Toolbar(document: HollowIdeRigDocument, state: RigEditorState, preview: RigPreview?, onPhysics: () -> Unit) {
    val viewer = state.viewer
    Row(tags = listOf("viewport-toolbar"), modifier = Modifier.position(8.px, 8.px)) {
        AnimatorIconButton(SkeletonIcon, rigText("toggle_skeleton"), size = 12f, active = state.showSkeleton) {
            state.showSkeleton = !state.showSkeleton
        }
        if (RigOverlays.all.isNotEmpty()) {
            AnimatorIconButton(ColliderIcon, rigText("toggle_colliders"), size = 12f, active = state.showColliders) {
                state.showColliders = !state.showColliders
            }
        }
        if (RigPreviews.isAvailable) {
            AnimatorIconButton(
                icon = if (preview == null) PhysicsIcon else StopIcon,
                tooltip = rigText(if (preview == null) "start_physics" else "stop_physics"),
                size = 12f,
                active = preview != null,
                onClick = onPhysics,
            )
        }
        RigGenerators.all.forEach { entry ->
            AnimatorIconButton(GenerateIcon, entry.titleKey.lang, size = 12f) {
                viewer.attachment.ensureReady()
                document.edit { entry.generator.generate(viewer.attachment, it) }
            }
        }
    }
}

/**
 * What the editor is looking at, as opposed to what it is editing.
 */
internal class RigEditorState(modelId: String) {
    val viewer = ModelViewerState(modelId)
    var selected by mutableStateOf<String?>(null)
    var showSkeleton by mutableStateOf(true)
    var showColliders by mutableStateOf(true)

    /** Which bones are open in the scene window, kept here so they survive switching files. */
    val expanded = mutableStateListOf<String>()
    var rootExpanded by mutableStateOf(true)
}

private fun RigPreview?.toggled(viewer: ModelViewerState): RigPreview? {
    if (this != null) {
        viewer.instance.animator.remove(RigPreviewLayer.ID)
        close()
        return null
    }

    val started = RigPreviews.create() ?: return null
    viewer.instance.animator.add(RigPreviewLayer(started))
    return started
}

@Composable
private fun RigViewport(viewer: ModelViewerState, preview: RigPreview?, onPick: (List<RuntimeNode>) -> Unit) {
    Model(
        state = viewer,
        id = "rig-viewport",
        tags = listOf("ide-file-viewport"),
        modifier = Modifier.size(100.percent, 100.percent).clip().input(hoverable = true, clickable = true)
            .onClick { event ->
                if (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return@onClick
                onPick(viewer.bonesAt(event.localX, event.localY))
                event.consume()
            },
        onDrag = { event ->
            val physics = preview
            if (physics == null || event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT) false
            else {
                physics.push(Vec3f(-event.deltaX, -event.deltaY, 0f) * PUSH_STRENGTH)
                true
            }
        },
    )
}

private const val PUSH_STRENGTH = 0.05f

internal fun rigText(name: String): String = "hollowengine.gui.rig_editor.$name".lang

internal fun String.toModelId(): String = substringAfter("assets/").replaceFirst("/", ":").removeSuffix(".rig")
