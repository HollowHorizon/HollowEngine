/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.client.Minecraft
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.Style
import net.minecraftforge.network.PacketDistributor
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.commands.arg
import ru.hollowhorizon.hc.common.commands.onRegisterCommands
import ru.hollowhorizon.hollowengine.client.gui.scripting.CodeEditor
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.network.CopyTextPacket
import ru.hollowhorizon.hollowengine.common.network.ShowModelInfoPacket
import java.io.File

object HECommands {
    @JvmStatic
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.onRegisterCommands {
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
                    CopyTextPacket(itemCommand).send(PacketDistributor.PLAYER.with { player })

                    player.sendSystemMessage("hollowengine.commands.tags".mcTranslate)

                    item.tags.forEach { tag ->
                        player.sendSystemMessage(
                            Component.translatable(
                                "hollowengine.commands.copy",
                                Component.literal("tag(\"${tag.location}\")").apply {
                                    style = Style.EMPTY
                                        .withHoverEvent(
                                            HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                "hollowengine.tooltips.copy".mcTranslate
                                            )
                                        )
                                        .withClickEvent(
                                            ClickEvent(
                                                ClickEvent.Action.COPY_TO_CLIPBOARD,
                                                "tag(\"${tag.location}\")"
                                            )
                                        )
                                })
                        )
                    }
                }

                "model"(
                    arg("model", StringArgumentType.greedyString(), listModels()),
                ) {
                    val player = source.playerOrException
                    val model = StringArgumentType.getString(this, "model")

                    ShowModelInfoPacket(model).send(PacketDistributor.PLAYER.with { player })
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