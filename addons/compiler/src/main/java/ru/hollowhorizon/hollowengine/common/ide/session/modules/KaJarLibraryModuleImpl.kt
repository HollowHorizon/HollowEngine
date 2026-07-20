package ru.hollowhorizon.hollowengine.common.ide.session.modules

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class KaJarLibraryModuleImpl(
    override val binaryRoots: List<Path>,
    override val libraryName: String,
    override val project: Project,
) : KaRekotLibraryModule() {
    @KaExperimentalApi
    override val contentScope: GlobalSearchScope = KaJarLibraryModuleImplScope.create(binaryRoots)
    override val isSdk: Boolean get() = false

    override fun toString(): String {
        return "KaJarLibraryModuleImpl('$libraryName')"
    }
}

@Suppress("EqualsOrHashCode")
private class KaJarLibraryModuleImplScope private constructor(roots: Set<String>) : GlobalSearchScope() {
    val roots = roots.map { it.replace("\\", "/") }

    override fun isSearchInLibraries(): Boolean {
        return true
    }

    override fun isSearchInModuleContent(aModule: Module): Boolean {
        return false
    }

    override fun contains(file: VirtualFile): Boolean {
        val filePath = file.path
        for (root in roots) {
            if (filePath.startsWith(root)) {
                return true
            }
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is KaJarLibraryModuleImplScope && other.roots == roots
    }

    override fun calcHashCode(): Int {
        return roots.hashCode()
    }

    companion object {
        fun create(roots: List<Path>): KaJarLibraryModuleImplScope {
            return KaJarLibraryModuleImplScope(roots.mapTo(mutableSetOf()) { it.absolutePathString() + if(java.nio.file.Files.isDirectory(it)) "/" else "!/" })
        }
    }
}