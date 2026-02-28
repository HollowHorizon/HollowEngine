package ru.hollowhorizon.hollowengine.common.items.dynamic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemPrefab(
    val id: String? = null,
    @SerialName("max_stack") val maxStack: Int? = null,
    @SerialName("max_damage") val maxDamage: Int? = null,
    val rarity: String? = null,
    @SerialName("fire_resistant") val fireResistant: Boolean = false,
    val tab: String? = null,
    val model: String? = null,
    @SerialName("model_parent") val modelParent: String? = null,
    @SerialName("model_texture") val modelTexture: String? = null,
    @SerialName("model_json") val modelJson: String? = null,
)
