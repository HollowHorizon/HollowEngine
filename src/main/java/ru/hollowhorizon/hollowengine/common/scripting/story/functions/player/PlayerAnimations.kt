package ru.hollowhorizon.hollowengine.common.scripting.story.functions.player

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.models.internal.controller.BlendMode
import ru.hollowhorizon.hc.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.play
import ru.hollowhorizon.hc.client.models.internal.manager.stop
import ru.hollowhorizon.hc.common.utils.get

fun Player.play(
    animation: String,
    layer: BlendMode = BlendMode.Additive,
    mode: WrapMode = WrapMode.Once,
    speed: Float = 1f,
) {
    this[AnimatedEntityCapability::class].play(animation, layer, mode, speed = speed.toString())
}

infix fun Player.stop(animation: String) {
    this[AnimatedEntityCapability::class].stop(animation)
}

infix fun Player.playOnce(animation: String) = play(animation, mode = WrapMode.Once)
infix fun Player.playLooped(animation: String) = play(animation, mode = WrapMode.Loop)
infix fun Player.playFreeze(animation: String) = play(animation, mode = WrapMode.ClampForever)
infix fun Player.playPingPong(animation: String) = play(animation, mode = WrapMode.PingPong)