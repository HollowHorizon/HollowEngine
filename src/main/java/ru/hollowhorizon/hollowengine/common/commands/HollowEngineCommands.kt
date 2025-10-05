package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.StringArgumentType
import de.fabmax.kool.math.Vec3f
import kotlinx.serialization.Serializable
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.api.ParticlesProvider
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.particles.ParticleEffect
import ru.hollowhorizon.hollowengine.client.particles.Transform
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.lifecycle.attach
import ru.hollowhorizon.hollowengine.common.components.lifecycle.detach
import ru.hollowhorizon.hollowengine.common.components.lifecycle.edit
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.coroutines.scopeAsync
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.utils.*
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.LivingEntityQuery
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.script.experimental.api.valueOrThrow

@SubscribeEvent
fun onRegisterCommands(event: RegisterCommandsEvent) {
    event.dispatcher.onRegisterCommands {
        "hollowengine" {
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

            "hand" {
                executes {
                    val player = source.playerOrException
                    val item = player.mainHandItem
                    val location = "\"" + BuiltInRegistries.ITEM.getKey(item.item).toString() + "\""
                    val count = item.count
                    val nbt = if (item.hasTag()) item.getOrCreateTag() else null
                    val itemCommand = when {
                        nbt == null && count > 1 -> "item($location, $count)"
                        nbt == null && count == 1 -> "item($location)"
                        else -> {
                            "item($location, $count, \"${
                                nbt.toString()
                                    .replace("\"", "\\\"")
                            }\")"
                        }
                    }
                    CopyTextPacket(itemCommand).send(player)
                    SUCCESS
                }
            }

            "model"(
                arg("model", StringArgumentType.greedyString(), ::listModels),
            ) {
                executes {
                    val player = source.playerOrException
                    val model = StringArgumentType.getString(this, "model")

                    ShowModelInfoPacket(model).send(player)

                    SUCCESS
                }
            }

            "pos" {
                executes {
                    val player = source.playerOrException
                    val loc = player.pick(100.0, 0.0f, true).location
                    CopyTextPacket("pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})").send(player)
                    SUCCESS
                }
            }

            "components" {
                "add"(
                    arg("entity", EntityArgument.entity()),
                    arg("component", StringArgumentType.string()) {
                        ComponentRegistry.map { it.key.location.toString() }.map { '"' + it + '"' }
                    }
                ) {
                    executes {
                        val entity = EntityArgument.getEntity(this, "entity")
                        val component = StringArgumentType.getString(this, "component").rl

                        (entity as ComponentDispatcher).attach(component)
                        SUCCESS
                    }
                }

                "add"(
                    arg("level", DimensionArgument.dimension()),
                    arg("component", StringArgumentType.string()) {
                        ComponentRegistry.map { it.key.location.toString() }.map { '"' + it + '"' }
                    }
                ) {
                    executes {
                        val level = DimensionArgument.getDimension(this, "level")
                        val component = StringArgumentType.getString(this, "component").rl

                        (level as ComponentDispatcher).attach(component)
                        SUCCESS
                    }
                }

                "remove"(
                    arg("entity", EntityArgument.entity()),
                    arg("component", StringArgumentType.string()) {
                        ComponentRegistry.map { it.key.location.toString() }.map { '"' + it + '"' }
                    }
                ) {
                    executes {
                        val entity = EntityArgument.getEntity(this, "entity")
                        val component = StringArgumentType.getString(this, "component").rl

                        (entity as ComponentDispatcher).detach(component)
                        SUCCESS
                    }
                }

                "remove"(
                    arg("level", DimensionArgument.dimension()),
                    arg("component", StringArgumentType.string()) {
                        ComponentRegistry.map { it.key.location.toString() }.map { '"' + it + '"' }
                    }
                ) {
                    executes {
                        val level = DimensionArgument.getDimension(this, "level")
                        val component = StringArgumentType.getString(this, "component").rl

                        (level as ComponentDispatcher).detach(component)
                        SUCCESS
                    }
                }

                "edit"(
                    arg("entity", EntityArgument.entity()),
                    arg("component", StringArgumentType.string()) {
                        ComponentRegistry.map { it.key.location.toString() }.map { '"' + it + '"' }
                    },
                    arg("property", StringArgumentType.string()),
                    arg("value", StringArgumentType.greedyString())
                ) {
                    executes {
                        val entity = EntityArgument.getEntity(this, "entity")
                        val componentLocation = StringArgumentType.getString(this, "component").rl
                        val propertyName = StringArgumentType.getString(this, "property")
                        val value = StringArgumentType.getString(this, "value").trim()

                        val dispatcher = entity as? ComponentDispatcher ?: error("Entity is not a ComponentDispatcher")

                        dispatcher.edit(componentLocation, propertyName, JsonFormat, JsonFormat.decodeFromString(value))

                        sendSuccess({
                            "[HollowEngine] ".literal.colored(ChatFormatting.GOLD) +
                                    "Property ".literal.colored(0xFFFFFF) +
                                    "$propertyName".literal.colored(ChatFormatting.DARK_PURPLE) +
                                    " of component ".literal.colored(0xFFFFFF) +
                                    "$componentLocation".literal.colored(ChatFormatting.GRAY) +
                                    " set to ".literal.colored(0xFFFFFF) +
                                    value.literal.colored(ChatFormatting.AQUA)
                        })                    }
                }

                "edit"(
                    arg("level", DimensionArgument.dimension()),
                    arg("component", StringArgumentType.string()) {
                        ComponentRegistry.map { it.key.location.toString() }.map { '"' + it + '"' }
                    },
                    arg("property", StringArgumentType.string()),
                    arg("value", StringArgumentType.greedyString())
                ) {
                    executes {
                        val level = DimensionArgument.getDimension(this, "level")
                        val componentLocation = StringArgumentType.getString(this, "component").rl
                        val propertyName = StringArgumentType.getString(this, "property")
                        val value = StringArgumentType.getString(this, "value").trim()

                        val dispatcher = level as? ComponentDispatcher ?: error("Level is not a ComponentDispatcher")

                        dispatcher.edit(componentLocation, propertyName, JsonFormat, JsonFormat.decodeFromString(value))

                        sendSuccess({
                            "[HollowEngine] ".literal.colored(ChatFormatting.GOLD) +
                                    "Property ".literal.colored(0xFFFFFF) +
                                    "$propertyName".literal.colored(ChatFormatting.DARK_PURPLE) +
                                    " of component ".literal.colored(0xFFFFFF) +
                                    "$componentLocation".literal.colored(ChatFormatting.GRAY) +
                                    " set to ".literal.colored(0xFFFFFF) +
                                    value.literal.colored(ChatFormatting.AQUA)
                        })
                    }
                }
            }
        }
    }
}

