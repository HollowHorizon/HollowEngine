package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.contextModule
import org.jetbrains.kotlin.psi.KtFile
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaJarLibraryModuleImpl
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaJdkLibraryModuleImpl
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaRekotLibraryModule

class ProjectStructureProviderImpl() : KotlinProjectStructureProvider {
    private val libraryModules = mutableListOf<KaRekotLibraryModule>()

    override fun getImplementingModules(module: KaModule): List<KaModule> {
        error("Should not be called for jvm code")
    }

    override fun getModule(element: PsiElement, useSiteModule: KaModule?): KaModule {
        val containingFile = element.containingFile

        val virtualFile: VirtualFile = containingFile.virtualFile
            ?: error("Virtual file for $containingFile not found")
        virtualFile.kaModule?.let { return it }

        for (jarLibraryModule in libraryModules) {
            when (jarLibraryModule) {
                is KaJarLibraryModuleImpl -> {
                    if (jarLibraryModule.baseContentScope.contains(virtualFile)) {
                        return jarLibraryModule
                    }
                }
                is KaJdkLibraryModuleImpl -> {
                    if (jarLibraryModule.baseContentScope.contains(virtualFile)) {
                        return jarLibraryModule
                    }
                }
                else -> error("Unknown library module type: $jarLibraryModule")
            }
        }

        error("Module not found for $virtualFile, $containingFile, $element")
    }

    fun setModule(file: VirtualFile, module: KaModule) {
        file.kaModule = module
    }

    fun setModule(file: PsiFile, module: KaModule) {
        setModule(file.virtualFile, module)
    }

    fun removeModule(file: PsiFile) {
        (file as? KtFile)?.contextModule = null
        file.virtualFile?.kaModule = null
    }

    fun registerLibraryModule(module: KaRekotLibraryModule) {
        libraryModules += module
    }
}

private val module_KEY: Key<KaModule> = Key.create("module_KEY")
var VirtualFile.kaModule: KaModule?
    get() = getUserData(module_KEY)
    set(value) {
        putUserData(module_KEY, value)
    }