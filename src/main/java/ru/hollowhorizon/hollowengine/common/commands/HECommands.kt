package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.register
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.substringAfterHE
import ru.hollowhorizon.hollowengine.common.network.CopyTextPacket
import ru.hollowhorizon.hollowengine.common.network.ShowModelInfoPacket

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
                            "item($location, $count, \"${
                                nbt.toString()
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

    DirectoryManager.HOLLOW_ENGINE.resolve("assets/").walkTopDown().mapNotNull {
        val path = it.substringAfterHE("assets").replaceFirst("/", ":")

        if (path.endsWith(".gltf")) path
        else null
    }

    return list
}