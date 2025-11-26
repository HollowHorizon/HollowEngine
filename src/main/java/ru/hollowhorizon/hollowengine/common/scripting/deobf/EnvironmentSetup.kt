package ru.hollowhorizon.hollowengine.common.scripting.deobf

import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import java.io.File

interface EnvironmentSetup {
    fun setup(mappings: Mappings, outputDir: File): List<File>
}