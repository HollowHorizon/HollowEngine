/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.StringArgumentType
import de.fabmax.kool.math.Vec3f
import net.minecraft.client.Minecraft
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.api.ParticlesProvider
import ru.hollowhorizon.hollowengine.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.particles.ParticleEffect
import ru.hollowhorizon.hollowengine.client.particles.Transform
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.utils.get
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

                "player-model"(
                    arg("model", StringArgumentType.string()) {
                        (HollowModelManager.allModels.map { it.toString() } + "%NO_MODEL%").map { '"' + it + '"' }
                    }
                ) {
                    executes {
                        source.player?.let {
                            it[AnimatedEntityCapability::class].model = StringArgumentType.getString(this, "model")
                        }

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
