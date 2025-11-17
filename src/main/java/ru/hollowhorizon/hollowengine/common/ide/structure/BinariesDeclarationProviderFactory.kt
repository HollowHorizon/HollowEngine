package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderFactory
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment

class BinariesDeclarationProviderFactory(
    kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
    libraryBinaryFiles: List<VirtualFile>,
) : KotlinDeclarationProviderFactory {
    private val delegate =
        KotlinStandaloneDeclarationProviderFactory(
            kotlinCoreProjectEnvironment.project,
            kotlinCoreProjectEnvironment.environment,
            sourceKtFiles = emptyList(),
            binaryRoots = libraryBinaryFiles,
            shouldBuildStubsForBinaryLibraries = true,
            skipBuiltins = true,
        )

    override fun createDeclarationProvider(
        scope: GlobalSearchScope,
        contextualModule: KaModule?,
    ): KotlinDeclarationProvider {
        return delegate.createDeclarationProvider(scope, contextualModule)
    }
}
