package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.QuatF
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
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.system
import ru.hollowhorizon.hollowengine.client.models.internal.AnimatedModel
import ru.hollowhorizon.hollowengine.client.models.internal.manager.HollowModelManager
import ru.hollowhorizon.hollowengine.client.particles.BedrockParticles
import ru.hollowhorizon.hollowengine.client.particles.ParticleEffect
import ru.hollowhorizon.hollowengine.client.particles.Transform
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.BlocksSystemSavedData
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.VariableMap
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.clearDevHistory
import ru.hollowhorizon.hollowengine.common.coroutines.OwnerScope
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.geary.anchor.*
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.sync.setSyncing
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.compiling.start
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariRunStatus
import ru.hollowhorizon.hollowengine.common.scripting.katari.getAvailableKatariScripts
import ru.hollowhorizon.hollowengine.common.utils.*
import ru.hollowhorizon.hollowengine.common.utils.molang.runtime.LivingEntityQuery
import java.io.File
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt

@SubscribeEvent
fun onRegisterCommands(event: RegisterCommandsEvent) {
    event.dispatcher.onRegisterCommands {
        "hollowengine"("he") {
            registerParticleCommands()
            registerModelCommands()
            registerLightCommands()
            registerUtilityCommands()
            registerCodeBlocksCommands()
            registerScriptCommands()
            registerKatariCommands()
        }
    }
}

@SubscribeEvent
fun onKatariChat(event: ServerChatEvent) {
    val server = event.player.server ?: return
    if (server.runtimeContext.katari.submitChat(event.player, event.message.string)) {
        event.isCanceled = true
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

    "model" {
        "info"(arg("model", StringArgumentType.string()) { getAvailableModels() }) {
            executes {
                val modelName = StringArgumentType.getString(this, "model")
                ShowModelInfoPacket(modelName).send(source.playerOrException)
                SUCCESS
            }
        }

        "attach"(
            arg("entity", EntityArgument.entity()),
            arg("model", StringArgumentType.string()) { getAvailableModels() }
        ) {
            executes {
                val host = EntityArgument.getEntity(this, "entity")
                val modelName = StringArgumentType.getString(this, "model")
                val stableKey = attachAnchoredModel(host, modelName)
                sendSuccess { "Attached anchored model with StableKey $stableKey".literal }
            }
        }

        "spawn"(
            arg("pos", Vec3Argument.vec3()),
            arg("model", StringArgumentType.string()) { getAvailableModels() }
        ) {
            executes {
                val position = Vec3Argument.getVec3(this, "pos")
                val modelName = StringArgumentType.getString(this, "model")
                val stableKey = spawnAnchoredModel(source, position, modelName)
                sendSuccess { "Spawned anchored model with StableKey $stableKey".literal }
            }
        }

        "move"(
            arg("stableKey", StringArgumentType.string()),
            arg("pos", Vec3Argument.vec3())
        ) {
            executes {
                val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
                val position = Vec3Argument.getVec3(this, "pos")
                moveAnchoredModel(source, stableKey, position)
            }
        }

        "remove"(arg("stableKey", StringArgumentType.string())) {
            executes {
                val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
                removeAnchoredModel(source, stableKey)
            }
        }
    }
}

private fun CommandExtension.registerLightCommands() {
    "light" {
        "list" {
            executes { listLights(source) }
        }

        "add" {
            "point"(arg("radius", FloatArgumentType.floatArg(0f))) {
                executes {
                    val radius = FloatArgumentType.getFloat(this, "radius")
                    val stableKey = spawnPointLight(source, radius)
                    sendSuccess { "Spawned point light with StableKey $stableKey".literal }
                }
            }

            "spot"(
                arg("distance", FloatArgumentType.floatArg(0f)),
                arg("innerAngle", FloatArgumentType.floatArg(0f, 90f)),
                arg("outerAngle", FloatArgumentType.floatArg(0f, 90f))
            ) {
                executes {
                    val distance = FloatArgumentType.getFloat(this, "distance")
                    val innerAngle = FloatArgumentType.getFloat(this, "innerAngle")
                    val outerAngle = FloatArgumentType.getFloat(this, "outerAngle")
                    if (outerAngle < innerAngle) {
                        return@executes sendFailure("Outer angle must be greater than or equal to inner angle".literal)
                    }

                    val stableKey = spawnSpotLight(source, distance, innerAngle, outerAngle)
                    sendSuccess { "Spawned spot light with StableKey $stableKey".literal }
                }
            }
        }

        "remove" {
            executes { removeLight(source, null) }
        }
        "remove"(arg("stableKey", StringArgumentType.string())) {
            executes { removeLight(source, UUID.fromString(StringArgumentType.getString(this, "stableKey"))) }
        }

        "enable"(arg("enabled", BoolArgumentType.bool())) {
            executes {
                val enabled = BoolArgumentType.getBool(this, "enabled")
                updateLight(source, null) { withEnabled(enabled) }
            }
        }
        "enable"(arg("stableKey", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
            executes {
                val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
                val enabled = BoolArgumentType.getBool(this, "enabled")
                updateLight(
                    source,
                    stableKey
                ) { withEnabled(enabled) }
            }
        }

        "color"(
            arg("r", FloatArgumentType.floatArg(0f, 1f)),
            arg("g", FloatArgumentType.floatArg(0f, 1f)),
            arg("b", FloatArgumentType.floatArg(0f, 1f))
        ) {
            executes {
                val color = LightColor(
                    FloatArgumentType.getFloat(this, "r"),
                    FloatArgumentType.getFloat(this, "g"),
                    FloatArgumentType.getFloat(this, "b"),
                )
                updateLight(source, null) { withColor(color) }
            }
        }
        "color"(
            arg("stableKey", StringArgumentType.string()),
            arg("r", FloatArgumentType.floatArg(0f, 1f)),
            arg("g", FloatArgumentType.floatArg(0f, 1f)),
            arg("b", FloatArgumentType.floatArg(0f, 1f))
        ) {
            executes {
                val color = LightColor(
                    FloatArgumentType.getFloat(this, "r"),
                    FloatArgumentType.getFloat(this, "g"),
                    FloatArgumentType.getFloat(this, "b"),
                )
                updateLight(
                    source,
                    UUID.fromString(StringArgumentType.getString(this, "stableKey"))
                ) { withColor(color) }
            }
        }

        "intensity"(arg("value", FloatArgumentType.floatArg(0f))) {
            executes {
                val intensity = FloatArgumentType.getFloat(this, "value")
                updateLight(source, null) { withIntensity(intensity) }
            }
        }
        "intensity"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f))) {
            executes {
                val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
                val intensity = FloatArgumentType.getFloat(this, "value")
                updateLight(
                    source,
                    stableKey
                ) { withIntensity(intensity) }
            }
        }

        "shadow" {
            registerShadowLightCommands()
        }

        "fog" {
            registerFogLightCommands()
        }

        "flare" {
            registerFlareLightCommands()
        }
    }
}

