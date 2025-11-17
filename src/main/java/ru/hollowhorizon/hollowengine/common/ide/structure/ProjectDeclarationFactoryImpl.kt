package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinCompositeDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

class ProjectDeclarationFactoryImpl() : KotlinDeclarationProviderFactory {
    lateinit var projectStructure: ProjectStructure

    private val essentialLibrariesProviderFactory by lazy {
        BinariesDeclarationProviderFactory(
            projectStructure.kotlinCoreProjectEnvironment,
            projectStructure.essentialLibraries.allVirtualFiles,
        )
    }

    override fun createDeclarationProvider(
        scope: GlobalSearchScope,
        contextualModule: KaModule?,
    ): KotlinDeclarationProvider {
        val providers = buildList {
//            cellAnalyzer.getAllCells().mapNotNullTo(this) { analyzableCell ->
//                if (analyzableCell.ktFile.virtualFile !in scope) return@mapNotNullTo null
//                KotlinFileBasedDeclarationProvider(analyzableCell.ktFile)
//            }
//            compiledCellStorage.allCompiledCells().mapTo(this) {
//                it.compiledCellProviderFactory.createDeclarationProvider(scope, contextualModule)
//            }
            add(essentialLibrariesProviderFactory.createDeclarationProvider(scope, contextualModule))
            add(projectStructure.builtins.symbolProvider.createDeclarationProvider(scope, contextualModule))
        }
        return KotlinCompositeDeclarationProvider.create(providers)
    }
}
