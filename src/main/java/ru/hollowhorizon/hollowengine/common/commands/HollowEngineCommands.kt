package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import de.fabmax.kool.math.Vec3f
import kotlinx.serialization.Serializable
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.DimensionArgument
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.api.ParticlesProvider
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.particles.ParticleEffect
import ru.hollowhorizon.hollowengine.client.particles.Transform
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.BlocksSystem
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.*
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.LivingEntityQuery
import java.io.File
import kotlin.math.pow
import kotlin.math.roundToInt

@SubscribeEvent
fun onRegisterCommands(event: RegisterCommandsEvent) {
    event.dispatcher.onRegisterCommands {
        "hollowengine" {
            registerParticleCommands()
            registerModelCommands()
            registerComponentCommands()
            registerUtilityCommands()
            registerGlobalsCommands()
        }
    }
}

typealias CommandExtension = CommandEditor<CommandSourceStack, LiteralArgumentBuilder<CommandSourceStack>>

private fun CommandExtension.registerParticleCommands() {
    "particle"(
        arg("pos", Vec3Argument.vec3()),
        arg("name", StringArgumentType.greedyString()) { BedrockParticles.PARTICLES.keys.map { it.toString() } }
    ) {
        executes {
            spawnParticleAtPosition(
                StringArgumentType.getString(this, "name"),
                Vec3Argument.getVec3(this, "pos")
            )
            SUCCESS
        }
    }

    "particle"(
        arg("entity", EntityArgument.entity()),
        arg("name", StringArgumentType.greedyString()) { BedrockParticles.PARTICLES.keys.map { it.toString() } }
    ) {
        executes {
            spawnParticleOnEntity(
                StringArgumentType.getString(this, "name"),
                EntityArgument.getEntity(this, "entity") as LivingEntity
            )
            SUCCESS
        }
    }

    "remove-particles"(
        arg("name", StringArgumentType.greedyString()) { BedrockParticles.PARTICLES.keys.map { it.toString() } }
    ) {
        executes {
            removeParticles(StringArgumentType.getString(this, "name"))
            SUCCESS
        }
    }
}

private fun CommandExtension.registerModelCommands() {
    "model"(arg("model", StringArgumentType.string()) { getAvailableModels() }) {
        executes {
            val modelName = StringArgumentType.getString(this, "model")
            ShowModelInfoPacket(modelName).send(source.playerOrException)
            SUCCESS
        }
    }
}

private fun CommandExtension.registerComponentCommands() {
    "components" {
        registerComponentAddCommands()
        registerComponentRemoveCommands()
    }
}

private fun CommandExtension.registerComponentAddCommands() {
    "add" {
        registerEntityComponentCommand { entity, component -> entity.container.attach(component) }
        registerLevelComponentCommand { level, component -> level.container.attach(component) }
        registerServerComponentCommand { server, component -> server.container.attach(component) }
    }
}

private fun CommandExtension.registerComponentRemoveCommands() {
    "remove" {
        registerEntityComponentCommand { entity, component -> entity.container.detach(component) }
        registerLevelComponentCommand { level, component -> level.container.detach(component) }
        registerServerComponentCommand { server, component -> server.container.detach(component) }
    }
}

private fun CommandExtension.registerUtilityCommands() {
    "hand" {
        executes {
            copyHandItemToClipboard(source.playerOrException)
            SUCCESS
        }
    }

    "pos" {
        executes {
            copyTargetPositionToClipboard(source.playerOrException)
            SUCCESS
        }
    }
}

private fun CommandExtension.registerGlobalsCommands() {
    "globals" {
        "list" {
            executes {
                val variables =
                    (source.server as ComponentDispatcher).container.get<BlocksSystem>("hollowengine:blocks_system".rl)?.globals
                        ?: return@executes FAILURE
                sendSuccess { "Global variables: ${variables.keys.joinToString("\n") { "- '$it'" }}".literal }
            }
        }

        "get"(arg("name", StringArgumentType.string()) {
            (currentServer as ComponentDispatcher).container.get<BlocksSystem>("hollowengine:blocks_system".rl)
                ?.globals?.keys?.map { "\"$it\"" } ?: emptyList()
        }) {
            executes {
                val variable = StringArgumentType.getString(this, "name")
                val variables =
                    (source.server as ComponentDispatcher).container.get<BlocksSystem>("hollowengine:blocks_system".rl)?.globals
                        ?: return@executes FAILURE

                if(variables.keys.isEmpty()) {
                    sendSuccess { "There are no variables here yet".literal }
                } else {
                    sendSuccess { "'$variable': ${variables[variable].toString()}".literal }
                }
            }
        }

        "remove"(arg("name", StringArgumentType.string()) {
            (currentServer as ComponentDispatcher).container.get<BlocksSystem>("hollowengine:blocks_system".rl)
                ?.globals?.keys?.map { "\"$it\"" } ?: emptyList()
        }) {
            executes {
                val variable = StringArgumentType.getString(this, "name")
                val variables =
                    (source.server as ComponentDispatcher).container.get<BlocksSystem>("hollowengine:blocks_system".rl)?.globals
                        ?: return@executes FAILURE

                variables.remove(variable)

                sendSuccess { "Variable '$variable' removed!".literal }
            }
        }
    }
}

// region Particle Functions
private fun spawnParticleAtPosition(particleName: String, pos: Vec3) {
    val particleFile = BedrockParticles.PARTICLES[particleName.rl] ?: error("Particle not found")
    (Minecraft.getInstance().level as ParticlesProvider).system.spawn(
        ParticleEffect.fromFile(particleFile),
        transform = Transform.create(Vec3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()))
    )
}

