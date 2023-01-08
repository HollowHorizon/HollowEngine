package ru.hollowhorizon.hollowstory.dialogues


import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hollowstory.client.gui.DialogueScreen
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

@KotlinScript(
    displayName = "Hollow Dialogue Script",
    fileExtension = "hds",
    compilationConfiguration = HDialogueConfiguration::class
)
abstract class HDialogue(screen: DialogueScreen, player: HDCharacter) : DialogueScriptBase(screen, player) {
    init {
        val crazy = HDCharacter("minecraft:skeleton", "Крейзи") //моб как в /summon
        val chopov = HDCharacter("minecraft:villager", "Чопов")
        val frakdgo = HDCharacter("minecraft:piglin", "Фракджо")

        player say "Так, сразу говорю, у меня нет моделек, так что использую ванильные)"

        crazy say "Ну здравствуй, спящая красавица. Мы давно тебя поджидаем."

        choice(
            "Учтите, что вы на частной территории, и ваш визит противоречит статье 35…" to {
                crazy say "Частной? Рассмешил."
                chopov say "А я  думал, статья 35 это…"
                frakdgo say "Смотри голову не потеряй со своими статейками."

            },
            "Кто вы такие и что здесь делаете?" to {

            },
        )
    }
}

class HDialogueConfiguration : ScriptCompilationConfiguration({
    defaultImports("ru.hollowhorizon.hollowstory.dialogues.*")

    jvm {
        dependenciesFromClassContext(HDialogueConfiguration::class, wholeClasspath = true)

        val files = ArrayList<File>()
        if (FMLLoader.isProduction()) {
            files.addAll(ModList.get().modFiles.map { it.file.filePath.toFile() })
            files.add(FMLLoader.getForgePath().toFile())
            files.addAll(FMLLoader.getMCPaths().map { it.toFile() })
        }

        updateClasspath(files)

        compilerOptions(
            "-opt-in=kotlin.time.ExperimentalTime,kotlin.ExperimentalStdlibApi,kotlinx.coroutines.ExperimentalCoroutinesApi-Xextended-compiler-checks",
            "-jvm-target", "1.8",
        )
    }

    baseClass(DialogueScriptBase::class)

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }

})