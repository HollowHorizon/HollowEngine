package ru.hollowhorizon.hollowengine.common.scripting.dialogues

import net.minecraft.world.entity.player.Player
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class DialogueExecutorThread(val player: Player, val file: File) : Thread() {
    override fun run() {

        try {
            if (FMLLoader.isProduction()) System.setProperty(
                "kotlin.java.stdlib.jar",
                ModList.get().getModFileById("hc").file.filePath.toFile().absolutePath
            )

            val dialogue = ScriptingCompiler.compileFile<DialogueScript>(file)

            val res = dialogue.execute {
                constructorArgs(player)
                jvm {
                    loadDependencies(false)
                }
            }

            res.reports.forEach {
                player.sendSystemMessage(it.render().mcText)
            }
        } catch (e: Exception) {
            player.sendSystemMessage("[DEBUG] Error while compiling dialogue: ${e.stackTraceToString()}".mcText)
        }
    }
}