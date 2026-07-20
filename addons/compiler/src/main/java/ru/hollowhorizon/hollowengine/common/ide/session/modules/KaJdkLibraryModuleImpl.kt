package ru.hollowhorizon.hollowengine.common.ide.session.modules

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class KaJdkLibraryModuleImpl(
    home: Path,
    override val binaryRoots: List<Path>,
    override val libraryName: String,
    override val project: Project,
) : KaRekotLibraryModule() {
    @KaExperimentalApi
    override val contentScope: GlobalSearchScope = KaJdkLibraryModuleImplScope.create(home)
    override val isSdk: Boolean get() = true

    override fun toString(): String {
        return "KaJarLibraryModuleImpl('$libraryName')"
    }
}

@Suppress("EqualsOrHashCode")
private class KaJdkLibraryModuleImplScope private constructor(home: String) : GlobalSearchScope() {
    val home = home.replace("\\", "/")
    override fun isSearchInLibraries(): Boolean = true
    override fun isSearchInModuleContent(aModule: Module): Boolean = false

    override fun contains(file: VirtualFile): Boolean {
        return file.path.startsWith(home)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaJdkLibraryModuleImplScope && other.home == home
    }

    override fun calcHashCode(): Int {
        return home.hashCode()
    }

    companion object {
        fun create(home: Path): KaJdkLibraryModuleImplScope {
            return KaJdkLibraryModuleImplScope(home.absolutePathString())
        }
    }
}