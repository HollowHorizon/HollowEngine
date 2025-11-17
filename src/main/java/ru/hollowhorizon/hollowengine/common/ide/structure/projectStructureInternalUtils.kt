package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import java.nio.file.Path

fun getVirtualFilesByRoots(
    roots: List<Path>,
    kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
): List<VirtualFile> =
    StandaloneProjectFactory.getVirtualFilesForLibraryRoots(roots, kotlinCoreProjectEnvironment.environment).distinct().flatMap {
        LibraryUtils.getAllVirtualFilesFromRoot(it, includeRoot = true)
    }

fun createLibraryModule(
    roots: List<Path>,
    kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
    name: String,
    isSdk: Boolean,
): KaLibraryModuleImpl {
    val allVirtualFiles = getVirtualFilesByRoots(roots, kotlinCoreProjectEnvironment)
    return KaLibraryModuleImpl(allVirtualFiles, roots, isSdk, name, kotlinCoreProjectEnvironment.project)
}

class KaLibraryModuleImpl(
    val virtualFiles: List<VirtualFile>,
    override val binaryRoots: List<Path>,
    override val isSdk: Boolean,
    override val libraryName: String,
    override val project: Project,
) : KaLibraryModule {
    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile>
        get() = emptyList()

    override val contentScope: GlobalSearchScope = GlobalSearchScope.filesScope(project, virtualFiles)

    override val directDependsOnDependencies: List<KaModule>
        get() = emptyList()

    override val directFriendDependencies: List<KaModule>
        get() = emptyList()

    @KaPlatformInterface
    override val baseContentScope: GlobalSearchScope get() = contentScope

    override val directRegularDependencies: List<KaModule>
        get() = emptyList()

    override val transitiveDependsOnDependencies: List<KaModule>
        get() = emptyList()

    override val librarySources: KaLibrarySourceModule?
        get() = null

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.unspecifiedJvmPlatform

    override fun toString(): String {
        return "KaLibraryModuleImpl('$libraryName')"
    }
}
