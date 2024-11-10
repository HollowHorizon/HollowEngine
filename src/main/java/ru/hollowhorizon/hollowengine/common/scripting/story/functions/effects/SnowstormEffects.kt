package ru.hollowhorizon.hollowengine.common.scripting.story.functions.effects

import dev.folomeev.kotgl.matrix.vectors.vec3
import kotlinx.serialization.Serializable
import net.minecraft.commands.arguments.EntityArgument.players
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.api.ParticlesProvider
import ru.hollowhorizon.hc.client.molang.asMolang
import ru.hollowhorizon.hc.client.particles.BedrockParticles
import ru.hollowhorizon.hc.client.particles.ParticleEffect
import ru.hollowhorizon.hc.client.particles.Transform
import ru.hollowhorizon.hc.client.utils.nbt.ForEntity
import ru.hollowhorizon.hc.client.utils.nbt.ForVec3
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3

fun Level.bedrockParticles(pos: Vec3, location: String) {
    SpawnParticlesPacket(location, pos).send(*players().map { it as ServerPlayer }.toTypedArray())
}

fun LivingEntity.bedrockParticles(location: String) {
    SpawnParticlesPacket(location, entity = this).send(*level().players().map { it as ServerPlayer }.toTypedArray())
}

@Serializable
@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
class SpawnParticlesPacket(
    val location: String,
    val pos: @Serializable(ForVec3::class) Vec3? = null,
    val entity: @Serializable(ForEntity::class) Entity? = null,
) : HollowPacketV3<SpawnParticlesPacket> {
    override fun handle(player: Player) {
        val renderer = player.level() as ParticlesProvider

        if(pos != null) {
            renderer.system.spawn(
                ParticleEffect.fromFile(
                    BedrockParticles.PARTICLES[location.rl] ?: error("Particle $location not found!")
                ),
                transform = Transform.create(vec3(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()))
            )
        }
        if(entity is LivingEntity) {
            renderer.system.spawn(
                ParticleEffect.fromFile(
                    BedrockParticles.PARTICLES[location.rl] ?: error("Particle $location not found!")
                ),
                entity = entity.asMolang()
            )
        }
    }

}