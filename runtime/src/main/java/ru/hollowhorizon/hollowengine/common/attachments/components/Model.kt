package ru.hollowhorizon.hollowengine.common.attachments.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable

/**
 * Which model an entity or node shows.
 */
@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:model")
data class Model(
    val model: String = "hollowengine:models/entity/player_model.gltf",
)
