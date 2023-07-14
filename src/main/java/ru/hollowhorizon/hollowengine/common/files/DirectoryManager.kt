package ru.hollowhorizon.hollowengine.common.files

import net.minecraft.entity.player.PlayerEntity
import net.minecraftforge.fml.loading.FMLPaths
import java.io.File

object DirectoryManager {
    private val SCRIPTS_DIR = FMLPaths.GAMEDIR.get().resolve("hollowengine/scripts").toFile().apply {
        if (!exists()) mkdirs()
    }

    @JvmStatic
    fun init() {

    }

    private fun getScripts() =
        SCRIPTS_DIR.walk().filter { it.path.endsWith(".kts") }.toList()

    fun getAllDialogues() = getScripts().filter { it.path.endsWith(".hsd.kts") }

    fun getAllStoryEvents() = getScripts().filter { it.path.endsWith(".se.kts") }

    fun getModScripts() = getScripts().filter { it.path.endsWith(".mod.kts") }


    @JvmStatic
    fun File.toReadablePath(): String {
        return this.path.substringAfter(FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile().path + "\\")
            .replace("\\", "/").replace(".jar", "")
    }

    @JvmStatic
    fun String.fromReadablePath(): File {
        return FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve(this).toFile()
    }
}

fun PlayerEntity.sendProgress(data: String, progress: Int) = runPacket {
    return@runPacket this
}

fun <T> runPacket(function: () -> T): T {
    return function()
}
