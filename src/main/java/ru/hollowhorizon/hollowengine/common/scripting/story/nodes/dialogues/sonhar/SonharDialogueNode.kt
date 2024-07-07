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

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.sonhar

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.gui.dialogue.SonharDialogueGui
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.NodeContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.deserializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.serializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues.ApplyChoiceEvent
import ru.hollowhorizon.hollowengine.common.util.Safe
import kotlin.collections.set

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class SonharDialogueScreenPacket(
    var open: Boolean = true,
) : HollowPacketV3<SonharDialogueScreenPacket> {
    override fun handle(player: Player, data: SonharDialogueScreenPacket) {
        SonharDialogueGui.textIndex = 0
        if (data.open) SonharDialogueGui.open()
        else SonharDialogueGui.onClose()
    }
}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class SonharUpdateDialoguePacket(
    var text: List<String> = arrayListOf(),
    var sounds: List<String> = arrayListOf(),
    var choices: List<String> = arrayListOf(),
    var entities: List<Int> = arrayListOf(),
) : HollowPacketV3<SonharUpdateDialoguePacket> {
    override fun handle(player: Player, data: SonharUpdateDialoguePacket) {
        SonharDialogueGui.text.clear()
        SonharDialogueGui.text.addAll(text)
        SonharDialogueGui.sounds.clear()
        SonharDialogueGui.sounds.addAll(sounds)
        SonharDialogueGui.choices.clear()
        SonharDialogueGui.choices.addAll(choices)

        SonharDialogueGui.entities = entities.map {
            Minecraft.getInstance().level?.getEntity(it) as? LivingEntity
        }
    }

}


class ChoiceContext(val stateMachine: StoryStateMachine) {
    var text = arrayListOf<String>()
    var sounds = arrayListOf<String>()
    val choices = LinkedHashMap<Component, List<Node>>()
    var open: Boolean = true
    var entities = arrayListOf<Safe<*>>()

    fun Safe<*>.entry(text: String, sound: String) {
        this@ChoiceContext.text += text
        this@ChoiceContext.sounds += sound
        this@ChoiceContext.entities += this
    }

    operator fun String.invoke(tasks: NodeContextBuilder.() -> Unit) {
        choices[this.mcText] = NodeContextBuilder(stateMachine).apply(tasks).tasks
    }
}

class ChoiceScreenNode(private val choiceContext: ChoiceContext) : Node(), HasInnerNodes {
    val choices = choiceContext.choices
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
            manager.server.playerList.players.forEach {
                SonharDialogueScreenPacket(true).send(it)
                SonharUpdateDialoguePacket(
                    choiceContext.text,
                    choiceContext.sounds,
                    choiceContext.choices.map { it.key.string },
                    choiceContext.entities.map {
                        (it() as? Entity ?: (it() as List<*>).random() as Entity).id
                    }
                ).send(it)
            }
        }

        if ((performedChoice?.size ?: 0) > 0) {
            if (!performedChoice!![index].tick()) index++
        }

        return !isEnded
    }

    @SubscribeEvent
    fun onChoice(event: ApplyChoiceEvent) {
        performedChoice = choices.values.filterIndexed { index, _ -> index == event.choice }.firstOrNull()
        performedChoiceIndex = event.choice
        MinecraftForge.EVENT_BUS.unregister(this)
        manager.server.playerList.players.forEach {
            SonharDialogueScreenPacket(false).send(PacketDistributor.PLAYER.with { it })
        }
        index = 0

    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("index", index)
        putInt("pindex", performedChoiceIndex)
        if (performedChoice != null) serializeNodes("performedChoice", performedChoice!!)

    }

    override fun deserializeNBT(nbt: CompoundTag) {
        index = nbt.getInt("index")
        performedChoiceIndex = nbt.getInt("pindex")
        if (nbt.contains("performedChoice")) {
            performedChoice = choices.values.filterIndexed { index, _ -> index == performedChoiceIndex }.firstOrNull()
            if (performedChoice != null) nbt.deserializeNodes("performedChoice", performedChoice!!)
        }

    }
}

fun IContextBuilder.sonharDialogue(context: ChoiceContext.() -> Unit) =
    +ChoiceScreenNode(ChoiceContext(stateMachine).apply(context))
