package ru.hollowhorizon.hollowengine.common.commands

import com.mineinabyss.geary.serialization.setPersisting
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import de.fabmax.kool.math.Vec3f
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.ParticlesProvider
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.particles.ParticleEffect
import ru.hollowhorizon.hollowengine.client.particles.Transform
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.BlocksSystemSavedData
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.clearDevHistory
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentSyncPolicy
import ru.hollowhorizon.hollowengine.common.geary.sync.setSyncing
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.compiling.start
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
            registerUtilityCommands()
            registerCodeBlocksCommands()
            registerScriptCommands()
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

private fun CommandExtension.registerUtilityCommands() {
    "hand" {
        executes {
            copyHandItemToClipboard(source.playerOrException)
            SUCCESS
        }
    }

    "geary" {
        ComponentDescriptorRegistry.forEach { holder ->
            val c = holder.value
            if (!c.editable) return@forEach
            val isSyncing = c.syncPolicy == ComponentSyncPolicy.SYNC
            "${holder.key}" {
                "add"(arg("entity", EntityArgument.entity())) {
                    executes {
                        val entity = EntityArgument.getEntity(this, "entity")
                        if (isSyncing) {
                            entity.entity.setSyncing(c.create(), c.value)
                        } else {
                            entity.entity.setPersisting(c.create(), c.value)
                        }
                        SUCCESS
                    }
                }

                "remove"(arg("entity", EntityArgument.entity())) {
                    executes {
                        val entity = EntityArgument.getEntity(this, "entity")
                        entity.entity.remove(c.value)
                        SUCCESS
                    }
                }
            }
        }
    }

    "pos" {
        executes {
            copyTargetPositionToClipboard(source.playerOrException)
            SUCCESS
        }
    }
}

private fun CommandExtension.registerCodeBlocksCommands() {
    "codeblocks" {
        requires { hasPermission(2) }

        "reload" {
            executes {
                val system = BlocksSystemSavedData.get(source.server)
                system.reloadScripts()
                sendSuccess { "CodeBlocks: reloaded scripts".literal }
            }
        }

        "list" {
            executes {
                val system = BlocksSystemSavedData.get(source.server)
                if (system.scripts.isEmpty()) {
                    sendSuccess { "CodeBlocks: no scripts loaded".literal }
                } else {
                    sendSuccess { "CodeBlocks scripts:".literal }
                    system.scripts.keys.sorted().forEach { p ->
                        source.sendSuccess({ "- $p".literal }, false)
                    }
                }
                SUCCESS
            }
        }

        "start"(arg("path", StringArgumentType.greedyString())) {
            executes {
                val system = BlocksSystemSavedData.get(source.server)
                system.reloadScripts()
                val path = StringArgumentType.getString(this, "path")
                val script = system.scripts[path]
                if (script == null) {
                    sendFailure("CodeBlocks: script not found: $path".literal)
                } else {
                    script.setEnabled(true)
                    sendSuccess { "CodeBlocks: enabled $path".literal }
                }
            }
        }

        "stop"(arg("path", StringArgumentType.greedyString())) {
            executes {
                val system = BlocksSystemSavedData.get(source.server)
                val path = StringArgumentType.getString(this, "path")
                val script = system.scripts[path]
                if (script == null) {
                    sendFailure("CodeBlocks: script not found: $path".literal)
                } else {
                    script.setEnabled(false)
                    sendSuccess { "CodeBlocks: disabled $path".literal }
                }
            }
        }

        "dev" {
            "clear" {
                executes {
                    val system = BlocksSystemSavedData.get(source.server)
                    system.clearDevHistory()
                    sendSuccess { "CodeBlocks dev history cleared".literal }
                }
            }
        }
    }
}

private fun CommandExtension.registerScriptCommands() {
    "script" {
        requires { hasPermission(2) }

        "run"(arg("path", StringArgumentType.greedyString()) { getAvailableScripts() }) {
            executes {
                if (!HollowEngine.compilerLoader.isLoaded) {
                    return@executes sendFailure("Scripting environment is not loaded. Please check if HollowEngineCompiler.jar exists.".literal)
                }

                val path = StringArgumentType.getString(this, "path")
                val file = path.fromReadablePath()

                if (!file.exists()) {
                    return@executes sendFailure("Script file not found: $path".literal)
                }

                val result = ScriptingEnvironment.INSTANCE.compiler.compile(file)
                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    sendFailure("Script compilation failed: ${exception?.message ?: "Unknown error"}".literal)
                    HollowEngine.LOGGER.error("Script compilation failed", exception)
                } else {
                    val script = result.getOrThrow()
                    script.start()
                    sendSuccess { "Script started: $path".literal }
                }
                SUCCESS
            }
        }

        "list" {
            executes {
                if (!HollowEngine.compilerLoader.isLoaded) {
                    return@executes sendFailure("Scripting environment is not loaded.".literal)
                }

                val scripts = getAvailableScripts()
                if (scripts.isEmpty()) {
                    sendSuccess { "No .kts scripts found in hollowengine/scripts/".literal }
                } else {
                    sendSuccess { "Available Kotlin scripts:".literal }
                    scripts.sorted().forEach { scriptPath ->
                        source.sendSuccess({ "- $scriptPath".literal }, false)
                    }
                }
                SUCCESS
            }
        }

        "eval"(arg("code", StringArgumentType.greedyString())) {
            executes {
                if (!HollowEngine.compilerLoader.isLoaded) {
                    return@executes sendFailure("Scripting environment is not loaded.".literal)
                }

                val code = StringArgumentType.getString(this, "code")
                val result = ScriptingEnvironment.INSTANCE.compiler.compile("eval.kts", code)

                if (result.isFailure) {
                    val exception = result.exceptionOrNull()
                    sendFailure("Script evaluation failed: ${exception?.message ?: "Unknown error"}".literal)
                    HollowEngine.LOGGER.error("Script evaluation failed", exception)
                } else {
                    val script = result.getOrThrow()
                    val result = script.execute<Any>()
                    if(result.isSuccess) {
                        sendSuccess { "Script evaluated successfully: ${result.getOrThrow()}".literal }
                    } else {
                        sendFailure("Script evaluation failed: ${result.exceptionOrNull()}".literal)
                    }
                }
                SUCCESS
            }
        }
    }
}

private fun getAvailableScripts(): Collection<String> {
    val scriptsDir = DirectoryManager.HOLLOW_ENGINE.resolve("scripts").toFile()
    if (!scriptsDir.exists()) {
        scriptsDir.mkdirs()
        return emptyList()
    }

    return scriptsDir.walk()
        .filter { it.isFile && it.name.endsWith(".kts") }
        .map { it.toReadablePath() }
        .toList()
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

// region Utility Functions
private fun copyHandItemToClipboard(player: Player) {
    val item = player.mainHandItem
    val location = "\"" + BuiltInRegistries.ITEM.getKey(item.item).toString() + "\""
    val count = item.count
    //? if > 1.20.1 {
    /*val nbt = item.components
    *///?} else {
    val nbt = item.tag
    //?}

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
        Minecraft.getInstance().coroutineScope.launch {
            val hollowModel = HollowModelManager.getOrCreate(location)
                .filter { it !== AnimatedModel.EMPTY }
                .first()

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
}
// endregion