private fun CommandExtension.registerShadowLightCommands() {
    "enable"(arg("enabled", BoolArgumentType.bool())) {
        executes {
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, null) { withShadowSettings { copy(enabled = enabled) } }
        }
    }
    "enable"(arg("stableKey", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, stableKey) {
                withShadowSettings { copy(enabled = enabled) }
            }
        }
    }
    "dynamic"(arg("value", BoolArgumentType.bool())) {
        executes {
            val dynamic = BoolArgumentType.getBool(this, "value")
            updateLight(source, null) { withShadowSettings { copy(dynamic = dynamic) } }
        }
    }
    "dynamic"(arg("stableKey", StringArgumentType.string()), arg("value", BoolArgumentType.bool())) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val dynamic = BoolArgumentType.getBool(this, "value")
            updateLight(source, stableKey) {
                withShadowSettings { copy(dynamic = dynamic) }
            }
        }
    }
    "distance"(arg("value", FloatArgumentType.floatArg(0f))) {
        executes {
            val distance = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withShadowSettings { copy(shadowDistance = distance) } }
        }
    }
    "distance"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val distance = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withShadowSettings { copy(shadowDistance = distance) }
            }
        }
    }
    "fovOffset"(arg("value", FloatArgumentType.floatArg(-180f, 180f))) {
        executes {
            val fovOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withShadowSettings { copy(fovOffset = fovOffset) } }
        }
    }
    "fovOffset"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(-180f, 180f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val fovOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withShadowSettings { copy(fovOffset = fovOffset) }
            }
        }
    }
}

