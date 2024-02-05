package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.models.gltf.Transform
import ru.hollowhorizon.hc.client.models.gltf.animations.AnimationType
import ru.hollowhorizon.hc.client.models.gltf.manager.SubModel
import ru.hollowhorizon.hollowengine.common.npcs.Attributes
import java.util.HashMap

class NpcContainer {
    var name = "Неизвестный"
    var model = "hollowengine:models/entity/player_model.gltf"
    val animations = HashMap<AnimationType, String>()
    val textures = HashMap<String, String>()
    var transform = Transform()
    val subModels = HashMap<String, SubModel>()
    var world = "minecraft:overworld"
    var pos = Vec3(0.0, 0.0, 0.0)
    var rotation: Vec2 = Vec2.ZERO
    var attributes = Attributes()
    var size = Pair(0.6f, 1.8f)
    var showName = true
    var switchHeadRot = false

    fun skin(name: String) = "skins/$name"
}