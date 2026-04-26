package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.binding.EntitySnapshotPacket
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf

@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
@Serializable
class NpcAnimationTransitionPacket(
    val entityId: Int,
    val from: String? = null,
    val to: String? = null,
    val duration: Float = 0.33f,
    val playMode: AnimationPlayMode = AnimationPlayMode.Once,
) : HollowPacket {
    override fun handle(player: Player) {
        if (player !is ServerPlayer) return
        val level = player.level()
        val entity = level.getEntity(entityId) ?: return

        NpcAnimationRuntime.apply(entity, from, to, playMode)
    }
}

object NpcAnimationRuntime {
    fun apply(entity: Entity, from: String?, to: String?, playMode: AnimationPlayMode) {
        val animatorId = ComponentDescriptorRegistry.idFor(AnimatorComponent::class) ?: return
        val components = GearyRuntimeState.componentsById(entity)
        val current = components[animatorId] as? AnimatorComponent ?: AnimatorComponent()
        val withoutOld = from?.let(current::withoutClip) ?: current
        val updated = to
            ?.takeIf(String::isNotBlank)
            ?.let { animation ->
                withoutOld.withLayer(
                    ClipAnimationLayerSpec(
                        id = "npc:$animation",
                        animation = animation,
                        playMode = playMode,
                        removeOnEnd = playMode == AnimationPlayMode.Once,
                    )
                )
            } ?: withoutOld

        components[animatorId] = updated
        val serverEntity = entity.takeIf { !it.level().isClientSide } ?: return
        EntitySnapshotPacket(
            serverEntity.id,
            snapshotOf(serverEntity),
        ).sendTrackingEntityAndSelf(serverEntity)
    }
}
