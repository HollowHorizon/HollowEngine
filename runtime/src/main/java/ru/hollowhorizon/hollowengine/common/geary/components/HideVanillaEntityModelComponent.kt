package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:hide_vanilla_entity_model")
data class HideVanillaEntityModelComponent(
    val enabled: Boolean = true,
)