private fun CommandExtension.registerFogLightCommands() {
    "enable"(arg("enabled", BoolArgumentType.bool())) {
        executes {
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, null) { withVolumetricFogSettings { copy(enabled = enabled) } }
        }
    }
    "enable"(arg("stableKey", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, stableKey) {
                withVolumetricFogSettings { copy(enabled = enabled) }
            }
        }
    }
    "samples"(arg("value", IntegerArgumentType.integer(1, 128))) {
        executes {
            val samples = IntegerArgumentType.getInteger(this, "value")
            updateLight(source, null) { withVolumetricFogSettings { copy(sampleCount = samples) } }
        }
    }
    "samples"(arg("stableKey", StringArgumentType.string()), arg("value", IntegerArgumentType.integer(1, 128))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val samples = IntegerArgumentType.getInteger(this, "value")
            updateLight(source, stableKey) {
                withVolumetricFogSettings { copy(sampleCount = samples) }
            }
        }
    }
    "scattering"(arg("value", FloatArgumentType.floatArg(0f, 1f))) {
        executes {
            val scattering = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withVolumetricFogSettings { copy(scattering = scattering) } }
        }
    }
    "scattering"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 1f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val scattering = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withVolumetricFogSettings { copy(scattering = scattering) }
            }
        }
    }
    "density"(arg("value", FloatArgumentType.floatArg(0f, 5f))) {
        executes {
            val density = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withVolumetricFogSettings { copy(density = density) } }
        }
    }
    "density"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 5f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val density = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withVolumetricFogSettings { copy(density = density) }
            }
        }
    }
    "anisotropy"(arg("value", FloatArgumentType.floatArg(-1f, 1f))) {
        executes {
            val anisotropy = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withVolumetricFogSettings { copy(anisotropy = anisotropy) } }
        }
    }
    "anisotropy"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(-1f, 1f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val anisotropy = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withVolumetricFogSettings { copy(anisotropy = anisotropy) }
            }
        }
    }
}

