package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinCompositePackageProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProvider
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderMerger
import org.jetbrains.kotlin.analysis.api.standalone.base.packages.KotlinStandalonePackageProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.packages.KotlinStandalonePackageProviderMerger

/** Package-provider counterpart of [HollowDeclarationProviderMerger]. */
class HollowPackageProviderMerger(project: Project) : KotlinPackageProviderMerger {
    private val delegate = KotlinStandalonePackageProviderMerger(project)

    override fun merge(providers: List<KotlinPackageProvider>): KotlinPackageProvider {
        val factory = KotlinCompositePackageProvider.factory
        val flattened = factory.flatten(providers)
        if (flattened.none { it is KotlinStandalonePackageProvider }) {
            return factory.create(flattened)
        }

        return delegate.merge(providers)
    }
}
