package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.margin
import de.fabmax.kool.modules.ui2.size
import de.fabmax.kool.modules.ui2.tint
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.kool.Item
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.utils.nbt.ForItemStack
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.fsm.StateNode
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.await

class Dialogue(private val targetNode: StateNode, vararg val players: ServerPlayer) {
    val scene = DialogueScene()

    suspend infix fun NpcEntity.say(text: String) {
        scene.character = name
        scene.characters.add(this)
        scene.text = text
        scene.sync(*players)
        await<DialogueUpdateEvent>()
    }

    suspend infix fun Player.say(text: String) {
        scene.character = name.string
        scene.characters.add(this)
        scene.text = text
        scene.sync(*players)
        await<DialogueUpdateEvent>()
    }

    suspend fun choices(vararg options: String) {
        scene.choices.clear()
        scene.choices.addAll(options.map { DialogChoice.simple(it) })
        scene.sync(*players)
        var choice: Int
        do {
            choice = await<DialogueUpdateEvent>().tag.let { if (it.contains("choiceId")) it.getInt("choiceId") else -1 }
        } while (choice < 0)
        scene.choices.clear()
        targetNode.transition(options[choice])
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class DialogueUpdateEvent(val tag: @Serializable(ForCompoundNBT::class) CompoundTag) : HollowPacket, Event {
    override fun handle(player: Player) {
        EventBus.post(this)
    }
}


fun StateNode.dialogue(vararg player: ServerPlayer, action: suspend Dialogue.() -> Unit) {
    val dialogue = Dialogue(this, *player)

    state("main") {
        action(dialogue)
    }
}

interface DialogChoice {
    val content: String
    fun UiScope.icon(scale: Float, progress: Float)

    companion object {
        fun simple(text: String, icon: String = "hollowengine:textures/gui/dialogues/simple.png") =
            ChoiceIcon(text, icon)

        fun item(text: String, item: ItemStack) = ChoiceItem(text, item)
    }
}

@Serializable
@Polymorphic(DialogChoice::class)
class ChoiceIcon(override val content: String, val icon: String) : DialogChoice {
    override fun UiScope.icon(scale: Float, progress: Float) {
        Image(icon) {
            modifier.size(18.dp * scale, 18.dp * scale)
                .margin(top = 4.dp * scale, start = 2.dp * scale)
                .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
        }
    }
}

@Serializable
@Polymorphic(DialogChoice::class)
class ChoiceItem(override val content: String, val item: @Serializable(ForItemStack::class) ItemStack) : DialogChoice {
    override fun UiScope.icon(scale: Float, progress: Float) {
        Item(item) {
            modifier.size(18.dp * scale, 18.dp * scale)
                .margin(top = 4.dp * scale, start = 2.dp * scale)
                .tint(Color(1f, 1f, 1f, Interpolation.QUAD_IN(progress)))
        }
    }
}