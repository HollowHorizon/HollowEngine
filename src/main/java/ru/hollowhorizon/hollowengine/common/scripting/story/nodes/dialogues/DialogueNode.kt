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

@file:Suppress("INAPPLICABLE_JVM_NAME")

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.screen.CLIENT_OPTIONS
import ru.hollowhorizon.hollowengine.client.screen.DialogueOptions
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.client.screen.overlays.DrawMousePacket
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.ServerMouseClickedEvent
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events.ClickNode
import ru.hollowhorizon.hollowengine.common.util.Safe

var SERVER_OPTIONS = DialogueOptions()

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class DialogueScreenPacket(private val enable: Boolean, private val canClose: Boolean) :
    HollowPacketV3<DialogueScreenPacket> {
    override fun handle(player: Player, data: DialogueScreenPacket) {
        DialogueScreen.cleanup()
        DialogueScreen.canClose = data.canClose
        if (data.enable) DialogueScreen.open()
        else DialogueScreen.onClose()
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class UpdateDialoguePacket(private val options: DialogueOptions = SERVER_OPTIONS) :
    HollowPacketV3<UpdateDialoguePacket> {
    override fun handle(player: Player, data: UpdateDialoguePacket) {
        CLIENT_OPTIONS = options
    }

}

var FORCE_CLOSE = false

class DialogueNode(val nodes: List<Node>, val npc: Safe<NPCEntity>? = null) : Node(), HasInnerNodes {
    private var index = 0
    val isEnded get() = index >= nodes.size
    var isStarted = false
    override val currentNode get() = nodes[index]

    override fun tick(): Boolean {
        if (!isStarted) {
            if (onStart()) isStarted = true
            return true
        }

        if (!currentNode.tick()) index++

        if (isEnded) {
            return onEnd()
        }

        if (FORCE_CLOSE && npc?.isLoaded == true) {
            FORCE_CLOSE = false

            npc.let {
                val entity = it()
                entity[NPCCapability::class].icon = NpcIcon.EMPTY
                DrawMousePacket(
                    enable = false
                ).send(*manager.server.playerList.players.toTypedArray())
                entity.onInteract = NPCEntity.EMPTY_INTERACT
            }
        }

        return true
    }

    private fun onStart(): Boolean {
        if (npc?.isLoaded == false) return false

        SERVER_OPTIONS = DialogueOptions()
        npc?.let {
            val entity = it()
            entity[NPCCapability::class].icon = NpcIcon.DIALOGUE
            DrawMousePacket(enable = true).send(*manager.server.playerList.players.toTypedArray())
            entity.onInteract = {
                if (it is ServerPlayer && it in manager.server.playerList.players) {
                    DialogueScreenPacket(true, canClose = true).send(PacketDistributor.PLAYER.with { it })
                }
                MinecraftForge.EVENT_BUS.post(ServerMouseClickedEvent(it, MouseButton.LEFT))
            }
        }
        if (npc == null) {
            manager.server.playerList.players.forEach {
                DialogueScreenPacket(true, canClose = false).send(PacketDistributor.PLAYER.with { it })
            }
        }
        return true
    }

    private fun onEnd(): Boolean {
        manager.server.playerList.players.forEach {
            DialogueScreenPacket(false, npc != null).send(PacketDistributor.PLAYER.with { it })
        }

        //Если нпс ещё не прогрузился, то ждём
        if (npc?.isLoaded == false) return true
        npc?.let {
            val entity = it()
            entity[NPCCapability::class].icon = NpcIcon.EMPTY
            DrawMousePacket(enable = false).send(*manager.server.playerList.players.toTypedArray())
            entity.onInteract = NPCEntity.EMPTY_INTERACT
        }
        return false
    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("index", index)
        serializeNodes("nodes", nodes)
        putBoolean("started", isStarted)
        putBoolean("ended", isEnded)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        index = nbt.getInt("index")
        nbt.deserializeNodes("nodes", nodes)
        isStarted = nbt.getBoolean("started")
        isStarted = nbt.getBoolean("ended")
    }
}

class DialogueContext(val action: ChoiceAction, stateMachine: StoryStateMachine) : NodeContextBuilder(stateMachine) {
    var dialogueNpc: Safe<NPCEntity>? = null

    override fun Safe<NPCEntity>.sayComponent(text: () -> Component): SimpleNode {
        if (action == ChoiceAction.WORLD) {
            return next {
                val component =
                    Component.literal("§6[§7" + this@sayComponent().displayName.string + "§6]§7 ").append(text())
                stateMachine.server.playerList.players.forEach { it.sendSystemMessage(component) }
            }
        } else {
            +ClickNode(MouseButton.LEFT)

            return next {
                val npc = this@sayComponent()
                SERVER_OPTIONS.update(manager.server.playerList.players) {
                    this.text = text()
                    this.name = npc.displayName
                    if (npc !in characters) characters.add(npc)
                }
            }
        }
    }

    fun options(options: DialogueOptions.() -> Unit) = next {
        SERVER_OPTIONS.update(manager.server.playerList.players, options)
    }

    @JvmName("playerSayComponent")
    override fun Safe<List<ServerPlayer>>.sayComponent(text: () -> Component): SimpleNode {
        if (action == ChoiceAction.WORLD) {
            return next {
                this@sayComponent().forEach {
                    val component =
                        Component.literal("§6[§7" + it.displayName.string + "§6]§7 ").append(text())
                    it.sendSystemMessage(component)
                }
            }
        } else {
            val result = +SimpleNode {
                val player = this@sayComponent().random()
                SERVER_OPTIONS.update(manager.server.playerList.players) {
                    this.text = text()
                    this.name = player.displayName
                    if (player !in characters) characters.add(player)
                }
            }
            +ClickNode(MouseButton.LEFT)

            return result
        }
    }

    fun choice(action: ChoiceAction = ChoiceAction.SCREEN, context: DialogueChoiceContext.() -> Unit) =
        +ChoicesNode(action, DialogueChoiceContext(action, stateMachine).apply(context))

}

fun DialogueOptions.update(players: List<ServerPlayer>, function: DialogueOptions.() -> Unit) {
    this.function()
    players.forEach { UpdateDialoguePacket(this).send(PacketDistributor.PLAYER.with { it }) }
}

enum class ChoiceAction {
    SCREEN, WORLD
}

class ApplyChoiceEvent(val player: Player, val choice: Int) : Event()

class ChoicesNode(val action: ChoiceAction, choiceContext: DialogueChoiceContext) : Node(), HasInnerNodes {
    val choices = choiceContext.choices
    var timeout = choiceContext.timeout
    var onTimeout = choiceContext.onTimeout
    var index = 0
    var performedChoice: List<Node>? = null
    var performedChoiceIndex = 0
    var isStarted = false
    val isEnded get() = performedChoice != null && index >= (performedChoice?.size ?: 0)
    override val currentNode get() = performedChoice?.get(index) ?: SimpleNode {}

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            MinecraftForge.EVENT_BUS.register(this)
            performChoice()
        } else if (timeout > 0) {
            timeout--

            if (timeout == 0 && performedChoice == null) {
                onTimeoutEnd()
            }
        }

        if ((performedChoice?.size ?: 0) > 0 && !performedChoice!![index].tick()) index++

        return !isEnded
    }

    private fun onTimeoutEnd() {
        TODO("Реализовать таймаут в диалогах")
    }

    @SubscribeEvent
    fun onChoice(event: ApplyChoiceEvent) {
        performedChoice = choices.values.filterIndexed { index, _ -> index == event.choice }.firstOrNull()
        performedChoiceIndex = event.choice
        MinecraftForge.EVENT_BUS.unregister(this)
        index = 0

        if (action == ChoiceAction.WORLD) {
            manager.server.playerList.players.forEach {
                DialogueScreenPacket(false, true).send(PacketDistributor.PLAYER.with { it })
            }
            FORCE_CLOSE = true
        }

    }

    private fun performChoice() {
        SERVER_OPTIONS.update(manager.server.playerList.players) {
            this.choices.clear()
            this.choices.addAll(this@ChoicesNode.choices.keys.map { it.string })
        }
        SERVER_OPTIONS.choices.clear() //без этого кнопки останутся после выбора
    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("index", index)
        putInt("timeout", timeout)
        putInt("pindex", performedChoiceIndex)
        if (performedChoice != null) serializeNodes("performedChoice", performedChoice!!)

    }

    override fun deserializeNBT(nbt: CompoundTag) {
        index = nbt.getInt("index")
        timeout = nbt.getInt("timeout")
        performedChoiceIndex = nbt.getInt("pindex")
        if (nbt.contains("performedChoice")) {
            performedChoice = choices.values.filterIndexed { index, _ -> index == performedChoiceIndex }.firstOrNull()
            if (performedChoice != null) nbt.deserializeNodes("performedChoice", performedChoice!!)
        }

    }

}

class DialogueChoiceContext(val action: ChoiceAction, val stateMachine: StoryStateMachine) {
    val choices = LinkedHashMap<Component, List<Node>>()
    var timeout = 0
    var onTimeout = {
        choices.values.firstOrNull()
    }

    fun onTimeout(builder: DialogueContext.() -> Unit) {
        onTimeout = { DialogueContext(action, stateMachine).apply(builder).tasks }
    }

    fun addChoice(text: Component, builder: DialogueContext.() -> Unit) {
        choices[text] = DialogueContext(action, stateMachine).apply(builder).tasks
    }

    operator fun String.invoke(builder: DialogueContext.() -> Unit) {
        choices[this.mcText] = DialogueContext(action, stateMachine).apply(builder).tasks
    }
}

fun IContextBuilder.dialogue(builder: DialogueContext.() -> Unit) {
    val ctx = DialogueContext(ChoiceAction.SCREEN, this.stateMachine).apply(builder)
    +DialogueNode(ctx.tasks, ctx.dialogueNpc)
}
