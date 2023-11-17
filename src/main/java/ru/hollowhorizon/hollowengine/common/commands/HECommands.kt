package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.common.commands.register
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.common.network.CopyTextPacket

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
            }
        }
    }
}
