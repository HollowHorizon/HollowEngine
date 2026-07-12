package ru.hollowhorizon.hollowengine.common.ide.session

import org.jetbrains.kotlin.analysis.api.impl.base.projectStructure.KaBuiltinsModuleImpl
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.decompiler.psi.BuiltinsVirtualFileProvider
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms

class Builtins(kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment) {
    val symbolProvider =
        KotlinStandaloneDeclarationProviderFactory(
            kotlinCoreProjectEnvironment.project,
            kotlinCoreProjectEnvironment.environment,
            sourceKtFiles = emptyList(),
            binaryRoots = emptyList(),
            shouldBuildStubsForBinaryLibraries = true,
            skipBuiltins = false,
        )

    val kaModule = KaBuiltinsModuleImpl(JvmPlatforms.defaultJvmPlatform, kotlinCoreProjectEnvironment.project)

    init {
        BuiltinsVirtualFileProvider.getInstance().getBuiltinVirtualFiles().forEach {
            it.kaModule = kaModule
        }
    }
}