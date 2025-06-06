package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.models.internal.Transform
import ru.hollowhorizon.hc.client.models.internal.animations.AnimationType
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.utils.currentServer
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import kotlin.contracts.ExperimentalContracts

@OptIn(ExperimentalContracts::class)
fun npc(
    pos: Vec3,
    name: String = "NPC",
    model: String = "hollowengine:models/entity/player_model.gltf",
    rotation: Vec2 = rotation(0, 0),
    world: String = "minecraft:overworld",
    size: Pair<Float, Float> = 0.6f to 1.8f,
    attributes: Map<String, Float> = emptyMap(),
    textures: Map<String, String> = emptyMap(),
    animations: Map<AnimationType, String> = emptyMap(),
    transform: Transform = Transform(),
    showName: Boolean = true,
    inverseHeadRotation: Boolean = false,
): NpcEntity {
    assert(ResourceLocation.isValidResourceLocation(model)) {
        "Non [a-z0-9/._-] character in path of location: $model"
    }

    val level = currentServer.getLevel(currentServer.levelKeys().find { it.location().toString() == world }
        ?: throw IllegalStateException("Dimension $world not found!"))
        ?: throw IllegalStateException("Dimension $world is not loaded!")

    level.allEntities.asSequence()
        .filterIsInstance<NpcEntity>()
        .filter { it.name == name }
        .firstOrNull { it.model == model }
        ?.let { return it }

    return NpcEntity(level).apply {
        setPos(pos.x, pos.y, pos.z)

        this[AnimatedEntityCapability::class].apply {
            this.model = model
            this.textures.clear()
            this.textures.putAll(textures)
            this.transform = transform
        }
        moveTo(pos.x, pos.y, pos.z, rotation.x, rotation.y)

        if (attributes.isNotEmpty()) {
            setAttributes(attributes)
        }

        setDimensions(size)
        refreshDimensions()

        isCustomNameVisible = showName && name.isNotEmpty()
        customName = name.literal

        level.addFreshEntity(this)
    }
}

fun NpcEntity.despawn() {
    this.remove(Entity.RemovalReason.DISCARDED)
}

fun NpcEntity.updateAttributes(attributes: Map<String, Float>) {
    setAttributes(attributes)
}

fun pos(x: Int, y: Int, z: Int) = Vec3(x + 0.5, y.toDouble(), z + 0.5)
fun pos(x: Double, y: Double, z: Double) = Vec3(x, y, z)
fun rotation(x: Int, y: Int) = Vec2(x.toFloat(), y.toFloat())
fun rotation(x: Float, y: Float) = Vec2(x, y)