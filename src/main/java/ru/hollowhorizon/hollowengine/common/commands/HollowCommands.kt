package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.StringArgumentType
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.Minecraft
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.api.ParticlesProvider
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.particles.ParticleEffect
import ru.hollowhorizon.hollowengine.client.particles.Transform
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.utils.literal
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.LivingEntityQuery
import ru.hollowhorizon.hollowengine.common.utils.rl

object HollowCommands {

    @SubscribeEvent
    fun onRegisterCommands(event: RegisterCommandsEvent) {
        event.dispatcher.onRegisterCommands {
            "hollowcore"("hc") {

                "particle"(
                    arg("pos", Vec3Argument.vec3()),
                    arg(
                        "name",
                        StringArgumentType.greedyString()
                    ) { BedrockParticles.PARTICLES.keys.map { it.toString() } },
                ) {
                    executes {
                        val particle = StringArgumentType.getString(this, "name")
                        val pos = Vec3Argument.getVec3(this, "pos")

                        (Minecraft.getInstance().level as ParticlesProvider).system.spawn(
                            ParticleEffect.fromFile(
                                BedrockParticles.PARTICLES[particle.rl] ?: error("Particle not found")
                            ),
                            transform = Transform.create(Vec3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())),
                        )

                        SUCCESS
                    }
                }

                "particle"(
                    arg("entity", EntityArgument.entity()),
                    arg(
                        "name",
                        StringArgumentType.greedyString()
                    ) { BedrockParticles.PARTICLES.keys.map { it.toString() } },
                ) {
                    executes {
                        val entity = EntityArgument.getEntity(this, "entity")
                        val particle = StringArgumentType.getString(this, "name")

                        (Minecraft.getInstance().level as ParticlesProvider).system.spawn(
                            ParticleEffect.fromFile(
                                BedrockParticles.PARTICLES[particle.rl] ?: error("Particle not found")
                            ),
                            query = LivingEntityQuery(entity as LivingEntity),
                        )

                        SUCCESS
                    }
                }

                "remove-particles"(
                    arg(
                        "name",
                        StringArgumentType.greedyString()
                    ) { BedrockParticles.PARTICLES.keys.map { it.toString() } },
                ) {
                    executes {
                        val particle = StringArgumentType.getString(this, "name")
                        val file = BedrockParticles.PARTICLES[particle.rl] ?: error("Particle not found")

                        (Minecraft.getInstance().level as ParticlesProvider).system.remove(
                            file.particleEffect.description.identifier
                        )

                        SUCCESS
                    }
                }

                "model"(
                    arg("model", StringArgumentType.string()) {
                        (HollowModelManager.allModels.map { it.toString() }).map { '"' + it + '"' }
                    }
                ) {
                    executes {
                        val model = HollowModelManager.getOrCreate(StringArgumentType.getString(this, "model").rl)

                        source.player?.let { player ->
                            player.sendSystemMessage("Animations:".literal)
                            model.animations.keys.forEach {
                                player.sendSystemMessage(it.literal)
                            }
                            player.sendSystemMessage("Textures:".literal)

                            model.model.materials.map { it.texture }
                                .forEach {
                                    player.sendSystemMessage(it.toString().literal)
                                }
                        }

                        SUCCESS
                    }
                }
            }
        }
    }
}
