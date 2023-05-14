package ru.hollowhorizon.hollowengine.common.hollowscript.dialogues

import net.minecraft.client.Minecraft
import net.minecraft.entity.player.PlayerEntity
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hc.common.scripting.HSCompiler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.dialogues.HollowDialogue
import java.io.File
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class DialogueExecutorThread(val player: PlayerEntity, val file: File) : Thread() {
    override fun run() {
        try {
            if (FMLLoader.isProduction()) System.setProperty("kotlin.java.stdlib.jar", ModList.get().getModFileById("hc").file.filePath.toFile().absolutePath)

            val dialogue = HSCompiler.COMPILER.compile<HollowDialogue>(
                DirectoryManager.CACHE_DIR,
                file
            )

            val res = dialogue.execute {
                constructorArgs(player)
                jvm {
                    loadDependencies(false)
                }
            }

            res.reports.forEach {
                player.sendMessage(it.render().toSTC(), player.uuid)
            }
        } catch (e: Exception) {
            player.sendMessage("[DEBUG] Error while compiling dialogue: ${e.stackTraceToString()}".toSTC(), player.uuid)
        }
    }
}