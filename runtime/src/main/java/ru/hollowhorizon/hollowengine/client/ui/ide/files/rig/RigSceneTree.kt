package ru.hollowhorizon.hollowengine.client.ui.ide.files.rig

import ru.hollowhorizon.hollowengine.client.models.internal.v2.RuntimeNode
import ru.hollowhorizon.hollowengine.client.models.internal.v2.walk
import ru.hollowhorizon.hollowengine.client.ui.ide.SceneTarget
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeRigDocument
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTreeItem
import ru.hollowhorizon.hollowengine.common.models.ModelRig
import ru.hollowhorizon.hollowengine.common.models.RigAttachmentTypes
import ru.hollowhorizon.hollowengine.common.models.RigBone

/** The id of the row that stands for the model itself, above its bones. */
private const val RigRootId = "rig-root"

private const val RigIcon = "hollowengine:textures/gui/icons/files/rig.svg"
private const val VisibleIcon = "hollowengine:textures/gui/icons/visible.svg"
private const val InvisibleIcon = "hollowengine:textures/gui/icons/invisible.svg"
private const val AddIcon = "hollowengine:textures/gui/icons/add.svg"

/**
 * The bones as the scene window shows them: the model as the root, its skeleton under it. The eye of
 * a bone hides it in the preview only; whether it is drawn in the game is the inspector's field.
 */
internal fun rigSceneTarget(document: HollowIdeRigDocument, state: RigEditorState, title: String): SceneTarget {
    val nodes = state.viewer.nodes
    return SceneTarget(
        id = "rig-bones",
        items = buildList {
            add(
                UiTreeItem(
                    id = RigRootId,
                    label = title,
                    depth = 0,
                    payload = null,
                    icon = RigIcon,
                    hasChildren = nodes.isNotEmpty(),
                    expanded = state.rootExpanded,
                    selected = state.selected == null,
                )
            )
            if (state.rootExpanded) appendBones(nodes, document.rig, state.expanded, state.selected, depth = 1)
        },
        onSelect = { id -> state.selected = id.takeUnless { it == RigRootId } },
        onToggle = { id ->
            when (id) {
                RigRootId -> state.rootExpanded = !state.rootExpanded
                in state.expanded -> state.expanded.remove(id)
                else -> state.expanded.add(id)
            }
        },
        hint = rigText("no_bones").takeIf { nodes.isEmpty() },
        menu = { id -> id?.takeUnless { it == RigRootId }?.let { boneMenu(document, state, it) }.orEmpty() },
        onIconClick = { id -> nodes.findBone(id)?.let(state.viewer::toggleNodeVisibility) },
    )
}

private fun MutableList<UiTreeItem<Any?>>.appendBones(
    nodes: List<RuntimeNode>,
    rig: ModelRig,
    expanded: List<String>,
    selected: String?,
    depth: Int,
) {
    nodes.forEach { node ->
        val bone = rig.bone(node.name)
        val marks = listOfNotNull(
            bone?.attachments?.size?.takeIf { it > 0 }?.let { "●$it" },
            bone?.alias?.takeIf { it.isNotBlank() }?.let { "→$it" },
        ).joinToString(" ")

        add(
            UiTreeItem(
                id = node.name,
                label = if (marks.isEmpty()) node.name else "${node.name}  $marks",
                depth = depth,
                payload = null,
                icon = if (node.isVisible) VisibleIcon else InvisibleIcon,
                hasChildren = node.children.isNotEmpty(),
                expanded = node.name in expanded,
                selected = node.name == selected,
            )
        )
        if (node.children.isNotEmpty() && node.name in expanded) {
            appendBones(node.children, rig, expanded, selected, depth + 1)
        }
    }
}

/** What a right click on a bone offers: hanging something on it, and hiding it in the preview. */
private fun boneMenu(document: HollowIdeRigDocument, state: RigEditorState, bone: String): List<UiDropdownItem> {
    val node = state.viewer.nodes.findBone(bone)
    val kinds = RigAttachmentTypes.all.filter { it.createDefault != null }
    return listOfNotNull(
        UiDropdownItem(
            rigText("attach"),
            icon = AddIcon,
            children = kinds.map { type ->
                val create = requireNotNull(type.createDefault)
                UiDropdownItem(type.title()) {
                    document.edit { rig ->
                        val current = rig.bone(bone) ?: RigBone.EMPTY
                        rig.withBone(bone, current.withAttachment(create(freeAttachmentId(current, type))))
                    }
                    state.selected = bone
                }
            },
        ).takeIf { kinds.isNotEmpty() },
        node?.let {
            UiDropdownItem(
                rigText(if (it.isVisible) "hide_in_preview" else "show_in_preview"),
                icon = if (it.isVisible) InvisibleIcon else VisibleIcon,
                separatorBefore = kinds.isNotEmpty(),
            ) { state.viewer.toggleNodeVisibility(it) }
        },
    )
}

private fun List<RuntimeNode>.findBone(name: String): RuntimeNode? =
    firstNotNullOfOrNull { root -> root.walk().firstOrNull { it.name == name } }
