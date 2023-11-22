package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import dev.ftb.mods.ftbteams.FTBTeamsAPI
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.TextComponent
import net.minecraft.network.chat.TranslatableComponent
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.register
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.common.events.StoryHandler.getActiveEvents
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.getAllStoryEvents
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.CopyTextPacket
import ru.hollowhorizon.hollowengine.common.network.ShowModelInfoPacket
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript
import java.io.File
import java.util.function.Consumer

object HECommands {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register {
            "hollowengine" {
                "hand" {

                    val player = source.playerOrException
                    val item = player.mainHandItem
                    val location = "\"" + ForgeRegistries.ITEMS.getKey(item.item).toString() + "\""
                    val count = item.count
                    val nbt = if (item.hasTag()) item.getOrCreateTag() else null
                    val itemCommand = when {
                        nbt == null && count > 1 -> "item($location, $count)"
                        nbt == null && count == 1 -> "item($location)"
                        else -> {
                            "item($location, $count, \"${nbt.toString()
                                .replace("\"", "\\\"")
                            }\")"
                        }
                    }
                    CopyTextPacket().send(itemCommand, player)
                }

                "model"(
                    arg("model", StringArgumentType.greedyString(), listModels()),
                ) {
                    val player = source.playerOrException
                    val model = StringArgumentType.getString(this, "model")

                    ShowModelInfoPacket().send(model, player)
                }
            }
        }
    }
}

private fun listModels(): Collection<String> {
    val list = mutableListOf<String>()
    list += "hollowengine:models/entity/player_model.gltf"

    list += DirectoryManager.HOLLOW_ENGINE.resolve("assets").walk()
        .filter { it.path.endsWith(".gltf") || it.path.endsWith(".glb") }
        .toList()
        .map {
            it.toReadablePath().substring(7).replace(File.separator, "/").replaceFirst("/", ":")
        }

    return list
}