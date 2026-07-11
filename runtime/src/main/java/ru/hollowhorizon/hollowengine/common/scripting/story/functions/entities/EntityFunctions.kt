package ru.hollowhorizon.hollowengine.common.scripting.story.functions.entities

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.geary.components.AnimationPlayMode
import ru.hollowhorizon.hollowengine.common.npcs.NpcAnimationRuntime

fun Entity.play(
    animation: String,
    playMode: AnimationPlayMode = AnimationPlayMode.Once,
    fadeIn: Double = 0.33,
    fadeOut: Double = 0.33,
) {
    NpcAnimationRuntime.apply(
        entity = this,
        from = null,
        to = animation,
        playMode = playMode,
        duration = 0f,
        fadeIn = fadeIn.toFloat(),
        fadeOut = fadeOut.toFloat(),
    )
}

infix fun Entity.playOnce(animation: String) = play(animation)
infix fun Entity.playLooped(animation: String) = play(animation, playMode = AnimationPlayMode.Loop)
infix fun Entity.playClamped(animation: String) = play(animation, playMode = AnimationPlayMode.ClampForever)
infix fun Entity.playPingPong(animation: String) = play(animation, playMode = AnimationPlayMode.PingPong)

fun Entity.stopAnimation(animation: String, fadeOut: Double = 0.33) {
    NpcAnimationRuntime.apply(
        entity = this,
        from = animation,
        to = null,
        playMode = AnimationPlayMode.Once,
        duration = fadeOut.toFloat(),
    )
}