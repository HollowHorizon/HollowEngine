package ru.hollowhorizon.hollowengine.common.ide.structure

class ProjectEssentialLibraries(val stdlib: EssentialLibrary, val jdk: EssentialLibrary) {
    val kaModules
        get() = listOf(stdlib.kaModule, jdk.kaModule)

    val allVirtualFiles
        get() = stdlib.files + jdk.files

    val allLibraries
        get() = listOf(stdlib, jdk)
}
