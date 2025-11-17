package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.psi.KtPsiFactory

class ProjectStructure(
    val kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
    val essentialLibraries: ProjectEssentialLibraries,
    val builtins: Builtins,
    val projectStructureProvider: ProjectStructureProviderImpl,
    private val projectDisposable: Disposable,
) {
    val project: Project
        get() = kotlinCoreProjectEnvironment.project
    val factory = KtPsiFactory(project, eventSystemEnabled = true)

    fun shutdown() {
        Disposer.dispose(projectDisposable)
    }
}
