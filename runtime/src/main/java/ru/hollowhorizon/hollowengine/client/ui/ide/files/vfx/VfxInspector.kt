package ru.hollowhorizon.hollowengine.client.ui.ide.files.vfx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeVfxDocument
import ru.hollowhorizon.hollowengine.client.ui.inspector.*
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.vfx.*

/** What the shared inspector shows for the selected node of an effect. */
internal fun vfxNodeInspector(
    document: HollowIdeVfxDocument,
    state: VfxEditorState,
    nodeId: String,
): InspectorTarget {
    val node = document.effect.node(nodeId)
    val type = node?.let(VfxNodeTypes::of)
    return InspectorTarget(
        id = "vfx-node-$nodeId",
        title = node?.name ?: nodeId,
        subtitle = type?.titleKey?.lang,
        icon = type?.icon,
    ) {
        val live = document.effect.node(nodeId) ?: return@InspectorTarget
        CompositionLocalProvider(
            LocalVfxFieldFocus provides { property -> state.focusProperty(nodeId, property) },
            LocalVfxDriven provides vfxDrivenLookup(document, state.preview, nodeId),
        ) {
            NodeFields(document, state, live)
        }
    }
}

internal fun HollowIdeVfxDocument.replace(node: VfxNodeSpec) = edit(mergeKey = "node:${node.id}") { it.withNode(node) }

@Composable
private fun NodeFields(document: HollowIdeVfxDocument, state: VfxEditorState, node: VfxNodeSpec) {
    Folding(state, "node", vfxText("section_node")) {
        NameRow(vfxText("name"), node.name) { document.replace(node.withCommon(name = it)) }
        ToggleRow(vfxText("enabled"), node.enabled) { document.replace(node.withCommon(enabled = it)) }
        if (node is VfxParticleRendererSpec) {
            val parent = document.effect.parentOf(node.id)
            Hint(vfxText(if (parent is VfxEmitterSpec) "renders_particles" else "renders_once"))
        }
    }

    Folding(state, "transform", vfxText("section_transform"), openByDefault = false) {
        val transform = node.transform
        VfxFloat3Row(vfxText("position"), transform.position, VfxProperty.POSITION) {
            document.replace(node.withCommon(transform = transform.copy(position = it)))
        }
        VfxFloat3Row(vfxText("rotation"), transform.rotation, VfxProperty.ROTATION) {
            document.replace(node.withCommon(transform = transform.copy(rotation = it)))
        }
        VfxFloat3Row(vfxText("scale"), transform.scale, VfxProperty.SCALE) {
            document.replace(node.withCommon(transform = transform.copy(scale = it)))
        }
    }

    when (node) {
        is VfxEmitterSpec -> EmitterFields(document, state, node)
        is VfxPlaneSpec -> PlaneFields(document, state, node)
        is VfxMeshSpec -> MeshFields(document, state, node)
        is VfxModelSpec -> ModelFields(document, state, node)
        is VfxTrailSpec -> TrailFields(document, state, node)
        is VfxBeamSpec -> BeamFields(document, state, node)
        is VfxPostEffectSpec -> PostEffectFields(document, state, node)
        is VfxCameraShakeSpec -> CameraShakeFields(document, state, node)
        else -> Unit
    }
}

/** A section that remembers whether it is open per effect editor. */
@Composable
internal fun Folding(
    state: VfxEditorState,
    key: String,
    title: String,
    openByDefault: Boolean = true,
    content: @Composable () -> Unit,
) {
    val open = state.isSectionOpen(key, openByDefault)
    CollapsibleSection(title, open, id = "vfx-section-$key", onToggle = { state.toggleSection(key, openByDefault) }) {
        content()
    }
}

/** What the inspector shows when nothing in the tree is selected. */
internal fun vfxEffectInspector(document: HollowIdeVfxDocument, state: VfxEditorState): InspectorTarget =
    InspectorTarget(id = "vfx-effect", title = vfxText("effect")) {
        val effect = document.effect
        Folding(state, "timeline", vfxText("section_timeline")) {
            VfxNumberRow(
                vfxText("timeline_duration"),
                effect.timeline.duration,
                vfxText("timeline_duration_hint"),
                min = 0.1f
            ) { duration ->
                document.edit { it.copy(timeline = it.timeline.copy(duration = duration)) }
            }
            ToggleRow(vfxText("timeline_loop"), effect.timeline.loop) { loop ->
                document.edit { it.copy(timeline = it.timeline.copy(loop = loop)) }
            }
        }
        Folding(state, "stats", vfxText("section_stats")) {
            Readonly(vfxText("nodes"), effect.walk().size.toString())
            Readonly(vfxText("expressions"), effect.expressions().size.toString())
        }
    }