private fun spawnParticleOnEntity(particleName: String, entity: LivingEntity) {
    val particleFile = BedrockParticles.PARTICLES[particleName.rl] ?: error("Particle not found")
    (Minecraft.getInstance().level as ParticlesProvider).system.spawn(
        ParticleEffect.fromFile(particleFile),
        query = LivingEntityQuery(entity)
    )
}

private fun removeParticles(particleName: String) {
    val file = BedrockParticles.PARTICLES[particleName.rl] ?: error("Particle not found")
    (Minecraft.getInstance().level as ParticlesProvider).system.remove(
        file.particleEffect.description.identifier
    )
}
// endregion

// region Component Functions
private fun CommandEditor<CommandSourceStack, LiteralArgumentBuilder<CommandSourceStack>>.registerEntityComponentCommand(
    action: (ComponentDispatcher, ResourceLocation) -> Unit,
) {
    "entity"(
        arg("entity", EntityArgument.entity()),
        arg("component", StringArgumentType.string()) { getAvailableComponents() }
    ) {
        executes {
            val entity = EntityArgument.getEntity(this, "entity") as ComponentDispatcher
            val component = StringArgumentType.getString(this, "component").rl
            action(entity, component)
            SUCCESS
        }
    }
}

private fun CommandEditor<CommandSourceStack, LiteralArgumentBuilder<CommandSourceStack>>.registerLevelComponentCommand(
    action: (ComponentDispatcher, ResourceLocation) -> Unit,
) {
    "level"(
        arg("level", DimensionArgument.dimension()),
        arg("component", StringArgumentType.string()) { getAvailableComponents() }
    ) {
        executes {
            val level = DimensionArgument.getDimension(this, "level") as ComponentDispatcher
            val component = StringArgumentType.getString(this, "component").rl
            action(level, component)
            SUCCESS
        }
    }
}

private fun CommandEditor<CommandSourceStack, LiteralArgumentBuilder<CommandSourceStack>>.registerServerComponentCommand(
    action: (ComponentDispatcher, ResourceLocation) -> Unit,
) {
    "server"(arg("component", StringArgumentType.string()) { getAvailableComponents() }) {
        executes {
            val server = source.server as ComponentDispatcher
            val component = StringArgumentType.getString(this, "component").rl
            action(server, component)
            SUCCESS
        }
    }
}

private fun createEditSuccessMessage(componentLocation: ResourceLocation, propertyName: String, value: String) =
    "[HollowEngine] ".literal.colored(ChatFormatting.GOLD) +
            "Property ".literal.colored(0xFFFFFF) +
            propertyName.literal.colored(ChatFormatting.DARK_PURPLE) +
            " of component ".literal.colored(0xFFFFFF) +
            componentLocation.toString().literal.colored(ChatFormatting.GRAY) +
            " set to ".literal.colored(0xFFFFFF) +
            value.literal.colored(ChatFormatting.AQUA)
// endregion

// region Utility Functions
private fun copyHandItemToClipboard(player: Player) {
    val item = player.mainHandItem
    val location = "\"" + BuiltInRegistries.ITEM.getKey(item.item).toString() + "\""
    val count = item.count
    val nbt = item.tag

    val itemCommand = when {
        nbt == null && count > 1 -> "item($location, $count)"
        nbt == null -> "item($location)"
        else -> "item($location, $count, \"${nbt.toString().replace("\"", "\\\"")}\")"
    }

    CopyTextPacket(itemCommand).send(player as ServerPlayer)
}

private fun copyTargetPositionToClipboard(player: Player) {
    val loc = player.pick(100.0, 0.0f, true).location
    val roundedX = loc.x.roundTo(2)
    val roundedY = loc.y.roundTo(2)
    val roundedZ = loc.z.roundTo(2)
    CopyTextPacket("pos($roundedX, $roundedY, $roundedZ)").send(player as ServerPlayer)
}

private fun getAvailableComponents() = ComponentRegistry.map { it.key.toString() }.map { "\"$it\"" }

private fun getAvailableModels(): Collection<String> {
    val defaultModels = listOf(
        "hollowengine:models/entity/player_model.gltf",
        "hollowengine:models/entity/player_model_slim.gltf",
        "hc:models/entity/hilda_regular.glb"
    )

    val customModels = DirectoryManager.HOLLOW_ENGINE.resolve("assets").toFile()
        .walk()
        .filter { it.path.endsWith(".gltf") || it.path.endsWith(".glb") }
        .map { it.toReadablePath().substring(7).replace(File.separator, "/").replaceFirst("/", ":") }
        .toList()

    return defaultModels + customModels
}

private fun Double.roundTo(numFractionDigits: Int): Double {
    val factor = 10.0.pow(numFractionDigits.toDouble())
    return (this * factor).roundToInt() / factor
}
// endregion

// region Packets
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
        val hollowModel = HollowModelManager.getOrCreate(location)

        player.sendSystemMessage(
            "hollowengine.commands.model_animations".mcTranslate(model.substringAfterLast('/'))
        )

        hollowModel.animations.keys.forEach { anim ->
            player.sendSystemMessage(
                ("- ".literal + anim.literal)
                    .onHoverText("hollowengine.tooltips.copy".mcTranslate)
                    .onClickCopy(anim)
            )
        }

        player.sendSystemMessage(
            "hollowengine.commands.model_textures".mcTranslate(model.substringAfterLast('/'))
        )

        hollowModel.model.materials.map { it.texture.path.removeSuffix(".png") }.forEach { texture ->
            player.sendSystemMessage(
                ("- ".literal + texture.literal)
                    .onHoverText("hollowengine.tooltips.copy".mcTranslate)
                    .onClickCopy(texture)
            )
        }
    }
}
// endregion