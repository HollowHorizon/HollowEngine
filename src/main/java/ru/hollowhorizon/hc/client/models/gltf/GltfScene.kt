package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hc.common.utils.nbt.ListOrSingle

@Serializable
data class GltfScene(
    val nodes: ListOrSingle<Int>,
    val name: String? = null
) {
    @Transient
    lateinit var nodeRefs: List<GltfNode>
}