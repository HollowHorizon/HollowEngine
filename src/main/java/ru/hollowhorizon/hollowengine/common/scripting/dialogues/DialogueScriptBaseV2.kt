package ru.hollowhorizon.hollowengine.common.scripting.dialogues

import ru.hollowhorizon.hollowengine.common.npcs.ICharacter
import ru.hollowhorizon.hollowengine.common.scripting.dialogues.actions.*

open class DialogueScriptBaseV2(val manager: IDialogueExecutor) {
    val scene = DialogueScene()

    infix fun ICharacter.say(text: String): ICharacter {
        if (scene.autoSwitch) {
            if (this !in scene.characters) {
                scene.actions += AddCharacterAction(this)
                scene.characters += this
            }
            scene.actions += FocusCharacterAction(this)
        }
        scene.actions += UpdateTextAction(this.characterName, text)

        manager.updateScene(scene)

        manager.waitAction()

        return this
    }

    infix fun ICharacter.play(sound: String): ICharacter {
        play(sound)
        return this
    }

    operator fun ICharacter.invoke(text: String) = say(text)
    operator fun ICharacter.compareTo(text: String): Int {
        say(text)
        return 0
    }

    fun say(character: String?, text: String) {
        scene.actions += UpdateTextAction(character ?: "", text)
        manager.waitAction()
    }

    fun play(sound: String) {
        scene.actions += PlaySoundAction(sound)
    }

    fun HDCharacter.say(text: String, time: Float) {
        scene.actions += UpdateTextAction(this.characterName, text)

        if (scene.autoSwitch) {
            if (this !in scene.characters) {
                scene.actions += AddCharacterAction(this)
                scene.characters += this
            }
            scene.actions += FocusCharacterAction(this)
        }

        manager.updateScene(scene)

        wait(time)
    }

    fun wait(time: Float) = delay(time)
    fun delay(time: Float) = Thread.sleep((time * 1000).toLong())

    fun choice(pairs: ChoiceContext.() -> Unit) {
        val context = ChoiceContext()
        pairs(context)
        val r = manager.applyChoice(context.choices.map { it.first })
        context.choices[r].second()
    }

    fun stop() {
        manager.stop()
    }

    class ChoiceContext {
        val choices = arrayListOf<Pair<String, () -> Unit>>()
        operator fun String.invoke(action: () -> Unit) {
            choices.add(this to action)
        }

        fun timeout(time: Float, action: () -> Unit) {
            TODO("Реализовать функционал тайм-аутов")
        }
    }
}