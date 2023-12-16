package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraftforge.registries.ForgeRegistries
import org.jetbrains.kotlin.konan.file.File
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.register
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.CopyTextPacket
import ru.hollowhorizon.hollowengine.common.network.ShowModelInfoPacket

object HECommands {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register {
            "hollowengine" {
                "compile-all" {
                    DirectoryManager.compileAll()
                }

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

                    player.sendSystemMessage("hollowengine.commands.tags".mcTranslate)

                    item.tags.forEach { tag ->
                        player.sendSystemMessage(Component.translatable("hollowengine.commands.copy", Component.literal("tag(\"${tag.location}\")").apply {
                            style = Style.EMPTY
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, "hollowengine.tooltips.copy".mcTranslate))
                                .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "tag(\"${tag.location}\")"))
                        }))
                    }
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

fun main() {
    println(listModels())
}