package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues

import dev.ftb.mods.ftbteams.data.Team
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.PacketDistributor
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.scripting.ownerPlayer
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.NodeContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.deserializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events.ClickNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.serializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.NPCProperty

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class DialogueScreenPacket(private val enable: Boolean) : HollowPacketV3<DialogueScreenPacket> {
    override fun handle(player: Player, data: DialogueScreenPacket) {
        DialogueScreen.cleanup()
        if (data.enable) DialogueScreen.open()
        else DialogueScreen.onClose()
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class DialogueSayPacket(
    var text: String = "", var name: String = "", val entity: Int
) : HollowPacketV3<DialogueSayPacket> {
    override fun handle(player: Player, data: DialogueSayPacket) {
        DialogueScreen.updateText(data.text)
        DialogueScreen.updateName(data.name.mcText)
        DialogueScreen.addEntity(data.entity)
    }

}

@HollowPacketV2(HollowPacketV2.Direction.TO_CLIENT)
@Serializable
class DialogueChoicePacket(val choices: List<String>) : HollowPacketV3<DialogueChoicePacket> {
    override fun handle(player: Player, data: DialogueChoicePacket) {
        DialogueScreen.updateChoices(data.choices.map { it.mcText })
    }

}

class DialogueNode(val nodes: List<Node>) : Node(), HasInnerNodes {
    private var index = 0
    val isEnded get() = index >= nodes.size
    var isStarted = false
    override val currentNode get() = nodes[index]

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            onStart()
        }

        if (!currentNode.tick()) index++

        if (isEnded) {
            onEnd()
            return false
        }

        return true
    }

    private fun onStart() {
        manager.team.onlineMembers.forEach {
            DialogueScreenPacket(true).send(PacketDistributor.PLAYER.with { it })
        }
    }

    private fun onEnd() {
        manager.team.onlineMembers.forEach {
            DialogueScreenPacket(false).send(PacketDistributor.PLAYER.with { it })
        }
    }

    override fun serializeNBT() = CompoundTag().apply {
        putInt("index", index)
        serializeNodes("nodes", nodes)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        index = nbt.getInt("index")
        nbt.deserializeNodes("nodes", nodes)
    }
}

class DialogueContext(stateMachine: StoryStateMachine) : NodeContextBuilder(stateMachine) {

    override fun NPCProperty.say(text: () -> String): SimpleNode {
        val result = +SimpleNode {
            val npc = this@say()
            manager.team.onlineMembers.forEach {
                DialogueSayPacket(text(), npc.displayName.string, npc.id)
                    .send(PacketDistributor.PLAYER.with { it })
            }
        }
        +ClickNode(MouseButton.LEFT)

        return result
    }

    override fun Team.send(text: () -> String): SimpleNode {
        val result = +SimpleNode {
            manager.team.onlineMembers.forEach {
                DialogueSayPacket(text(), this@send.name.string, this@send.ownerPlayer?.id ?: -1)
                    .send(PacketDistributor.PLAYER.with { it })
            }
        }
        +ClickNode(MouseButton.LEFT)

        return result
    }

    fun send(body: DialogueSayPacket.() -> Unit) {
        +SimpleNode {
            manager.team.onlineMembers.forEach {
                DialogueSayPacket("hollowengine.no_text_dialogue", "", -1).apply(body)
                    .send(PacketDistributor.PLAYER.with { it })
            }
        }
    }

    fun choice(context: DialogueChoiceContext.() -> Unit) =
        +ChoicesNode(DialogueChoiceContext(stateMachine).apply(context))
}

class ApplyChoiceEvent(val player: Player, val choice: Int) : Event()

class ChoicesNode(choiceContext: DialogueChoiceContext) : Node(), HasInnerNodes {
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

        if ((performedChoice?.size ?: 0) > 0) {
            if (!performedChoice!![index].tick()) index++
        }

        return !isEnded
    }

    private fun onTimeoutEnd() {
        TODO("Not yet implemented")
    }

    @SubscribeEvent
    fun onChoice(event: ApplyChoiceEvent) {
        if (manager.team.isMember(event.player.uuid)) {
            performedChoice = choices.values.filterIndexed { index, _ -> index == event.choice }.firstOrNull()
            performedChoiceIndex = event.choice
            MinecraftForge.EVENT_BUS.unregister(this)
            index = 0
        }
    }

    private fun performChoice() {
        manager.team.onlineMembers.forEach {
            DialogueChoicePacket(choices.keys.map { it.string })
                .send(PacketDistributor.PLAYER.with { it })
        }
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

class DialogueChoiceContext(val stateMachine: StoryStateMachine) {
    val choices = LinkedHashMap<Component, List<Node>>()
    var timeout = 0
    var onTimeout = {
        choices.values.firstOrNull()
    }

    fun onTimeout(action: DialogueContext.() -> Unit) {
        onTimeout = { DialogueContext(stateMachine).apply(action).tasks }
    }

    fun addChoice(text: Component, tasks: DialogueContext.() -> Unit) {
        choices[text] = DialogueContext(stateMachine).apply(tasks).tasks
    }

    operator fun String.invoke(tasks: DialogueContext.() -> Unit) {
        choices[this.mcText] = DialogueContext(stateMachine).apply(tasks).tasks
    }
}

fun IContextBuilder.dialogue(nodes: DialogueContext.() -> Unit) =
    +DialogueNode(DialogueContext(this.stateMachine).apply(nodes).tasks)
