package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.gltf.manager.RawPose
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.nbt.loadAsNBT
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.next
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.AnimationContainer

infix fun NPCProperty.setPose(fileName: () -> String?) {
    builder.apply {
        next {
            val file = fileName()
            if (file == null) {
                this@setPose()[AnimatedEntityCapability::class].pose = RawPose()
                return@next
            }
            val replay = RawPose.fromNBT(
                DirectoryManager.HOLLOW_ENGINE.resolve("npcs/poses/").resolve(file).inputStream().loadAsNBT()
            )
            this@setPose()[AnimatedEntityCapability::class].pose = replay
        }
    }
}

infix fun NPCProperty.play(block: AnimationContainer.() -> Unit) {
    builder.apply {
        next {
            val container = AnimationContainer().apply(block)

            val serverLayers = this@play()[AnimatedEntityCapability::class].layers

            if (serverLayers.any { it.animation == container.animation }) return@next

            StartAnimationPacket(
                this@play().id, container.animation, container.layerMode, container.playType, container.speed
            ).send(PacketDistributor.TRACKING_ENTITY.with(this@play))

            if (container.playType != PlayMode.ONCE) {
                //Нужно на случай если клиентская сущность выйдет из зоны видимости (удалится)
                serverLayers.addNoUpdate(
                    AnimationLayer(
                        container.animation, container.layerMode, container.playType, container.speed
                    )
                )
            }
        }
    }
}

infix fun NPCProperty.playLooped(animation: () -> String) = play {
    this.playType = PlayMode.LOOPED
    this.animation = animation()
}

infix fun NPCProperty.playOnce(animation: () -> String) = play {
    this.playType = PlayMode.ONCE
    this.animation = animation()
}

infix fun NPCProperty.playFreeze(animation: () -> String) = play {
    this.playType = PlayMode.LAST_FRAME
    this.animation = animation()
}

infix fun NPCProperty.stop(animation: () -> String) {
    builder.apply {
        next {
            val anim = animation()
            this@stop()[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == anim }
            StopAnimationPacket(this@stop().id, anim).send(PacketDistributor.TRACKING_ENTITY.with(this@stop))
        }
    }
}