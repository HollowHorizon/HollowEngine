package ru.hollowhorizon.hollowstory.story

import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hollowstory.dialogues.DialogueScriptBase
import ru.hollowhorizon.hollowstory.dialogues.HDialogueConfiguration
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromClassContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

@KotlinScript(
    displayName = "Story Script",
    fileExtension = "se.kts",
    compilationConfiguration = StoryScriptConfiguration::class
)
abstract class StoryScript(team: StoryTeam, variables: StoryVariables, name: String) : StoryEvent(team, variables, name)

class StoryScriptConfiguration : ScriptCompilationConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowstory.story.*",
        "net.minecraftforge.event.*",
        "ru.hollowhorizon.hc.client.utils.toRL",
        "ru.hollowhorizon.hollowstory.common.npcs.*"
    )

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

    baseClass(StoryEvent::class)

    ide {
        acceptedLocations(ScriptAcceptedLocation.Everywhere)
    }
})