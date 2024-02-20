@file:Suppress("INAPPLICABLE_JVM_NAME")

package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.dialogues

import dev.ftb.mods.ftbteams.data.Team
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
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.screen.CLIENT_OPTIONS
import ru.hollowhorizon.hollowengine.client.screen.DefaultOptions
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.ServerMouseClickedEvent
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability
import ru.hollowhorizon.hollowengine.common.npcs.NpcIcon
import ru.hollowhorizon.hollowengine.common.scripting.forEachPlayer
import ru.hollowhorizon.hollowengine.common.scripting.ownerPlayer
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryStateMachine
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.HasInnerNodes
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.IContextBuilder
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.*
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.events.ClickNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs.NPCProperty
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.players.PlayerProperty

var SERVER_OPTIONS = DefaultOptions()

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
class UpdateDialoguePacket(val options: DefaultOptions = SERVER_OPTIONS) : HollowPacketV3<UpdateDialoguePacket> {
    override fun handle(player: Player, data: UpdateDialoguePacket) {
        CLIENT_OPTIONS = options
    }

}

var FORCE_CLOSE = false

class DialogueNode(val nodes: List<Node>, val npc: NPCProperty? = null) : Node(), HasInnerNodes {
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

        if (FORCE_CLOSE) {
            FORCE_CLOSE = false

            npc?.let {
                val entity = it()
                entity[NPCCapability::class].icon = NpcIcon.EMPTY
                entity.onInteract = {}
            }
        }

        return true
    }

    private fun onStart() {
        npc?.let {
            val entity = it()
            entity[NPCCapability::class].icon = NpcIcon.DIALOGUE
            entity.onInteract = {
                if (it is ServerPlayer && it in manager.team.onlineMembers) {
                    DialogueScreenPacket(true, canClose = true).send(PacketDistributor.PLAYER.with { it })
                }
                MinecraftForge.EVENT_BUS.post(ServerMouseClickedEvent(it, MouseButton.LEFT))
            }
        }
        if (npc == null) {
            manager.team.onlineMembers.forEach {
                DialogueScreenPacket(true, canClose = false).send(PacketDistributor.PLAYER.with { it })
            }
        }
    }

    private fun onEnd() {
        manager.team.onlineMembers.forEach {
            DialogueScreenPacket(false, npc != null).send(PacketDistributor.PLAYER.with { it })
        }
        npc?.let {
            val entity = it()
            entity[NPCCapability::class].icon = NpcIcon.EMPTY
            entity.onInteract = {}
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

class DialogueContext(val action: ChoiceAction, stateMachine: StoryStateMachine) : NodeContextBuilder(stateMachine) {
    var dialogueNpc: NPCProperty? = null

    override fun NPCProperty.say(text: () -> String): SimpleNode {
        if (action == ChoiceAction.WORLD) {
            wait { (text().length / 14 + 15).sec }
            return next {
                val component =
                    Component.literal("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
                stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(component) }
            }
        } else {
            val result = +SimpleNode {
                val npc = this@say()
                SERVER_OPTIONS.update(manager.team) {
                    this.text = text().mcTranslate
                    this.name = npc.displayName
                    if(npc !in characters) characters.add(npc)
                }
            }
            +ClickNode(MouseButton.LEFT)

            return result
        }
    }

    fun options(options: DefaultOptions.() -> Unit) = next {
        SERVER_OPTIONS.update(manager.team, options)
    }

    @JvmName("playerSay")
    override fun PlayerProperty.say(text: () -> String): SimpleNode {
        if (action == ChoiceAction.WORLD) {
            wait { (text().length / 14 + 15).sec }
            return next {
                val component =
                    Component.literal("§6[§7" + this@say().displayName.string + "§6]§7 ").append(text().mcTranslate)
                stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(component) }
            }
        } else {
            val result = +SimpleNode {
                val player = this@say()
                SERVER_OPTIONS.update(manager.team) {
                    this.text = text().mcTranslate
                    this.name = player.displayName
                    if(player !in characters) characters.add(player)
                }
            }
            +ClickNode(MouseButton.LEFT)

            return result
        }
    }

    override fun Team.send(text: () -> String): SimpleNode {
        if (action == ChoiceAction.WORLD) {
            wait { (text().length / 14 + 15).sec }
            return next {
                stateMachine.team.onlineMembers.forEach { it.sendSystemMessage(text().mcTranslate) }
            }
        } else {
            val result = +SimpleNode {
                SERVER_OPTIONS.update(manager.team) {
                    this.text = text().mcTranslate
                    ownerPlayer?.let {
                        this.name = it.name
                        if(it !in characters) characters.add(it)
                    }
                }
            }
            +ClickNode(MouseButton.LEFT)

            return result
        }
    }

    fun choice(action: ChoiceAction = ChoiceAction.SCREEN, context: DialogueChoiceContext.() -> Unit) =
        +ChoicesNode(action, DialogueChoiceContext(action, stateMachine).apply(context))

}

private fun DefaultOptions.update(team: Team, function: DefaultOptions.() -> Unit) {
    this.function()
    team.forEachPlayer { UpdateDialoguePacket(this).send(PacketDistributor.PLAYER.with { it }) }
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
        TODO("Not yet implemented")
    }

    @SubscribeEvent
    fun onChoice(event: ApplyChoiceEvent) {
        if (manager.team.isMember(event.player.uuid)) {
            performedChoice = choices.values.filterIndexed { index, _ -> index == event.choice }.firstOrNull()
            performedChoiceIndex = event.choice
            MinecraftForge.EVENT_BUS.unregister(this)
            index = 0

            if (action == ChoiceAction.WORLD) {
                manager.team.onlineMembers.forEach {
                    DialogueScreenPacket(false, true).send(PacketDistributor.PLAYER.with { it })
                }
                FORCE_CLOSE = true
            }
        }
    }

    private fun performChoice() {
        SERVER_OPTIONS.update(manager.team) {
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
