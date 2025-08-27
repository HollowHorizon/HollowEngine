package ru.hollowhorizon.hc.client.models.gltf

import kotlinx.serialization.Serializable

@Serializable
data class GltfAsset(
    val copyright: String? = null,
    val generator: String? = null,
    val version: String,
    val minVersion: String? = null
)