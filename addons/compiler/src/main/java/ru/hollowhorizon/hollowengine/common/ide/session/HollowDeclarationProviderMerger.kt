package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinCompositeDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderMerger

/**
 * Preserves file-based declaration providers when Kotlin combines several script dependency sessions.
 */
class HollowDeclarationProviderMerger(project: Project) : KotlinDeclarationProviderMerger {
    private val delegate = KotlinStandaloneDeclarationProviderMerger(project)

    override fun merge(providers: List<KotlinDeclarationProvider>): KotlinDeclarationProvider {
        val factory = KotlinCompositeDeclarationProvider.factory
        val flattened = factory.flatten(providers)
        if (flattened.none { it is KotlinStandaloneDeclarationProvider }) {
            return factory.create(flattened)
        }

        return delegate.merge(providers)
    }
}
