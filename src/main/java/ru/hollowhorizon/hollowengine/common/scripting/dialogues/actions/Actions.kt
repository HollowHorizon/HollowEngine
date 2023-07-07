package ru.hollowhorizon.hollowengine.common.scripting.dialogues.actions

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.log
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import ru.hollowhorizon.hollowengine.common.npcs.ICharacter

@Serializable
sealed interface IAction {
    fun call(screen: DialogueScreen)
}

@Serializable
class AddCharacterAction(val character: ICharacter) : IAction {
    override fun call(screen: DialogueScreen) {

    }

}

@Serializable
class RemoveCharacterAction(val character: ICharacter) : IAction {
    override fun call(screen: DialogueScreen) {

    }
}

@Serializable
class FocusCharacterAction(val character: ICharacter) : IAction {
    override fun call(screen: DialogueScreen) {

    }
}

@Serializable
class UpdateTextAction(val character: String, val text: String): IAction {
    override fun call(screen: DialogueScreen) {
        "<$character>: $text".log().info()
        screen.textBox?.text = text
        screen.currentName = character.mcText
    }

}

@Serializable
class PlaySoundAction(val sound: String): IAction {
    override fun call(screen: DialogueScreen) {

    }

}