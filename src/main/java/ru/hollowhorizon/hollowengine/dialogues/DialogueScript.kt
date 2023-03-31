package ru.hollowhorizon.hollowengine.dialogues


import ru.hollowhorizon.hc.common.scripting.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hollowengine.client.screen.DialogueScreen
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*

@KotlinScript(
    displayName = "Hollow Dialogue Script",
    fileExtension = "hds",
    compilationConfiguration = HollowDialogueConfiguration::class
)
abstract class HDialogue(screen: DialogueScreen, player: HDCharacter) : DialogueScriptBase(screen, player) {

}

class HollowDialogueConfiguration : AbstractHollowScriptConfiguration({
    defaultImports("ru.hollowhorizon.hollowengine.dialogues.*")
    baseClass(DialogueScriptBase::class)
})