package ru.hollowhorizon.hollowengine.common.ide.session.modules

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

abstract class KaRekotLibraryModule : KaLibraryModule {
    @KaExperimentalApi
    override val binaryVirtualFiles: Collection<VirtualFile> get() = emptyList()
    override val directDependsOnDependencies: List<KaModule> get() = emptyList()
    override val directFriendDependencies: List<KaModule> get() = emptyList()

    @KaPlatformInterface
    override val baseContentScope: GlobalSearchScope get() = contentScope
    override val directRegularDependencies: List<KaModule> get() = emptyList()
    override val transitiveDependsOnDependencies: List<KaModule> get() = emptyList()
    override val librarySources: KaLibrarySourceModule? get() = null
    override val targetPlatform: TargetPlatform get() = JvmPlatforms.unspecifiedJvmPlatform
    override fun toString(): String = "KaRekotLibraryModuleBase('$libraryName')"
}