fun listModels(): Collection<String> {
    val list = mutableListOf<String>()
    list += "hollowengine:models/entity/player_model.gltf"
    list += "hollowengine:models/entity/player_model_slim.gltf"
    list += "hc:models/entity/hilda_regular.glb"

    list += DirectoryManager.HOLLOW_ENGINE.resolve("assets").toFile().walk()
        .filter { it.path.endsWith(".gltf") || it.path.endsWith(".glb") }
        .toList()
        .map {
            it.toReadablePath().substring(7).replace(File.separator, "/").replaceFirst("/", ":")
        }

    return list
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class CopyTextPacket(val text: String) : HollowPacket {
    override fun handle(player: Player) {
        player.sendSystemMessage(
            "hollowengine.commands.copy".mcTranslate(text.literal)
                .onHoverText("hollowengine.tooltips.copy".mcTranslate)
                .onClickCopy(text)
        )
        mc.keyboardHandler.clipboard = text
    }
}

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class ShowModelInfoPacket(val model: String) : HollowPacket {
    override fun handle(player: Player) {
        val location = model.rl

        HollowModelManager.getOrCreate(location).let { model ->
            player.sendSystemMessage(
                "hollowengine.commands.model_animations"
                    .mcTranslate(this.model.substringAfterLast('/'))
            )

            model.animations.keys.forEach { anim ->
                player.sendSystemMessage(
                    ("- ".literal + anim.literal)
                        .onHoverText("hollowengine.tooltips.copy".mcTranslate)
                        .onClickCopy(anim)
                )
            }

            player.sendSystemMessage(
                "hollowengine.commands.model_textures"
                    .mcTranslate(this.model.substringAfterLast('/'))
            )


            model.model.materials.map { it.texture.path.removeSuffix(".png") }.forEach { anim ->
                player.sendSystemMessage(
                    ("- ".literal + anim.literal)
                        .onHoverText("hollowengine.tooltips.copy".mcTranslate)
                        .onClickCopy(anim)
                )
            }
        }
    }

}

fun Double.roundTo(numFractionDigits: Int): Double {
    val factor = 10.0.pow(numFractionDigits.toDouble())
    return (this * factor).roundToInt() / factor
}