package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.margin
import de.fabmax.kool.modules.ui2.size
import de.fabmax.kool.modules.ui2.tint
import de.fabmax.kool.util.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.kool.Item
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.utils.angleTo
import ru.hollowhorizon.hc.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.utils.nbt.ForItemStack
import ru.hollowhorizon.hollowengine.client.gui.scripting.CloseScreenPacket
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.await
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.*

class Dialogue(vararg val players: ServerPlayer) {
    val scene = DialogueScene()

    suspend infix fun NpcEntity.say(text: String) {
        scene.character = name
        scene.characters.add(this)
        scene.text = text
        scene.sync(*players)
        await<DialogueUpdateEvent>()
    }

    suspend fun choices(builder: suspend Choices.() -> Unit) {
        val container = Choices()
        builder.invoke(container)
        scene.choices.clear()
        scene.choices.addAll(container.choices.map { it.first })
        scene.sync(*players)
        var choice: Int
        do {
            choice = await<DialogueUpdateEvent>().tag.let { if(it.contains("choiceId")) it.getInt("choiceId") else -1 }
        } while (choice < 0)
        scene.choices.clear()
        container.choices[choice].second(this)
    }

    class Choices {
        internal val choices = ArrayList<Pair<DialogChoice, suspend Dialogue.() -> Unit>>()

        operator fun String.invoke(action: suspend Dialogue.() -> Unit) {
            choices.add(DialogChoice.simple(text = this) to action)
        }
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_SERVER)
class DialogueUpdateEvent(val tag: @Serializable(ForCompoundNBT::class) CompoundTag) : HollowPacket, Event {
    override fun handle(player: Player) {
        EventBus.post(this)
    }
}


suspend fun dialogue(player: ServerPlayer, action: suspend Dialogue.() -> Unit) {
    val dialogue = Dialogue(player)
    action(dialogue)
    CloseScreenPacket().send(player)
}

suspend fun ServerPlayer.exampleDialog(pos: Vec3) {
    val npc = npc(name="Виталик", pos=pos)
    val player = this

    npc move player

    dialogue(this) {
        npc say "Ах, вот и ты..."
        npc say "Шаги твои слышал издалека, словно эхо давно минувших дней."

        choices {
            "Привет. Ты кто вообще такой?" {
                npc say "Некоторые зовут меня рассказчиком. Другие — безумцем."
                npc say "А ты? Ты кем себя считаешь, странник?"

                choices {
                    "Я герой!" {
                        npc say "Ха! Герой — это бремя, а не титул. Надеюсь, ты готов его нести."
                    }
                    "Просто человек." {
                        npc say "Смирение — редкий дар. Но в простоте кроется сила."
                    }
                    "Я ищу ответы." {
                        npc say "Вопросов больше, чем звёзд на небе. Но некоторые ответы лучше оставить спящими."
                    }
                }
            }

            "Что ты тут делаешь один?" {
                npc say "Жду. Возможно, тебя. Или кого-то ещё."
                npc say "Ты веришь в предначертанность, ${player.name}?"

                choices {
                    "Нет. Судьбу творим мы сами." {
                        npc say "Смелые слова. Пусть они станут компасом в буре."
                    }
                    "Да. Всё предрешено." {
                        npc say "Тогда остаётся лишь сыграть свою роль... до конца."
                    }
                    "Я не знаю." {
                        npc say "Не знать — это начало мудрости. Возможно, ты дойдёшь дальше всех."
                    }
                    "Э-э, старикан, ты откуда моё имя знаешь?!" {}
                }
            }

            "У меня нет времени. Прощай." {
                npc say "Время — иллюзия. Но иди, если зовёт тебя дорога."
                npc say "*Старик тихо вздыхает и исчезает в тени.*"
            }
        }
    }

    while(player angleTo npc in -90f .. 90f) {
        delay(50)
    }
    npc.despawn()
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