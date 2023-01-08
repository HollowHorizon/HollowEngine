package ru.hollowhorizon.hollowstory.common.hollowscript.dialogues

import net.minecraft.client.Minecraft
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hc.client.utils.toSTC
import ru.hollowhorizon.hc.common.scripting.HSCompiler
import ru.hollowhorizon.hc.common.scripting.classloader.HollowScriptClassLoader
import ru.hollowhorizon.hollowstory.client.gui.DialogueScreen
import ru.hollowhorizon.hollowstory.dialogues.HDCharacter
import ru.hollowhorizon.hollowstory.dialogues.HDialogue
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.jvm.baseClassLoader
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

class DialogueExecutorThread(val screen: DialogueScreen, private val dialogueName: String, private val stream: String) : Thread() {
    override fun run() {
        val player = Minecraft.getInstance().player!!
        player.sendMessage("[DEBUG] Compiling dialogue".toSTC(), player.uuid)

        try {
            if(FMLLoader.isProduction()) System.setProperty("kotlin.java.stdlib.jar", ModList.get().getModFileById("hc").file.filePath.toFile().absolutePath)

            val dialogue = HSCompiler().compile<HDialogue>(
                dialogueName.replace(".", "/"),
                stream
            )

            player.sendMessage("[DEBUG] Dialogue compiled".toSTC(), player.uuid)

            val res = dialogue.execute {
                constructorArgs(screen, HDCharacter(player))
                jvm {
                    baseClassLoader(
                        HollowScriptClassLoader(
                            { null },
                            FMLLoader.getLaunchClassLoader(),
                            setOf()
                        )
                    )
                    loadDependencies(false)
                }
            }

            player.sendMessage("[DEBUG] Dialogue finished".toSTC(), player.uuid)

            res.reports.forEach {
                player.sendMessage(it.render().toSTC(), player.uuid)
            }
        } catch (e: Exception) {
            player.sendMessage("[DEBUG] Error while compiling dialogue: ${e.stackTraceToString()}".toSTC(), player.uuid)
        }

        screen.shouldClose = true
    }
}