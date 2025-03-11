package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.models.internal.Transform
import ru.hollowhorizon.hc.client.models.internal.animations.AnimationType
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

fun npc(
    pos: Vec3,
    name: String = "NPC",
    model: String = "hollowengine:models/entity/player_model.gltf",
    rotation: Vec2 = rotation(0, 0),
    world: String = "minecraft:overworld",
    size: Pair<Float, Float> = 0.8f to 1.6f,
    attributes: Map<String, Float> = emptyMap(),
    textures: Map<String, String> = emptyMap(),
    animations: Map<AnimationType, String> = emptyMap(),
    transform: Transform = Transform(),
    showName: Boolean = true,
    inverseHeadRotation: Boolean = false,
): NPCEntity {
    val level = currentServer.getLevel(currentServer.levelKeys().find { it.location().toString() == world }
        ?: throw IllegalStateException("Dimension $world not found!")) ?: throw IllegalStateException("Dimension $world is not loaded!")

    return NPCEntity(level).apply {
        setPos(pos.x, pos.y, pos.z)

        this[AnimatedEntityCapability::class].apply {
            this.model = model
            this.animations.clear()
            this.animations.putAll(animations)
            this.textures.clear()
            this.textures.putAll(textures)
            this.transform = transform
            switchHeadRot = inverseHeadRotation
        }
        moveTo(pos.x, pos.y, pos.z, rotation.x, rotation.y)

        //TODO: Make attributes

        setDimensions(size)
        refreshDimensions()

        isCustomNameVisible = showName && name.isNotEmpty()
        customName = name.literal

        level.addFreshEntity(this)
    }
}

fun NPCEntity.despawn() {
    this.remove(Entity.RemovalReason.DISCARDED)
}

fun pos(x: Int, y: Int, z: Int) = Vec3(x + 0.5, y.toDouble(), z + 0.5)
fun pos(x: Double, y: Double, z: Double) = Vec3(x, y, z)
fun rotation(x: Int, y: Int) = Vec2(x.toFloat(), y.toFloat())
fun rotation(x: Float, y: Float) = Vec2(x, y)