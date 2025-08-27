package ru.hollowhorizon.hollowengine.client.models.internal

import ru.hollowhorizon.hollowengine.client.models.gltf.GltfAccessor

data class Channel(
    val node: Int,
    val path: String,
    val times: List<Float>,
    val interpolation: String,
    val values: GltfAccessor,
)