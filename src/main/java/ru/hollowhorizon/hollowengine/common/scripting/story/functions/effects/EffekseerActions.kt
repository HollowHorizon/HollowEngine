package ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects

import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.effects.ParticleEmitterInfo
import ru.hollowhorizon.hc.common.effects.ParticleHelper

fun Level.effekseer(location: String, config: ParticleEmitterInfo.() -> Unit) {
    ParticleHelper.addParticle(this, ParticleEmitterInfo(location.rl).apply(config), true)
}
