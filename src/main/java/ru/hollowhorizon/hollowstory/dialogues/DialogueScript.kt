package ru.hollowhorizon.hollowstory.dialogues


import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.common.scripting.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hollowstory.client.screen.DialogueScreen
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

@KotlinScript(
    displayName = "Hollow Dialogue Script",
    fileExtension = "hds",
    compilationConfiguration = HollowDialogueConfiguration::class
)
abstract class HDialogue(screen: DialogueScreen, player: HDCharacter) : DialogueScriptBase(screen, player) {

}

class HollowDialogueConfiguration : AbstractHollowScriptConfiguration({
    defaultImports("ru.hollowhorizon.hollowstory.dialogues.*")
    baseClass(DialogueScriptBase::class)
})