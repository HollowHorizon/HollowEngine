package ru.hollowhorizon.hollowengine.common.npcs

import ru.hollowhorizon.hc.client.models.gltf.Transform
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayType
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.common.capabilities.CapabilityStorage
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

interface IHollowNPC : ICharacter {
    val npcEntity: NPCEntity
    override val entityType: NPCEntity
        get() = npcEntity
    override val characterName: String
        get() = npcEntity.displayName.string


    infix fun play(animation: String) {
        npcEntity[AnimatedEntityCapability::class].layers.add(AnimationLayer(
            animation, 1.0f, PlayType.LOOPED, 1.0f, 0
        ))
    }

    infix fun playOnce(animation: String) {
        npcEntity[AnimatedEntityCapability::class].layers.add(AnimationLayer(
            animation, 1.0f, PlayType.ONCE, 1.0f, 0
        ))
    }

    fun setTransform(transform: Transform) {
        npcEntity.getCapability(CapabilityStorage.getCapability(AnimatedEntityCapability::class.java))
            .orElseThrow { IllegalStateException("AnimatedEntityCapability not found!") }
            .transform = transform
    }

    fun getTransform() = npcEntity.getCapability(CapabilityStorage.getCapability(AnimatedEntityCapability::class.java))
        .orElseThrow { IllegalStateException("AnimatedEntityCapability not found!") }
        .transform

    fun play(name: String, priority: Float = 1.0f, playType: PlayType = PlayType.ONCE, speed: Float = 1.0f) {
        npcEntity[AnimatedEntityCapability::class].layers.add(AnimationLayer(
            name, priority, playType, speed, 0
        ))
    }

    infix fun stop(animation: String) {
        npcEntity[AnimatedEntityCapability::class].layers.removeIf { it.animation == animation }
    }

    fun waitInteract(icon: IconType) {
        synchronized(npcEntity.interactionWaiter) { npcEntity.interactionWaiter.wait() }
    }
}

enum class IconType {
    NONE, DIALOGUE, WARNING, QUESTION
}
