package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.particles

import net.minecraft.server.MinecraftServer
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.render.particles.DiscardType
import ru.hollowhorizon.hc.client.render.particles.HollowParticleBuilder
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import kotlin.math.cos
import kotlin.math.sin


class ParticleContainer(val server: MinecraftServer) {
    private lateinit var settings: HollowParticleBuilder
    var world = "minecraft:overworld"
    var particle = "hc:circle"

    fun settings(builder: HollowParticleBuilder.() -> Unit) {
        val dimension = server.levelKeys().find { it.location() == world.rl }
            ?: throw IllegalStateException("Dimension $world not found. Or not loaded!")

        settings = HollowParticleBuilder.create(server.getLevel(dimension)!!, particle, builder)
    }
}

fun IContextBuilder.script() {
    particles {
        settings {
            transparency(0f, 1f, 0f)
            discardType = DiscardType.ENDING_CURVE_INVISIBLE
        }
    }
}

fun IContextBuilder.particles(builder: ParticleContainer.() -> Unit) = +SimpleNode {
    ParticleContainer(this@particles.stateMachine.server).apply(builder)
}