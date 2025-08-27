package ru.hollowhorizon.hc.client.models.internal

import ru.hollowhorizon.hc.client.models.gltf.GltfAccessor

data class Channel(
    val node: Int,
    val path: String,
    val times: List<Float>,
    val interpolation: String,
    val values: GltfAccessor,
)