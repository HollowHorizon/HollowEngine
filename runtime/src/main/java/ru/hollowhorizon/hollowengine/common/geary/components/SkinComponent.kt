package ru.hollowhorizon.hollowengine.common.geary.components

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
@EditorIcon("hollowengine:textures/gui/icons/color_picker.png")
data class SkinComponent(
    val texture: String = "",
    val model: SkinModel = SkinModel.WIDE,
    val cape: String = "",
)
