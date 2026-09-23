package ru.hollowhorizon.hollowengine.client.ui.ide.files.vfx

import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeVfxDocument
import ru.hollowhorizon.hollowengine.client.vfx.VfxNodeRuntime
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxAnimatables

/**
 * What the timeline holds for the properties of [nodeId], as the preview plays it.
 */
internal fun vfxDrivenLookup(
    document: HollowIdeVfxDocument,
    preview: VfxPreviewState,
    nodeId: String,
): (String) -> VfxDrivenValue? = lookup@{ property ->
    val track = document.effect.timeline.track(nodeId, property) ?: return@lookup null
    val channels = track.curves.filter { it.visible && it.keys.isNotEmpty() }.map { it.channel }.toSet()
    if (channels.isEmpty()) return@lookup null

    preview.time
    preview.revision
    VfxDrivenValue(currentValues(preview.instance?.node(nodeId), property), channels)
}

/** The values the running node has for [property] this frame. */
private fun currentValues(node: VfxNodeRuntime?, property: String): FloatArray {
    if (node == null) return FloatArray(4)
    return when (property) {
        VfxAnimatables.POSITION -> node.transform.position.asArray()
        VfxAnimatables.ROTATION -> node.transform.rotation.asArray()
        VfxAnimatables.SCALE -> node.transform.scale.asArray()
        VfxAnimatables.ENABLED -> floatArrayOf(if (node.enabled) 1f else 0f)
        else -> node.drive(property)?.values?.copyOf() ?: FloatArray(4)
    }
}

private fun Vec3f.asArray() = floatArrayOf(x, y, z)
