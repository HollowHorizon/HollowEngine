package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.platform.TargetPlatform
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile

class KaEngineScriptModule(
    override val file: KtFile,
    override val project: Project,
    binaryDependencies: List<KaModule>
) : KaScriptModule {
    override val directRegularDependencies: List<KaModule> = binaryDependencies

    override val contentScope: GlobalSearchScope
        get() = GlobalSearchScope.fileScope(file)

    override val languageVersionSettings: LanguageVersionSettings
        get() = LanguageVersionSettingsImpl.DEFAULT

    override val targetPlatform: TargetPlatform
        get() = JvmPlatforms.defaultJvmPlatform

    override val transitiveDependsOnDependencies: List<KaModule>
        get() = emptyList()

    override val directDependsOnDependencies: List<KaModule>
        get() = emptyList()

    override val directFriendDependencies: List<KaModule>
        get() = emptyList()

    @KaPlatformInterface
    override val baseContentScope: GlobalSearchScope
        get() = contentScope
}
