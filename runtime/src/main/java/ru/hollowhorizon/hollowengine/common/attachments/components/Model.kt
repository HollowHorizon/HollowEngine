package ru.hollowhorizon.hollowengine.common.attachments.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.common.attachments.editor.EditorAsset
import ru.hollowhorizon.hollowengine.common.attachments.editor.EditorIcon

/**
 * Which model an entity or node shows.
 */
@Registerable
@Syncable
@Serializable
@EditorIcon("hollowengine:textures/gui/icons/file_model.svg")
@SerialName("hollowengine:model")
data class Model(
    @EditorAsset(
        "gltf", "glb", "geo.json", "fbx", "obj", "bbmodel"
    ) val model: String = "hollowengine:models/entity/player_model.gltf",
)
