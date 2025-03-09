package ru.hollowhorizon.hollowengine.common.scripting.story.functions.player

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.models.internal.animations.PlayMode
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.internal.manager.LayerMode
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.network.packets.StartAnimationPacket
import ru.hollowhorizon.hc.common.network.packets.StopAnimationPacket
import ru.hollowhorizon.hc.common.network.sendTrackingEntity

fun Player.play(
    animation: String,
    layer: LayerMode = LayerMode.ADD,
    mode: PlayMode = PlayMode.ONCE,
    speed: Float = 1f
) {
    val serverLayers = this[AnimatedEntityCapability::class].layers

    if (serverLayers.any { it.animation == animation }) return // Анимация уже запущена

    StartAnimationPacket(id, animation, layer, mode, speed)
        .sendTrackingEntity(this)

    if (mode != PlayMode.ONCE) {
        serverLayers.addNoUpdate(AnimationLayer(animation, layer, mode, speed))
    }
}
infix fun Player.stop(animation: String) {
    this[AnimatedEntityCapability::class].layers.removeIfNoUpdate { it.animation == animation }
    StopAnimationPacket(id, animation).sendTrackingEntity(this)
}

infix fun Player.playOnce(animation: String) = play(animation, mode = PlayMode.ONCE)
infix fun Player.playLooped(animation: String) = play(animation, mode = PlayMode.LOOPED)
infix fun Player.playFreeze(animation: String) = play(animation, mode = PlayMode.LAST_FRAME)
infix fun Player.playReversed(animation: String) = play(animation, mode = PlayMode.REVERSED)