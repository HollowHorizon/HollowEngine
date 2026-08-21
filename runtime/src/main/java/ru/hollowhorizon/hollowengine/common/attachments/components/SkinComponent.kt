package ru.hollowhorizon.hollowengine.common.attachments.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable

@Serializable
enum class SkinModel { WIDE, SLIM }

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:skin")
data class SkinComponent(
    val texture: String = "",
    val model: SkinModel = SkinModel.WIDE,
    val cape: String = "",
)
