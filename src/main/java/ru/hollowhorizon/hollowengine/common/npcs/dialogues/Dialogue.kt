package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import kotlinx.serialization.Serializable
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hc.common.utils.nbt.ForCompoundNBT
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.await
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.npc
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.pos
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.say
import ru.hollowhorizon.hollowengine.compiler.suspendable.SuspendContext
import ru.hollowhorizon.hollowengine.scripting.Suspendable

class Dialogue {
    val scene = DialogueScene()
    val context = SuspendContext()

    @Suspendable
    infix fun NPCEntity.say(text: String) {
        scene.character = name
        scene.text = text

        await<SceneClickedEvent>()
    }

    @Suspendable
    fun choices(builder: Choices.() -> Unit) {
        val container = Choices().apply(builder)
        scene.choices.clear()
        scene.choices.addAll(container.choices.map { it.first })

        var choice = 0
        do {
            val event = await<SceneClickedEvent>()
            val hasChoice = event.tag.contains("choice")
            choice = event.tag.getInt("choice")
        } while (!hasChoice)

        container.choices[choice].second(this)
    }

    class Choices {
        internal val choices = ArrayList<Pair<String, Dialogue.() -> Unit>>()

        operator fun String.invoke(action: Dialogue.() -> Unit) {
            choices += this to action
        }
    }
}

@Serializable
class SceneClickedEvent(val tag: @Serializable(ForCompoundNBT::class) CompoundTag) : Event

@Suspendable
fun dialogue(action: Dialogue.() -> Unit) {
    val dialogue = Dialogue()
    action(dialogue)
}

@Suspendable
fun example() {
    val npc = npc(pos(1,2,3))

    npc.say("Дароу")

    dialogue {
        npc say "Привет"

        choices {
            "Хм, здрасте" {
                npc say "Ты хто?"
            }

            "Пока" {
                npc say "Ээ, куда?"
            }
        }
    }
}