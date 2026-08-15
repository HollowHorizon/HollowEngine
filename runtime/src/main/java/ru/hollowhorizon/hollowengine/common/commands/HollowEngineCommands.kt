package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.FloatArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.coordinates.Vec3Argument
import net.minecraft.core.registries.BuiltInRegistries
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
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.runtimeContext
import ru.hollowhorizon.hollowengine.common.dialogue.DialogueInput
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.geary.api.GearyRuntimeState
import ru.hollowhorizon.hollowengine.common.geary.binding.*
import ru.hollowhorizon.hollowengine.common.geary.components.*
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.LevelSnapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.Snapshot
import ru.hollowhorizon.hollowengine.common.geary.snapshot.snapshotOf
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf
import ru.hollowhorizon.hollowengine.common.npcs.NpcAnimationRuntime
import ru.hollowhorizon.hollowengine.common.scripting.nodes.EntityNodeRuntime
import ru.hollowhorizon.hollowengine.common.scripting.nodes.addNode
import ru.hollowhorizon.hollowengine.common.scripting.nodes.removeNode
import ru.hollowhorizon.hollowengine.common.scripting.NODE_SCRIPT_EXTENSION
import ru.hollowhorizon.hollowengine.common.scripting.RELOAD_SCRIPT_EXTENSION
import ru.hollowhorizon.hollowengine.common.scripting.UI_SCRIPT_EXTENSION
import ru.hollowhorizon.hollowengine.common.scripting.ScriptLoader
import ru.hollowhorizon.hollowengine.common.commands.arguments.ScriptPathArgument
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptId
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import ru.hollowhorizon.hollowengine.common.scripting.state.StateContext
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
            registerScriptingCommands()
            registerAddonCommands()
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
                val snapshotId = attachNodeModel(host, modelName)
                sendSuccess { "Attached node model with snapshotId $snapshotId".literal }
            }
        }

        "player-preset"(arg("entity", EntityArgument.entity())) {
            executes {
                val host = EntityArgument.getEntity(this, "entity")
                applyStandardPlayerAnimationPreset(source, host, hideVanilla = true)
            }
        }

        "player-preset"(
            arg("entity", EntityArgument.entity()),
            arg("hideVanilla", BoolArgumentType.bool())
        ) {
            executes {
                val host = EntityArgument.getEntity(this, "entity")
                val hideVanilla = BoolArgumentType.getBool(this, "hideVanilla")
                applyStandardPlayerAnimationPreset(source, host, hideVanilla)
            }
        }

        "hide-vanilla"(
            arg("entity", EntityArgument.entity()),
            arg("hidden", BoolArgumentType.bool())
        ) {
            executes {
                val entity = EntityArgument.getEntity(this, "entity")
                setHideVanillaEntityModel(entity, BoolArgumentType.getBool(this, "hidden"))
                sendSuccess { "Vanilla model visibility component updated for ${entity.name.string}".literal }
            }
        }

        "animation" {
            "play"(
                arg("entity", EntityArgument.entity()),
                arg("animation", StringArgumentType.string())
            ) {
                executes {
                    val entity = EntityArgument.getEntity(this, "entity")
                    val animation = StringArgumentType.getString(this, "animation")
                    playEntityAnimation(source, entity, animation, AnimationPlayMode.Once)
                }
            }

            "play"(
                arg("entity", EntityArgument.entity()),
                arg("animation", StringArgumentType.string()),
                arg("mode", StringArgumentType.string()) { animationPlayModeNames() }
            ) {
                executes {
                    val entity = EntityArgument.getEntity(this, "entity")
                    val animation = StringArgumentType.getString(this, "animation")
                    val mode = parseAnimationPlayMode(StringArgumentType.getString(this, "mode"))
                    playEntityAnimation(source, entity, animation, mode)
                }
            }

            "play"(
                arg("entity", EntityArgument.entity()),
                arg("animation", StringArgumentType.string()),
                arg("mode", StringArgumentType.string()) { animationPlayModeNames() },
                arg("fadeIn", FloatArgumentType.floatArg(0f, 10f)),
                arg("fadeOut", FloatArgumentType.floatArg(0f, 10f))
            ) {
                executes {
                    val entity = EntityArgument.getEntity(this, "entity")
                    val animation = StringArgumentType.getString(this, "animation")
                    val mode = parseAnimationPlayMode(StringArgumentType.getString(this, "mode"))
                    val fadeIn = FloatArgumentType.getFloat(this, "fadeIn")
                    val fadeOut = FloatArgumentType.getFloat(this, "fadeOut")
                    playEntityAnimation(source, entity, animation, mode, fadeIn, fadeOut)
                }
            }

            "stop"(
                arg("entity", EntityArgument.entity()),
                arg("animation", StringArgumentType.string())
            ) {
                executes {
                    val entity = EntityArgument.getEntity(this, "entity")
                    val animation = StringArgumentType.getString(this, "animation")
                    stopEntityAnimation(source, entity, animation, DEFAULT_ANIMATION_FADE_DURATION)
                }
            }

            "stop"(
                arg("entity", EntityArgument.entity()),
                arg("animation", StringArgumentType.string()),
                arg("fade", FloatArgumentType.floatArg(0f, 10f))
            ) {
                executes {
                    val entity = EntityArgument.getEntity(this, "entity")
                    val animation = StringArgumentType.getString(this, "animation")
                    val fade = FloatArgumentType.getFloat(this, "fade")
                    stopEntityAnimation(source, entity, animation, fade)
                }
            }
        }

        "spawn"(
            arg("pos", Vec3Argument.vec3()),
            arg("model", StringArgumentType.string()) { getAvailableModels() }
        ) {
            executes {
                val position = Vec3Argument.getVec3(this, "pos")
                val modelName = StringArgumentType.getString(this, "model")
                val snapshotId = spawnNodeModel(source, position, modelName)
                sendSuccess { "Spawned node model with snapshotId $snapshotId".literal }
            }
        }

        "move"(
            arg("snapshotId", StringArgumentType.string()),
            arg("pos", Vec3Argument.vec3())
        ) {
            executes {
                val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
                val position = Vec3Argument.getVec3(this, "pos")
                moveNodeModel(source, snapshotId, position)
            }
        }

        "remove"(arg("snapshotId", StringArgumentType.string())) {
            executes {
                val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
                removeNodeModel(source, snapshotId)
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
                    val snapshotId = spawnPointLight(source, radius)
                    sendSuccess { "Spawned point light with snapshotId $snapshotId".literal }
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

                    val snapshotId = spawnSpotLight(source, distance, innerAngle, outerAngle)
                    sendSuccess { "Spawned spot light with snapshotId $snapshotId".literal }
                }
            }
        }

        "remove" {
            executes { removeLight(source, null) }
        }
        "remove"(arg("snapshotId", StringArgumentType.string())) {
            executes { removeLight(source, UUID.fromString(StringArgumentType.getString(this, "snapshotId"))) }
        }

        "enable"(arg("enabled", BoolArgumentType.bool())) {
            executes {
                val enabled = BoolArgumentType.getBool(this, "enabled")
                updateLight(source, null) { withEnabled(enabled) }
            }
        }
        "enable"(arg("snapshotId", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
            executes {
                val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
                val enabled = BoolArgumentType.getBool(this, "enabled")
                updateLight(
                    source,
                    snapshotId
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
            arg("snapshotId", StringArgumentType.string()),
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
                    UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
                ) { withColor(color) }
            }
        }

        "intensity"(arg("value", FloatArgumentType.floatArg(0f))) {
            executes {
                val intensity = FloatArgumentType.getFloat(this, "value")
                updateLight(source, null) { withIntensity(intensity) }
            }
        }
        "intensity"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f))) {
            executes {
                val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
                val intensity = FloatArgumentType.getFloat(this, "value")
                updateLight(
                    source,
                    snapshotId
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
    "enable"(arg("snapshotId", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, snapshotId) {
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
    "dynamic"(arg("snapshotId", StringArgumentType.string()), arg("value", BoolArgumentType.bool())) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val dynamic = BoolArgumentType.getBool(this, "value")
            updateLight(source, snapshotId) {
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
    "distance"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val distance = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "fovOffset"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(-180f, 180f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val fovOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "enable"(arg("snapshotId", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, snapshotId) {
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
    "samples"(arg("snapshotId", StringArgumentType.string()), arg("value", IntegerArgumentType.integer(1, 128))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val samples = IntegerArgumentType.getInteger(this, "value")
            updateLight(source, snapshotId) {
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
    "scattering"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 1f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val scattering = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "density"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 5f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val density = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "anisotropy"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(-1f, 1f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val anisotropy = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "enable"(arg("snapshotId", StringArgumentType.string()), arg("enabled", BoolArgumentType.bool())) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val enabled = BoolArgumentType.getBool(this, "enabled")
            updateLight(source, snapshotId) {
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
    "sizeOffset"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 2f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val sizeOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
        arg("snapshotId", StringArgumentType.string()),
        arg("value", FloatArgumentType.floatArg(0f, 100f))
    ) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val falloffDistance = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "startAngle"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 360f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val startAngle = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "endAngle"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 360f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val endAngle = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
        arg("snapshotId", StringArgumentType.string()),
        arg("value", FloatArgumentType.floatArg(0f, 5f))
    ) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val angleFactorOffset = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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
    "intensity"(arg("snapshotId", StringArgumentType.string()), arg("value", FloatArgumentType.floatArg(0f, 1f))) {
        executes {
            val snapshotId = UUID.fromString(StringArgumentType.getString(this, "snapshotId"))
            val intensity = FloatArgumentType.getFloat(this, "value")
            updateLight(source, snapshotId) {
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

    "pos" {
        executes {
            copyTargetPositionToClipboard(source.playerOrException)
            SUCCESS
        }
    }

    registerDialogueCommands()
}

private fun CommandExtension.registerDialogueCommands() {
    "dialogue" {
        "advance" {
            executes {
                if (DialogueInput.advance(source.playerOrException)) SUCCESS
                else sendFailure("hollowengine.commands.dialogue_not_in_dialogue".mcTranslate())
            }
        }

        "choose"(arg("option", IntegerArgumentType.integer(0))) {
            executes {
                val option = IntegerArgumentType.getInteger(this, "option")
                if (DialogueInput.choose(source.playerOrException, option)) SUCCESS
                else sendFailure("hollowengine.commands.dialogue_no_such_option".mcTranslate(option))
            }
        }
    }
}

private fun CommandExtension.registerScriptingCommands() {
    "scripting" {
        requires { hasPermission(2) }

        "run"(
            nodeScriptArgument(),
            arg("state", StringArgumentType.string())
        ) {
            executes {
                val id = ScriptPathArgument.getScript(this, "path")
                if (!isNodeScriptPath(id.path)) {
                    return@executes sendFailure(
                        "hollowengine.commands.scripting_state_requires_node_script"
                            .mcTranslate(ScriptRegistry.display(id))
                    )
                }
                source.server.addNode(
                    ScriptRegistry.display(id),
                    context = StateContext(nextState = StringArgumentType.getString(this, "state"))
                )
                SUCCESS
            }
        }

        "run"(runnableScriptArgument()) {
            executes {
                val id = ScriptPathArgument.getScript(this, "path")
                if (isNodeScriptPath(id.path)) {
                    source.server.addNode(ScriptRegistry.display(id))
                    return@executes SUCCESS
                }
                runPlainScript(source, id)
            }
        }

        "stop"(runningNodeArgument()) {
            executes {
                source.server.removeNode(ScriptRegistry.display(ScriptPathArgument.getScript(this, "path")))
                SUCCESS
            }
        }

        "attach"(
            arg("entity", EntityArgument.entity()),
            nodeScriptArgument(),
            arg("state", StringArgumentType.string())
        ) {
            executes {
                attachEntityNode(
                    EntityArgument.getEntity(this, "entity"),
                    ScriptPathArgument.getScript(this, "path"),
                    StringArgumentType.getString(this, "state"),
                )
            }
        }

        "attach"(
            arg("entity", EntityArgument.entity()),
            nodeScriptArgument()
        ) {
            executes {
                attachEntityNode(
                    EntityArgument.getEntity(this, "entity"),
                    ScriptPathArgument.getScript(this, "path"),
                    state = null,
                )
            }
        }

        "detach"(
            arg("entity", EntityArgument.entity()),
            attachedNodeArgument()
        ) {
            executes {
                val entity = EntityArgument.getEntity(this, "entity")
                val path = ScriptRegistry.display(ScriptPathArgument.getScript(this, "path"))
                if (EntityNodeRuntime.detach(entity, path)) SUCCESS
                else sendFailure("Node '$path' is not attached to ${entity.name.string}".literal)
            }
        }

        "list" {
            executes { listServerNodes(source) }

            "entity"(arg("entity", EntityArgument.entity())) {
                executes { listEntityNodes(source, EntityArgument.getEntity(this, "entity")) }
            }
        }

        "compile" {
            executes { compileAllScripts(source) }
        }
    }
}

/**
 * Fills the compilation cache for every known script, which is what turns a pack into something that
 * runs on a client without the Kotlin compiler addon installed.
 */
private fun compileAllScripts(source: CommandSourceStack): Int {
    val scripts = ScriptRegistry.list(".kts")
    if (scripts.isEmpty()) {
        source.sendFailure("No scripts were found".literal)
        return 0
    }
    val failures = ScriptLoader.compileAll(scripts)
    val compiled = scripts.size - failures.size
    failures.forEach { (id, error) ->
        HollowEngine.LOGGER.error("Failed to compile {}", ScriptRegistry.display(id), error)
        source.sendFailure("- ${ScriptRegistry.display(id)}: ${error.message}".literal)
    }
    source.sendSuccess({ "Compiled $compiled of ${scripts.size} scripts".literal }, true)
    return compiled
}

/** Every node script the engine can see, in the spelling the commands expect back. */
private fun nodeScriptArgument(): RequiredArgumentBuilder<CommandSourceStack, ScriptId> =
    arg<ScriptId, CommandSourceStack>("path", ScriptPathArgument.scriptPath()).suggests { _, builder ->
        SharedSuggestionProvider.suggest(DirectoryManager.componentScripts.map(ScriptRegistry::display), builder)
    }

/**
 * Everything `run` accepts: node scripts, plus plain `.kts` files, which is what one reaches for
 * when trying something out.
 */
private fun runnableScriptArgument(): RequiredArgumentBuilder<CommandSourceStack, ScriptId> =
    arg<ScriptId, CommandSourceStack>("path", ScriptPathArgument.scriptPath()).suggests { _, builder ->
        val nodes = DirectoryManager.componentScripts.map(ScriptRegistry::display)
        val plain = ScriptRegistry.list(".kts").filter { isPlainScriptPath(it.path) }.map(ScriptRegistry::display)
        SharedSuggestionProvider.suggest(nodes + plain, builder)
    }

/** Runs a plain script once, reporting whatever it threw straight back to the caller. */
private fun runPlainScript(source: CommandSourceStack, id: ScriptId): Int {
    val display = ScriptRegistry.display(id)
    if (!isPlainScriptPath(id.path)) {
        return source.sendFailure("'$display' is not a script /he scripting run can execute".literal).let { 0 }
    }
    return ScriptLoader.execute<Any?>(id).fold(
        onSuccess = {
            source.sendSuccess({ "Executed '$display'".literal }, true)
            1
        },
        onFailure = { error ->
            HollowEngine.LOGGER.error("Failed to run '{}'", display, error)
            source.sendFailure("Failed to run '$display': ${error.message ?: error::class.simpleName}".literal)
            0
        },
    )
}

/** Only the nodes actually running on this server, which is what one can stop. */
private fun runningNodeArgument(): RequiredArgumentBuilder<CommandSourceStack, ScriptId> =
    arg<ScriptId, CommandSourceStack>("path", ScriptPathArgument.scriptPath()).suggests { context, builder ->
        SharedSuggestionProvider.suggest(context.source.server.runtimeContext.nodes.paths(), builder)
    }

/** Only the nodes attached to the entity named earlier in the command. */
private fun attachedNodeArgument(): RequiredArgumentBuilder<CommandSourceStack, ScriptId> =
    arg<ScriptId, CommandSourceStack>("path", ScriptPathArgument.scriptPath()).suggests { context, builder ->
        val attached = runCatching { EntityNodeRuntime.paths(EntityArgument.getEntity(context, "entity")) }
            .getOrDefault(emptySet())
        SharedSuggestionProvider.suggest(attached, builder)
    }

private fun com.mojang.brigadier.context.CommandContext<CommandSourceStack>.attachEntityNode(
    entity: net.minecraft.world.entity.Entity,
    id: ScriptId,
    state: String?,
): Int {
    val path = ScriptRegistry.display(id)
    if (!isNodeScriptPath(id.path)) {
        return sendFailure("hollowengine.commands.scripting_state_requires_node_script".mcTranslate(path))
    }
    val context = state?.let { StateContext(nextState = it) }
    return if (EntityNodeRuntime.attach(entity, path, context = context)) {
        sendSuccess(true) { "Attached node '$path' to ${entity.name.string}".literal }
    } else {
        sendFailure("Failed to attach node '$path' to ${entity.name.string}".literal)
    }
}

private fun listServerNodes(source: CommandSourceStack): Int {
    val paths = source.server.runtimeContext.nodes.paths().sorted()
    if (paths.isEmpty()) {
        source.sendFailure("No server nodes are running".literal)
        return 0
    }
    source.sendSuccess({ "Server nodes:".literal }, false)
    paths.forEach { source.sendSuccess({ "- $it".literal }, false) }
    return paths.size
}

private fun listEntityNodes(source: CommandSourceStack, entity: net.minecraft.world.entity.Entity): Int {
    val paths = EntityNodeRuntime.paths(entity).sorted()
    if (paths.isEmpty()) {
        source.sendFailure("No nodes are attached to ${entity.name.string}".literal)
        return 0
    }
    source.sendSuccess({ "Nodes on ${entity.name.string}:".literal }, false)
    paths.forEach { source.sendSuccess({ "- $it".literal }, false) }
    return paths.size
}

internal fun isNodeScriptPath(path: String): Boolean = path.endsWith(".$NODE_SCRIPT_EXTENSION")

/** A `.kts` with no special role not a node, UI or reload script, so running it just runs it. */
internal fun isPlainScriptPath(path: String): Boolean {
    if (!path.endsWith(".kts")) return false
    return listOf(NODE_SCRIPT_EXTENSION, UI_SCRIPT_EXTENSION, RELOAD_SCRIPT_EXTENSION)
        .none { path.endsWith(".$it") }
}

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
    val snapshotId = UUID.randomUUID()
    val position = source.position
    val service = NodeRuntimeState.service(source.level)
    val snapshot = LevelSnapshot(
        id = snapshotId,
        dimension = source.level.dimension().location(),
        components = listOf(
            TransformComponent().withWorldPosition(position),
            PointLightComponent(radius = radius),
        )
    )
    service.materialize(snapshot)
    service.snapshot(snapshotId)?.let(service::syncSnapshot)
    return snapshotId
}

private fun spawnSpotLight(source: CommandSourceStack, distance: Float, innerAngle: Float, outerAngle: Float): UUID {
    val snapshotId = UUID.randomUUID()
    val position = source.position
    val service = NodeRuntimeState.service(source.level)
    val rotation = rotationFromPositiveZ(source.entity?.lookAngle ?: Vec3(0.0, 0.0, 1.0))
    val snapshot = LevelSnapshot(
        id = snapshotId,
        dimension = source.level.dimension().location(),
        components = listOf(
            TransformComponent(
                translation = Vec3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat()),
                rotation = rotation,
            ),
            SpotLightComponent(distance = distance, innerAngle = innerAngle, outerAngle = outerAngle),
        )
    )
    service.materialize(snapshot)
    service.snapshot(snapshotId)?.let(service::syncSnapshot)
    return snapshotId
}

private fun listLights(source: CommandSourceStack): Int {
    val rows = arrayListOf<String>()
    val seen = linkedSetOf<UUID>()

    source.server.allLevels.forEach { level ->
        val service = NodeRuntimeState.service(level)
        service.records.forEach { record ->
            val snapshot = service.snapshot(record.snapshotId) ?: return@forEach
            val light = snapshot.lightComponentOrNull() ?: return@forEach
            if (!seen.add(record.snapshotId)) return@forEach
            rows += "${record.snapshotId} | ${light.javaClass.simpleName} | enabled=${light.enabled} | pos=${
                formatPosition(
                    snapshot.transformOrNull()?.translation
                )
            } | level=${level.dimension().location()}"
        }
        WorldNodeSavedData.get(level).allRecords().forEach { record ->
            val light = record.snapshot.lightComponentOrNull() ?: return@forEach
            if (!seen.add(record.id)) return@forEach
            rows += "${record.id} | ${light.javaClass.simpleName} | enabled=${light.enabled} | pos=${
                formatPosition(
                    record.snapshot.transformOrNull()?.translation
                )
            } | level=${level.dimension().location()} | dormant=true"
        }
    }

    if (rows.isEmpty()) {
        source.sendFailure("No node lights found".literal)
        return 0
    }

    source.sendSuccess({ "Node lights:".literal }, false)
    rows.sorted().forEach { source.sendSuccess({ it.literal }, false) }
    return 1
}

private fun removeLight(source: CommandSourceStack, snapshotId: UUID?): Int {
    val resolved = resolveLightTarget(source, snapshotId) ?: return 0
    if (resolved.isDormantWorldRecord) {
        WorldNodeSavedData.get(resolved.level).remove(resolved.snapshotId)
        source.sendSuccess({ "Removed node light ${resolved.snapshotId}".literal }, true)
        return 1
    } else {
        val service = NodeRuntimeState.service(resolved.level)
        if (service.remove(resolved.snapshotId, syncToClients = true)) {
            source.sendSuccess({ "Removed node light ${resolved.snapshotId}".literal }, true)
            return 1
        } else {
            source.sendFailure("Node light ${resolved.snapshotId} was not found".literal)
            return 0
        }
    }
}

private fun updateLight(
    source: CommandSourceStack,
    snapshotId: UUID?,
    updater: LightComponent.() -> LightComponent,
): Int {
    val resolved = resolveLightTarget(source, snapshotId) ?: return 0
    val current = resolved.snapshot.lightComponentOrNull()
        ?: run {
            source.sendFailure("Node object ${resolved.snapshotId} does not contain a light component".literal)
            return 0
        }

    val updatedSnapshot = resolved.snapshot.withLightComponent(current.updater())
    applyUpdatedLightSnapshot(resolved, updatedSnapshot)
    source.sendSuccess({ "Updated node light ${resolved.snapshotId}".literal }, true)
    return 1
}

private fun resolveLightTarget(source: CommandSourceStack, snapshotId: UUID?): LightTargetResolution? {
    if (snapshotId != null) {
        val resolved = findNodeSnapshot(source, snapshotId)
            ?: run {
                source.sendFailure("Node light $snapshotId was not found".literal)
                return null
            }
        if (resolved.snapshot.lightComponentOrNull() == null) {
            source.sendFailure("Node object $snapshotId does not contain a light component".literal)
            return null
        }
        return LightTargetResolution(resolved.level, snapshotId, resolved.snapshot, resolved.isDormantWorldRecord)
    }

    return findNearestLightTarget(source)
        ?: run {
            source.sendFailure("No nearby world node light was found".literal)
            null
        }
}

private fun findNearestLightTarget(source: CommandSourceStack): LightTargetResolution? {
    val origin = source.position
    val level = source.level
    var best: LightTargetResolution? = null
    var bestDistance = Double.MAX_VALUE

    val service = NodeRuntimeState.service(level)
    service.records.forEach { record ->
        val snapshot = service.snapshot(record.snapshotId) ?: return@forEach
        if (snapshot.lightComponentOrNull() == null) return@forEach
        if (record.isEntityBound) return@forEach
        val transform = snapshot.transformOrNull() ?: return@forEach
        val position = Vec3(
            transform.translation.x.toDouble(),
            transform.translation.y.toDouble(),
            transform.translation.z.toDouble()
        )
        val distance = position.distanceToSqr(origin)
        if (distance < bestDistance) {
            bestDistance = distance
            best = LightTargetResolution(level, record.snapshotId, snapshot, isDormantWorldRecord = false)
        }
    }

    WorldNodeSavedData.get(level).allRecords().forEach { record ->
        if (record.snapshot.lightComponentOrNull() == null) return@forEach
        val transform = record.snapshot.transformOrNull() ?: return@forEach
        val position = Vec3(
            transform.translation.x.toDouble(),
            transform.translation.y.toDouble(),
            transform.translation.z.toDouble()
        )
        val distance = position.distanceToSqr(origin)
        if (distance < bestDistance) {
            bestDistance = distance
            best = LightTargetResolution(level, record.id, record.snapshot, isDormantWorldRecord = true)
        }
    }

    return best
}

private fun applyUpdatedLightSnapshot(resolved: LightTargetResolution, snapshot: Snapshot) {
    if (resolved.isDormantWorldRecord) {
        WorldNodeSavedData.get(resolved.level).put(DormantRecord(resolved.snapshotId, snapshot as LevelSnapshot))
    } else {
        val service = NodeRuntimeState.service(resolved.level)
        service.materialize(snapshot)
        service.snapshot(resolved.snapshotId)?.let(service::syncSnapshot)
    }
}

private data class LightTargetResolution(
    val level: net.minecraft.server.level.ServerLevel,
    val snapshotId: UUID,
    val snapshot: Snapshot,
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

private fun applyStandardPlayerAnimationPreset(
    source: CommandSourceStack,
    host: net.minecraft.world.entity.Entity,
    hideVanilla: Boolean,
): Int {
    val snapshotId = attachNodeModel(
        host = host,
        modelName = StandardPlayerAnimatorPreset.MODEL,
        extraComponents = listOf(StandardPlayerAnimatorPreset.create()),
    )
    if (!setHideVanillaEntityModel(host, hideVanilla)) {
        source.sendFailure("Hide vanilla model component is not registered".literal)
        return 0
    }
    source.sendSuccess(
        { "Applied standard player animation preset to ${host.name.string} with snapshotId $snapshotId".literal },
        true,
    )
    return 1
}

private fun playEntityAnimation(
    source: CommandSourceStack,
    entity: net.minecraft.world.entity.Entity,
    animation: String,
    playMode: AnimationPlayMode,
    fadeIn: Float = DEFAULT_ANIMATION_FADE_DURATION,
    fadeOut: Float = DEFAULT_ANIMATION_FADE_DURATION,
): Int {
    if (animation.isBlank()) {
        source.sendFailure("Animation name must not be blank".literal)
        return 0
    }

    NpcAnimationRuntime.apply(
        entity = entity,
        from = null,
        to = animation,
        playMode = playMode,
        fadeIn = fadeIn,
        fadeOut = fadeOut,
    )
    source.sendSuccess(
        {
            "Playing animation '$animation' on ${entity.name.string} with mode $playMode, fadeIn=${
                fadeIn.format(2)
            }s, fadeOut=${fadeOut.format(2)}s".literal
        },
        true,
    )
    return 1
}

private fun stopEntityAnimation(
    source: CommandSourceStack,
    entity: net.minecraft.world.entity.Entity,
    animation: String,
    fadeDuration: Float,
): Int {
    if (animation.isBlank()) {
        source.sendFailure("Animation name must not be blank".literal)
        return 0
    }

    NpcAnimationRuntime.apply(
        entity = entity,
        from = animation,
        to = null,
        playMode = AnimationPlayMode.Once,
        duration = fadeDuration,
    )
    source.sendSuccess(
        { "Stopping animation '$animation' on ${entity.name.string} with ${fadeDuration.format(2)}s fade".literal },
        true,
    )
    return 1
}

private fun parseAnimationPlayMode(value: String): AnimationPlayMode =
    AnimationPlayMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AnimationPlayMode.Once

private fun animationPlayModeNames(): List<String> =
    AnimationPlayMode.entries.map { it.name.lowercase() }

private fun setHideVanillaEntityModel(entity: net.minecraft.world.entity.Entity, hidden: Boolean): Boolean {
    val componentId = ComponentDescriptorRegistry.idFor(HideVanillaEntityModelComponent::class) ?: return false
    val components = GearyRuntimeState.componentsById(entity)
    if (hidden) {
        components[componentId] = HideVanillaEntityModelComponent()
    } else {
        components.remove(componentId)
    }
    GearyRuntimeState.markDirty(entity)
    if (!entity.level().isClientSide) {
        EntitySnapshotPacket(entity.id, snapshotOf(entity)).sendTrackingEntityAndSelf(entity)
    }
    return true
}

private const val DEFAULT_ANIMATION_FADE_DURATION = 0.33f

private fun attachNodeModel(
    host: net.minecraft.world.entity.Entity,
    modelName: String,
    extraComponents: List<Any> = emptyList(),
): UUID {
    val snapshotId = host.uuid
    val service = NodeRuntimeState.service(host.level())
    val snapshot = EntitySnapshot(
        components = listOf(
            Model(modelName),
            TransformComponent(),
        ) + extraComponents
    ).withEntity(host)
    service.materialize(snapshot)
    service.snapshot(snapshotId)?.let(service::syncSnapshot)
    return snapshotId
}

private fun spawnNodeModel(source: CommandSourceStack, position: Vec3, modelName: String): UUID {
    val snapshotId = UUID.randomUUID()
    val service = NodeRuntimeState.service(source.level)
    val snapshot = LevelSnapshot(
        id = snapshotId,
        dimension = source.level.dimension().location(),
        components = listOf(
            Model(modelName),
            TransformComponent().withWorldPosition(position),
        )
    )
    service.materialize(snapshot)
    service.snapshot(snapshotId)?.let(service::syncSnapshot)
    return snapshotId
}

private fun moveNodeModel(source: CommandSourceStack, snapshotId: UUID, position: Vec3): Int {
    val resolved = findNodeSnapshot(source, snapshotId)
        ?: run {
            source.sendFailure("Node object $snapshotId was not found".literal)
            return 0
        }

    val (level, snapshot, isDormantWorldRecord) = resolved
    val service = NodeRuntimeState.service(level)
    val updated = when (snapshot) {
        is LevelSnapshot -> snapshot.withWorldBinding(position)
        else -> snapshot.withOrReplace(
            (snapshot.transformOrNull() ?: TransformComponent())
                .withTranslation(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
        )
    }

    if (isDormantWorldRecord) {
        WorldNodeSavedData.get(level).put(DormantRecord(snapshotId, updated as LevelSnapshot))
    } else {
        service.materialize(updated)
        service.snapshot(snapshotId)?.let(service::syncSnapshot)
    }

    source.sendSuccess({ "Moved node object $snapshotId".literal }, true)
    return 1
}

private fun removeNodeModel(source: CommandSourceStack, snapshotId: UUID): Int {
    source.server.allLevels.forEach { level ->
        val service = NodeRuntimeState.service(level)
        if (service.remove(snapshotId, syncToClients = true)) {
            source.sendSuccess({ "Removed node object $snapshotId".literal }, true)
            return 1
        }
    }
    source.sendFailure("Node object $snapshotId was not found".literal)
    return 0
}

private data class NodeSnapshotResolution(
    val level: net.minecraft.server.level.ServerLevel,
    val snapshot: Snapshot,
    val isDormantWorldRecord: Boolean,
)

private fun findNodeSnapshot(source: CommandSourceStack, snapshotId: UUID): NodeSnapshotResolution? {
    source.server.allLevels.forEach { level ->
        val service = NodeRuntimeState.service(level)
        val runtimeSnapshot = service.snapshot(snapshotId)
        if (runtimeSnapshot != null) {
            return NodeSnapshotResolution(level, runtimeSnapshot, isDormantWorldRecord = false)
        }

        val dormant = WorldNodeSavedData.get(level).allRecords().firstOrNull { it.id == snapshotId }
        if (dormant != null) {
            return NodeSnapshotResolution(level, dormant.snapshot, isDormantWorldRecord = true)
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






