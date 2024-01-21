package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hc.client.models.gltf.Transform
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.SubModel
import ru.hollowhorizon.hc.client.utils.get

fun subModel(body: SubModel.() -> Unit): SubModel {
    return SubModel(
        "hollowengine:models/entity/player_model.gltf",
        ArrayList(), HashMap(), Transform(), HashMap()
    ).apply(body)
}

val NPCProperty.asSubModel: SubModel
    get() {
        val npc = this()
        val original = npc[AnimatedEntityCapability::class]
        return subModel {
            model = original.model
            layers.addAll(original.layers)
            textures.putAll(original.textures)
            transform = original.transform
            subModels.putAll(original.subModels)
        }
    }

val NPCProperty.subModels get() = this()[AnimatedEntityCapability::class].subModels