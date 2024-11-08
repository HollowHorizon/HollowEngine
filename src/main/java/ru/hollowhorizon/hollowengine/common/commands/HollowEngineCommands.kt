package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.models.internal.manager.GltfManager
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.onRegisterCommands
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.gui.npcs.dialogue.DialogueGui
import ru.hollowhorizon.hollowengine.client.gui.scripting.roundTo
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.story.openGui
import ru.hollowhorizon.hollowengine.common.scripting.story.startEvent
import java.io.File

@SubscribeEvent
fun onRegisterCommands(event: RegisterCommandsEvent) {
    event.dispatcher.onRegisterCommands {
        "hollowengine" {
            "hand" {
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
            }

            "dialogue" {
                OpenScreenPacket().send(source.playerOrException)
            }

            "model"(
                arg("model", StringArgumentType.greedyString(), ::listModels),
            ) {
                val player = source.playerOrException
                val model = StringArgumentType.getString(this, "model")

                ShowModelInfoPacket(model).send(player)
            }

            "pos" {
                val player = source.playerOrException
                val loc = player.pick(100.0, 0.0f, true).location
                CopyTextPacket("pos(${loc.x.roundTo(2)}, ${loc.y.roundTo(2)}, ${loc.z.roundTo(2)})").send(player)
            }

            "open-gui"(
                arg(
                    "script",
                    StringArgumentType.greedyString()
                ) {
                    DirectoryManager.guiScripts.map { it.toReadablePath() }.toList()
                }
            ) {
                val raw = StringArgumentType.getString(this, "script")
                val script = raw.fromReadablePath()

                if (!script.exists()) {
                    HollowCore.LOGGER.warn("File $script does not exist!")
                    source.player?.sendSystemMessage("File $script does not exist!".literal)
                }

                openGui(script)
                HollowCore.LOGGER.info("Started script $script")
            }

            "start-script"(
                arg(
                    "script",
                    StringArgumentType.greedyString()
                ) {
                    DirectoryManager.storyScripts.map { it.toReadablePath() }.toList()
                }
            ) {
                val raw = StringArgumentType.getString(this, "script")
                val script = raw.fromReadablePath()

                if (!script.exists()) {
                    HollowCore.LOGGER.warn("File $script does not exist!")
                    source.player?.sendSystemMessage("File $script does not exist!".literal)
                }

                startEvent(script)
                HollowCore.LOGGER.info("Started script $script")
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

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class OpenScreenPacket : HollowPacketV3<OpenScreenPacket> {
    override fun handle(player: Player) {
        RenderSystem.recordRenderCall { DialogueGui.open() }
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class CopyTextPacket(val text: String) : HollowPacketV3<CopyTextPacket> {
    override fun handle(player: Player) {
        player.sendSystemMessage(
            "hollowengine.commands.copy".mcTranslate(text.literal)
                .onHoverText("hollowengine.tooltips.copy".mcTranslate)
                .onClickCopy(text)
        )
        mc.keyboardHandler.clipboard = text
    }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class ShowModelInfoPacket(val model: String) : HollowPacketV3<ShowModelInfoPacket> {
    override fun handle(player: Player) {
        val location = model.rl

        GltfManager.getOrCreate(location).let { model ->
            player.sendSystemMessage(
                "hollowengine.commands.model_animations"
                    .mcTranslate(this.model.substringAfterLast('/'))
            )

            model.animationPlayer.nameToAnimationMap.keys.forEach { anim ->
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


            model.modelTree.materials.map { it.texture.path.removeSuffix(".png") }.forEach { anim ->
                player.sendSystemMessage(
                    ("- ".literal + anim.literal)
                        .onHoverText("hollowengine.tooltips.copy".mcTranslate)
                        .onClickCopy(anim)
                )
            }
        }
    }

}