package ru.hollowhorizon.hollowengine.common.scripting.deobf

import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.remapJars
import ru.hollowhorizon.hollowengine.common.utils.ModList
import java.io.File

class ModsEnvironment(vararg val modIds: String) : EnvironmentSetup {
    override fun setup(mappings: Mappings, outputDir: File): List<File> {
        val mods = modIds.flatMap { ModList.getAllFiles(it) }
        if (mods.singleOrNull()?.isDirectory == true) return mods
        return remapJars(mappings, mods, outputDir)
    }
}