private fun CommandExtension.registerFlareLightCommands() {
    "enable"(arg("enabled", BoolArgumentType.bool())) {
        executes {
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, null) { withFlareSettings { copy(enabled = enabled) } }
        }
    }
    "enable"(arg("stableKey", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, stableKey) {
                withFlareSettings { copy(enabled = enabled) }
            }
        }
    }
    "sizeOffset"(arg("value", FloatArgumentType.floatArg(0f, 2f))) {
        executes {
            val sizeOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withFlareSettings { copy(sizeOffset = sizeOffset) } }
        }
    }
    "sizeOffset"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 2f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val sizeOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withFlareSettings { copy(sizeOffset = sizeOffset) }
            }
        }
    }
    "falloffDistance"(arg("value", FloatArgumentType.floatArg(0f, 100f))) {
        executes {
            val falloffDistance = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withFlareSettings { copy(falloffDistance = falloffDistance) } }
        }
    }
    "falloffDistance"(
        arg("stableKey", StringArgumentType.string()),
        arg("value", FloatArgumentType.floatArg(0f, 100f))
    ) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val falloffDistance = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withFlareSettings { copy(falloffDistance = falloffDistance) }
            }
        }
    }
    "startAngle"(arg("value", FloatArgumentType.floatArg(0f, 360f))) {
        executes {
            val startAngle = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withFlareSettings { copy(startAngle = startAngle) } }
        }
    }
    "startAngle"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 360f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val startAngle = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withFlareSettings { copy(startAngle = startAngle) }
            }
        }
    }
    "endAngle"(arg("value", FloatArgumentType.floatArg(0f, 360f))) {
        executes {
            val endAngle = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withFlareSettings { copy(endAngle = endAngle) } }
        }
    }
    "endAngle"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 360f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val endAngle = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withFlareSettings { copy(endAngle = endAngle) }
            }
        }
    }
    "angleFactorOffset"(arg("value", FloatArgumentType.floatArg(0f, 5f))) {
        executes {
            val angleFactorOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withFlareSettings { copy(angleFactorOffset = angleFactorOffset) } }
        }
    }
    "angleFactorOffset"(
        arg("stableKey", StringArgumentType.string()),
        arg("value", FloatArgumentType.floatArg(0f, 5f))
    ) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val angleFactorOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withFlareSettings { copy(angleFactorOffset = angleFactorOffset) }
            }
        }
    }
    "intensity"(arg("value", FloatArgumentType.floatArg(0f, 1f))) {
        executes {
            val intensity = FloatArgumentType.getFloat(this, "value")
            updateLight(source, null) { withFlareSettings { copy(intensity = intensity) } }
        }
    }
    "intensity"(arg("stableKey", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 1f))) {
        executes {
            val stableKey = UUID.fromString(StringArgumentType.getString(this, "stableKey"))
            val intensity = FloatArgumentType.getFloat(this, "value")
            updateLight(source, stableKey) {
                withFlareSettings { copy(intensity = intensity) }
            }
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
                            entity.entity.set(c.create(), c.value)
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

        "vars" {
            "global" {
                executes {
                    printVariables("Global variables", source.server.runtimeContext.scope.variables)
                }

                "value"(arg("name", StringArgumentType.string())) {
                    executes {
                        printVariable(
                            scopeName = "Global",
                            map = source.server.runtimeContext.scope.variables,
                            name = StringArgumentType.getString(this, "name")
                        )
                    }
                }
            }

            "entity"(arg("entity", EntityArgument.entity())) {
                executes {
                    val entity = EntityArgument.getEntity(this, "entity")
                    val scope = entity.coroutineScope as? OwnerScope
                        ?: return@executes sendFailure("CodeBlocks: entity ${entity.stringUUID} has no owner scope".literal)
                    printVariables("Entity ${entity.stringUUID} variables", scope.variables)
                }

                "value"(arg("name", StringArgumentType.string())) {
                    executes {
                        val entity = EntityArgument.getEntity(this, "entity")
                        val scope = entity.coroutineScope as? OwnerScope
                            ?: return@executes sendFailure("CodeBlocks: entity ${entity.stringUUID} has no owner scope".literal)
                        printVariable(
                            scopeName = "Entity ${entity.stringUUID}",
                            map = scope.variables,
                            name = StringArgumentType.getString(this, "name")
                        )
                    }
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
                if (path.endsWith(".reload.kts")) {
                    return@executes sendFailure("Reload scripts run only during server resource reload".literal)
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
                    if (result.isSuccess) {
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

private fun CommandExtension.registerKatariCommands() {
    "katari" {
        requires { hasPermission(2) }

        "run"(arg("path", StringArgumentType.greedyString()) { getAvailableKatariScripts() }) {
            executes {
                val path = StringArgumentType.getString(this, "path")
                val player = runCatching { source.playerOrException }.getOrNull()
                val result = source.server.runtimeContext.katari.run(path, player)
                if (result.isSuccess) {
                    sendSuccess { "Katari script started: $path (${result.getOrThrow()})".literal }
                } else {
                    val error = result.exceptionOrNull()
                    HollowEngine.LOGGER.error("Katari script start failed", error)
                    sendFailure("Katari script start failed: ${error?.message ?: "Unknown error"}".literal)
                }
                SUCCESS
            }
        }

        "list" {
            executes {
                val system = source.server.runtimeContext.katari
                val runs = system.list()
                val scripts = getAvailableKatariScripts().sorted()
                if (scripts.isEmpty()) {
                    sendSuccess { "No .ktr scripts found in hollowengine/scripts/".literal }
                } else {
                    sendSuccess { "Available Katari scripts:".literal }
                    scripts.forEach { source.sendSuccess({ "- $it".literal }, false) }
                }
                if (runs.isEmpty()) {
                    source.sendSuccess({ "Katari runs: <none>".literal }, false)
                } else {
                    source.sendSuccess({ "Katari runs:".literal }, false)
                    runs.forEach { run ->
                        val status = when (run.status) {
                            KatariRunStatus.RUNNING -> "running"
                            KatariRunStatus.PAUSED -> "paused"
                            KatariRunStatus.FAILED -> "failed"
                        }
                        val suffix = run.error?.let { " - $it" }.orEmpty()
                        source.sendSuccess({ "- ${run.id} [$status] ${run.path}$suffix".literal }, false)
                    }
                }
                SUCCESS
            }
        }

        "stop"(arg("target", StringArgumentType.greedyString()) { listOf("all") }) {
            executes {
                val target = StringArgumentType.getString(this, "target")
                val stopped = source.server.runtimeContext.katari.stop(target)
                if (stopped == 0) {
                    sendFailure("Katari run not found: $target".literal)
                } else {
                    sendSuccess { "Stopped Katari run(s): $stopped".literal }
                }
                SUCCESS
            }
        }

        "choose"(
            arg("run", StringArgumentType.string()),
            arg("option", StringArgumentType.greedyString())
        ) {
            executes {
                val run = StringArgumentType.getString(this, "run")
                val option = StringArgumentType.getString(this, "option")
                if (source.server.runtimeContext.katari.choose(run, option)) {
                    sendSuccess { "Katari choice selected: $option".literal }
                } else {
                    sendFailure("Katari choice is not pending for run: $run".literal)
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

private fun CommandContext<CommandSourceStack>.printVariables(header: String, map: VariableMap): Int {
    val entries = map.entries().sortedBy { it.key }
    if (entries.isEmpty()) {
        return sendSuccess { "$header: <empty>".literal }
    }

    sendSuccess { "$header:".literal }
    entries.forEach { (name, wrapper) ->
        source.sendSuccess({ "- $name = ${wrapper.describeVariableValue()}".literal }, false)
    }
    return 1
}

private fun CommandContext<CommandSourceStack>.printVariable(scopeName: String, map: VariableMap, name: String): Int {
    val wrapper = map.entries().firstOrNull { it.key == name }?.value
        ?: return sendFailure("CodeBlocks: $scopeName variable '$name' not found".literal)
    return sendSuccess { "$scopeName variable '$name' = ${wrapper.describeVariableValue()}".literal }
}

private fun CompoundTag.describeVariableValue(): String = get(VariableMap.VALUE_KEY).describeTag()

private fun Tag?.describeTag(): String = this?.toString() ?: "<null>"

// region Particle Functions
private fun spawnParticleAtPosition(particleName: String, pos: Vec3) {
    val particleFile = BedrockParticles.PARTICLES[particleName.rl] ?: error("Particle not found")
    Minecraft.getInstance().level?.system?.spawn(
        ParticleEffect.fromFile(particleFile),
        transform = Transform.create(Vec3f(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat()))
    ) ?: error("Client level is not available")
}

private fun spawnParticleOnEntity(particleName: String, entity: LivingEntity) {
    val particleFile = BedrockParticles.PARTICLES[particleName.rl] ?: error("Particle not found")
    Minecraft.getInstance().level?.system?.spawn(
        ParticleEffect.fromFile(particleFile),
        query = LivingEntityQuery(entity)
    ) ?: error("Client level is not available")
}

private fun removeParticles(particleName: String) {
    val file = BedrockParticles.PARTICLES[particleName.rl] ?: error("Particle not found")
    Minecraft.getInstance().level?.system?.remove(
        file.particleEffect.description.identifier
    ) ?: error("Client level is not available")
}
// endregion

// region Utility Functions
private fun copyHandItemToClipboard(player: Player) {
    val item = player.mainHandItem
    val location = "\"" + BuiltInRegistries.ITEM.getKey(item.item).toString() + "\""
    val count = item.count
    val nbt = item.components


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

private fun spawnPointLight(source: CommandSourceStack, radius: Float): UUID {
    val stableKey = UUID.randomUUID()
    val position = source.position
    val service = MaterializationRuntimeState.service(source.level)
    val snapshot = EntitySnapshot(
        components = listOf(
            StableKeyComponent(stableKey),
            worldAnchorFor(position),
            TransformComponent().withWorldPosition(position),
            PointLightComponent(radius = radius),
        )
    )
    service.materialize(snapshot)
    service.snapshot(stableKey)?.let(service::syncSnapshot)
    return stableKey
}

private fun spawnSpotLight(source: CommandSourceStack, distance: Float, innerAngle: Float, outerAngle: Float): UUID {
    val stableKey = UUID.randomUUID()
    val position = source.position
    val service = MaterializationRuntimeState.service(source.level)
    val rotation = rotationFromPositiveZ(source.entity?.lookAngle ?: Vec3(0.0, 0.0, 1.0))
    val snapshot = EntitySnapshot(
        components = listOf(
            StableKeyComponent(stableKey),
            worldAnchorFor(position),
            TransformComponent(
                translation = Vec3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat()),
                rotation = rotation,
            ),
            SpotLightComponent(distance = distance, innerAngle = innerAngle, outerAngle = outerAngle),
        )
    )
    service.materialize(snapshot)
    service.snapshot(stableKey)?.let(service::syncSnapshot)
    return stableKey
}

private fun listLights(source: CommandSourceStack): Int {
    val rows = arrayListOf<String>()
    val seen = linkedSetOf<UUID>()

    source.server.allLevels.forEach { level ->
        val service = MaterializationRuntimeState.service(level)
        service.records.forEach { record ->
            val snapshot = service.snapshot(record.stableKey) ?: return@forEach
            val light = snapshot.lightComponentOrNull() ?: return@forEach
            if (!seen.add(record.stableKey)) return@forEach
            rows += "${record.stableKey} | ${light.javaClass.simpleName} | enabled=${light.enabled} | pos=${
                formatPosition(
                    snapshot.transformOrNull()?.translation
                )
            } | level=${level.dimension().location()}"
        }
        WorldAnchorSavedData.get(level).allRecords().forEach { record ->
            val light = record.snapshot.lightComponentOrNull() ?: return@forEach
            if (!seen.add(record.stableKey)) return@forEach
            rows += "${record.stableKey} | ${light.javaClass.simpleName} | enabled=${light.enabled} | pos=${
                formatPosition(
                    record.snapshot.transformOrNull()?.translation
                )
            } | level=${level.dimension().location()} | dormant=true"
        }
    }

    if (rows.isEmpty()) {
        source.sendFailure("No anchored lights found".literal)
        return 0
    }

    source.sendSuccess({ "Anchored lights:".literal }, false)
    rows.sorted().forEach { source.sendSuccess({ it.literal }, false) }
    return 1
}

private fun removeLight(source: CommandSourceStack, stableKey: UUID?): Int {
    val resolved = resolveLightTarget(source, stableKey) ?: return 0
    if (resolved.isDormantWorldRecord) {
        WorldAnchorSavedData.get(resolved.level).remove(resolved.stableKey)
        source.sendSuccess({ "Removed anchored light ${resolved.stableKey}".literal }, true)
        return 1
    } else {
        val service = MaterializationRuntimeState.service(resolved.level)
        if (service.remove(resolved.stableKey, syncToClients = true)) {
            source.sendSuccess({ "Removed anchored light ${resolved.stableKey}".literal }, true)
            return 1
        } else {
            source.sendFailure("Anchored light ${resolved.stableKey} was not found".literal)
            return 0
        }
    }
}

private fun updateLight(
    source: CommandSourceStack,
    stableKey: UUID?,
    updater: LightComponent.() -> LightComponent,
): Int {
    val resolved = resolveLightTarget(source, stableKey) ?: return 0
    val current = resolved.snapshot.lightComponentOrNull()
        ?: run {
            source.sendFailure("Anchored object ${resolved.stableKey} does not contain a light component".literal)
            return 0
        }

    val updatedSnapshot = resolved.snapshot.withLightComponent(current.updater())
    applyUpdatedLightSnapshot(resolved, updatedSnapshot)
    source.sendSuccess({ "Updated anchored light ${resolved.stableKey}".literal }, true)
    return 1
}

private fun resolveLightTarget(source: CommandSourceStack, stableKey: UUID?): LightTargetResolution? {
    if (stableKey != null) {
        val resolved = findAnchoredSnapshot(source, stableKey)
            ?: run {
                source.sendFailure("Anchored light $stableKey was not found".literal)
                return null
            }
        if (resolved.snapshot.lightComponentOrNull() == null) {
            source.sendFailure("Anchored object $stableKey does not contain a light component".literal)
            return null
        }
        return LightTargetResolution(resolved.level, stableKey, resolved.snapshot, resolved.isDormantWorldRecord)
    }

    return findNearestLightTarget(source)
        ?: run {
            source.sendFailure("No nearby anchored world light was found".literal)
            null
        }
}

private fun findNearestLightTarget(source: CommandSourceStack): LightTargetResolution? {
    val origin = source.position
    val level = source.level
    var best: LightTargetResolution? = null
    var bestDistance = Double.MAX_VALUE

    val service = MaterializationRuntimeState.service(level)
    service.records.forEach { record ->
        val snapshot = service.snapshot(record.stableKey) ?: return@forEach
        if (snapshot.lightComponentOrNull() == null) return@forEach
        if (snapshot.anchorOrNull() !is WorldAnchor) return@forEach
        val transform = snapshot.transformOrNull() ?: return@forEach
        val position = Vec3(
            transform.translation.x.toDouble(),
            transform.translation.y.toDouble(),
            transform.translation.z.toDouble()
        )
        val distance = position.distanceToSqr(origin)
        if (distance < bestDistance) {
            bestDistance = distance
            best = LightTargetResolution(level, record.stableKey, snapshot, isDormantWorldRecord = false)
        }
    }

    WorldAnchorSavedData.get(level).allRecords().forEach { record ->
        if (record.snapshot.lightComponentOrNull() == null) return@forEach
        if (record.snapshot.anchorOrNull() !is WorldAnchor) return@forEach
        val transform = record.snapshot.transformOrNull() ?: return@forEach
        val position = Vec3(
            transform.translation.x.toDouble(),
            transform.translation.y.toDouble(),
            transform.translation.z.toDouble()
        )
        val distance = position.distanceToSqr(origin)
        if (distance < bestDistance) {
            bestDistance = distance
            best = LightTargetResolution(level, record.stableKey, record.snapshot, isDormantWorldRecord = true)
        }
    }

    return best
}

private fun applyUpdatedLightSnapshot(resolved: LightTargetResolution, snapshot: EntitySnapshot) {
    if (resolved.isDormantWorldRecord) {
        WorldAnchorSavedData.get(resolved.level).put(DormantRecord(resolved.stableKey, snapshot))
    } else {
        val service = MaterializationRuntimeState.service(resolved.level)
        service.materialize(snapshot)
        service.snapshot(resolved.stableKey)?.let(service::syncSnapshot)
    }
}

private data class LightTargetResolution(
    val level: net.minecraft.server.level.ServerLevel,
    val stableKey: UUID,
    val snapshot: EntitySnapshot,
    val isDormantWorldRecord: Boolean,
)

private fun formatPosition(position: Vec3f?): String {
    if (position == null) return "unknown"
    return "(${position.x.format(2)}, ${position.y.format(2)}, ${position.z.format(2)})"
}

private fun Float.format(decimals: Int): String = "%.${decimals}f".format(this)

private fun rotationFromPositiveZ(direction: Vec3): QuatF {
    val normalized = MutableVec3f(direction.x.toFloat(), direction.y.toFloat(), direction.z.toFloat())
    if (normalized.length() <= 1e-5f) return QuatF.IDENTITY
    normalized.norm()

    val forward = Vec3f(0f, 0f, 1f)
    val dot = forward.dot(normalized)
    if (dot >= 0.9999f) return QuatF.IDENTITY
    if (dot <= -0.9999f) return QuatF(0f, 1f, 0f, 0f)

    val axis = forward.cross(normalized, MutableVec3f()).norm()
    return QuatF(axis.x, axis.y, axis.z, 1f + dot).normed()
}

private fun LightComponent.withEnabled(value: Boolean): LightComponent = when (this) {
    is PointLightComponent -> copy(enabled = value)
    is SpotLightComponent -> copy(enabled = value)
}

private fun LightComponent.withColor(value: LightColor): LightComponent = when (this) {
    is PointLightComponent -> copy(color = value)
    is SpotLightComponent -> copy(color = value)
}

private fun LightComponent.withIntensity(value: Float): LightComponent = when (this) {
    is PointLightComponent -> copy(intensity = value)
    is SpotLightComponent -> copy(intensity = value)
}

private fun LightComponent.withShadowSettings(transform: ShadowSettings.() -> ShadowSettings): LightComponent =
    when (this) {
        is PointLightComponent -> copy(shadow = (shadow ?: ShadowSettings()).transform())
        is SpotLightComponent -> copy(shadow = (shadow ?: ShadowSettings()).transform())
    }

private fun LightComponent.withVolumetricFogSettings(transform: VolumetricFogSettings.() -> VolumetricFogSettings): LightComponent =
    when (this) {
        is PointLightComponent -> copy(volumetricFog = (volumetricFog ?: VolumetricFogSettings()).transform())
        is SpotLightComponent -> copy(volumetricFog = (volumetricFog ?: VolumetricFogSettings()).transform())
    }

private fun LightComponent.withFlareSettings(transform: FlareSettings.() -> FlareSettings): LightComponent =
    when (this) {
        is PointLightComponent -> copy(flare = (flare ?: FlareSettings()).transform())
        is SpotLightComponent -> copy(flare = (flare ?: FlareSettings()).transform())
    }

private fun attachAnchoredModel(host: net.minecraft.world.entity.Entity, modelName: String): UUID {
    host.entity
    val stableKey = UUID.randomUUID()
    val service = MaterializationRuntimeState.service(host.level())
    val snapshot = EntitySnapshot(
        components = listOf(
            StableKeyComponent(stableKey),
            EntityAnchor(host.uuid),
            Model(modelName),
            TransformComponent(),
        )
    )
    service.materialize(snapshot)
    service.snapshot(stableKey)?.let(service::syncSnapshot)
    return stableKey
}

private fun spawnAnchoredModel(source: CommandSourceStack, position: Vec3, modelName: String): UUID {
    val stableKey = UUID.randomUUID()
    val service = MaterializationRuntimeState.service(source.level)
    val anchor = worldAnchorFor(position)
    val snapshot = EntitySnapshot(
        components = listOf(
            StableKeyComponent(stableKey),
            anchor,
            Model(modelName),
            TransformComponent().withWorldPosition(position),
        )
    )
    service.materialize(snapshot)
    service.snapshot(stableKey)?.let(service::syncSnapshot)
    return stableKey
}

private fun moveAnchoredModel(source: CommandSourceStack, stableKey: UUID, position: Vec3): Int {
    val resolved = findAnchoredSnapshot(source, stableKey)
        ?: run {
            source.sendFailure("Anchored object $stableKey was not found".literal)
            return 0
        }

    val (level, snapshot, isDormantWorldRecord) = resolved
    val service = MaterializationRuntimeState.service(level)
    val anchor = snapshot.anchorOrNull()
        ?: run {
            source.sendFailure("Anchored object $stableKey does not have an anchor".literal)
            return 0
        }

    val updated = when (anchor) {
        is WorldAnchor -> snapshot
            .withIdentity(worldAnchorFor(position, anchor.localId))
            .withOrReplace((snapshot.transformOrNull() ?: TransformComponent()).withWorldPosition(position))

        is EntityAnchor -> snapshot
            .withOrReplace(
                (snapshot.transformOrNull() ?: TransformComponent())
                    .withTranslation(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
            )
    }

    if (isDormantWorldRecord) {
        WorldAnchorSavedData.get(level).put(DormantRecord(stableKey, updated))
    } else {
        service.materialize(updated)
        service.snapshot(stableKey)?.let(service::syncSnapshot)
    }

    source.sendSuccess({ "Moved anchored object $stableKey".literal }, true)
    return 1
}

private fun removeAnchoredModel(source: CommandSourceStack, stableKey: UUID): Int {
    source.server.allLevels.forEach { level ->
        val service = MaterializationRuntimeState.service(level)
        if (service.remove(stableKey, syncToClients = true)) {
            source.sendSuccess({ "Removed anchored object $stableKey".literal }, true)
            return 1
        }
    }
    source.sendFailure("Anchored object $stableKey was not found".literal)
    return 0
}

private data class AnchoredSnapshotResolution(
    val level: net.minecraft.server.level.ServerLevel,
    val snapshot: EntitySnapshot,
    val isDormantWorldRecord: Boolean,
)

private fun findAnchoredSnapshot(source: CommandSourceStack, stableKey: UUID): AnchoredSnapshotResolution? {
    source.server.allLevels.forEach { level ->
        val service = MaterializationRuntimeState.service(level)
        val runtimeSnapshot = service.snapshot(stableKey)
        if (runtimeSnapshot != null) {
            return AnchoredSnapshotResolution(level, runtimeSnapshot, isDormantWorldRecord = false)
        }

        val dormant = WorldAnchorSavedData.get(level).allRecords().firstOrNull { it.stableKey == stableKey }
        if (dormant != null) {
            return AnchoredSnapshotResolution(level, dormant.snapshot, isDormantWorldRecord = true)
        }
    }
    return null
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






