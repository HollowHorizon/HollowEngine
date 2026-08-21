package ru.hollowhorizon.hollowengine.common.attachments.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.client.models.internal.v2.ModelAttachment
import ru.hollowhorizon.hollowengine.generated.Assets

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:model")
data class Model(

    val model: String = "hollowengine:models/entity/player_model.gltf"
) {

    val attachment by lazy {
        try {
            ModelAttachment(model)
        } catch (_: Exception) {
            ModelAttachment(Assets.Hollowengine.Models.ERROR.toString())
        }
    }
}
