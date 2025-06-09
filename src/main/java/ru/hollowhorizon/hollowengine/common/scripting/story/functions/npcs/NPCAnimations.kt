package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import ru.hollowhorizon.hc.client.models.internal.controller.BlendMode
import ru.hollowhorizon.hc.client.models.internal.controller.Controller
import ru.hollowhorizon.hc.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.manager
import ru.hollowhorizon.hc.client.models.internal.manager.play
import ru.hollowhorizon.hc.client.models.internal.manager.stop
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

fun NpcEntity.play(
    animation: String,
    layer: BlendMode = BlendMode.Additive,
    mode: WrapMode = WrapMode.Once,
    speed: Float = 1f,
) {
    this[AnimatedEntityCapability::class].play(animation, layer, mode, speed = speed.toString())
}

infix fun NpcEntity.stop(animation: String) {
    this[AnimatedEntityCapability::class].stop(animation)
}

infix fun NpcEntity.playOnce(animation: String) = play(animation, mode = WrapMode.Once)
infix fun NpcEntity.playLooped(animation: String) = play(animation, mode = WrapMode.Loop)
infix fun NpcEntity.playFreeze(animation: String) = play(animation, mode = WrapMode.ClampForever)
infix fun NpcEntity.playPingPong(animation: String) = play(animation, mode = WrapMode.PingPong)

var NpcEntity.controller: Controller
    get() = manager.controller
    set(value) {
        manager.controller = value
    }
