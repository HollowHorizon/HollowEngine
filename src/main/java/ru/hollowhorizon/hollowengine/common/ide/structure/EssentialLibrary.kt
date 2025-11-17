package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import java.nio.file.Path

class EssentialLibrary(val kaModule: KaLibraryModule, val files: List<VirtualFile>) {
    companion object {
        fun create(
            roots: List<Path>,
            kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
            name: String,
            isSdk: Boolean,
        ): EssentialLibrary {
            val allVirtualFiles = getVirtualFilesByRoots(roots, kotlinCoreProjectEnvironment)
            val kaLibraryModule =
                KaLibraryModuleImpl(allVirtualFiles, roots, isSdk, name, kotlinCoreProjectEnvironment.project)
            return EssentialLibrary(kaLibraryModule, allVirtualFiles)
        }
    }
}
