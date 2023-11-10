package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues

import dev.ftb.mods.ftbteams.api.Team
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.network.NetworkDirection
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.Packet
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.NodeContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.deserializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events.ClickNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.serializeNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.NPCProperty

@HollowPacketV2(NetworkDirection.PLAY_TO_CLIENT)
class DialogueScreenPacket : Packet<Boolean>({ player, value ->
    DialogueScreen.cleanup()
    if (value) DialogueScreen.open()
    else DialogueScreen.onClose()
})

@Serializable
class SayContainer(var text: String = "", var name: String = "", val entity: Int)

@HollowPacketV2(NetworkDirection.PLAY_TO_CLIENT)
class DialogueSayPacket : Packet<SayContainer>({ player, value ->
    DialogueScreen.updateText(value.text)
    DialogueScreen.updateName(value.name.mcText)
    DialogueScreen.addEntity(value.entity)
})

@Serializable
class ChoicesContainer(val choices: List<String>)

@HollowPacketV2(NetworkDirection.PLAY_TO_CLIENT)
class DialogueChoicePacket : Packet<ChoicesContainer>({ player, value ->
    DialogueScreen.updateChoices(value.choices.map { it.mcText })
})

class DialogueNode(val nodes: List<Node>) : Node() {
    private var index = 0
    val isEnded get() = index >= nodes.size
    var isStarted = false

    override fun tick(): Boolean {
        if (!isStarted) {
            isStarted = true
            onStart()
        }

        if (!nodes[index].tick()) index++

        if (isEnded) {
            onEnd()
            return false
        }

        return true
    }

    private fun onStart() {
        DialogueScreenPacket().send(true, *manager.team.onlineMembers.toTypedArray())
    }

    private fun onEnd() {
        DialogueScreenPacket().send(false, *manager.team.onlineMembers.toTypedArray())
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

    override fun NPCProperty.say(text: IContextBuilder.TextContainer.() -> Unit): SimpleNode {
        val container = IContextBuilder.TextContainer().apply(text)

        val result = +SimpleNode {
            val npc = this@say()
            DialogueSayPacket().send(
                SayContainer(container.text, npc.displayName.string, npc.id),
                *manager.team.onlineMembers.toTypedArray()
            )
        }
        +ClickNode(MouseButton.LEFT)

        return result
    }

    override fun Team.send(text: IContextBuilder.TextContainer.() -> Unit): SimpleNode {
        val container = IContextBuilder.TextContainer().apply(text)

        val result = +SimpleNode {
            DialogueSayPacket().send(
                SayContainer(container.text, this@send.name.string, this@send.onlineMembers.find { it.uuid == this@send.owner }?.id ?: -1),
                *manager.team.onlineMembers.toTypedArray()
            )
        }
        +ClickNode(MouseButton.LEFT)

        return result
    }

    fun send(body: SayContainer.() -> Unit) {
        +SimpleNode {
            val container = SayContainer("hollowengine.no_text_dialogue", "", 0).apply(body)
            DialogueSayPacket().send(container, *manager.team.onlineMembers.toTypedArray())
        }
    }

    fun choice(context: DialogueChoiceContext.() -> Unit) =
        +ChoicesNode(DialogueChoiceContext(stateMachine).apply(context))
}

class ApplyChoiceEvent(val player: Player, val choice: Int) : Event()

class ChoicesNode(choiceContext: DialogueChoiceContext) : Node() {
    val choices = choiceContext.choices
    var timeout = choiceContext.timeout
    var onTimeout = choiceContext.onTimeout
    var index = 0
    var performedChoice: List<Node>? = null
    var performedChoiceIndex = 0
    var isStarted = false
    val isEnded get() = performedChoice != null && index >= (performedChoice?.size ?: 0)

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

        if (performedChoice != null) {
            if (!performedChoice!![index].tick()) index++
        }

        return !isEnded
    }

    private fun onTimeoutEnd() {
        TODO("Not yet implemented")
    }

    @SubscribeEvent
    fun onChoice(event: ApplyChoiceEvent) {
        if (manager.team.members.contains(event.player.uuid)) {
            performedChoice = choices.values.filterIndexed { index, _ -> index == event.choice }.firstOrNull()
            performedChoiceIndex = event.choice
            MinecraftForge.EVENT_BUS.unregister(this)
            index = 0
        }
    }

    private fun performChoice() {
        DialogueChoicePacket().send(
            ChoicesContainer(choices.keys.map { it.string }),
            *manager.team.onlineMembers.toTypedArray()
        )
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
    val choices = HashMap<Component, List<Node>>()
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
