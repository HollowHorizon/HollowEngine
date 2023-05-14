package ru.hollowhorizon.hollowengine.dialogues

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.common.npcs.ICharacter
import ru.hollowhorizon.hollowengine.dialogues.actions.IAction

@Serializable
class DialogueScene {
    var background: String? = null
    val characters = HashSet<ICharacter>()
    val actions = HashSet<IAction>()
    var autoSwitch = true
}
