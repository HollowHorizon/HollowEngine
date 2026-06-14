package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.serialization.Serializable

@Serializable
data class HollowAddonMetadata(
    val id: String,
    val version: String,
    val name: String = id,
    val entrypoint: String,
    val engineVersion: String? = null,
    val requiredClasses: List<String> = emptyList(),
)
