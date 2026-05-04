package ru.hollowhorizon.hollowengine.common.events.client.render

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.entity.HumanoidArm
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class RenderArmEvent(
    val poseStack: PoseStack,
    val multiBufferSource: MultiBufferSource,
    val packedLight: Int,
    val player: AbstractClientPlayer,
    val arm: HumanoidArm,
) : ClientEvent, Cancellable {
    override var isCanceled = false

    companion object : EventHandler<RenderArmEvent>()
}

