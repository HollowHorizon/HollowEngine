package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

class ProjectStructureProviderImpl() : KotlinProjectStructureProvider {
    override fun getImplementingModules(module: KaModule): List<KaModule> {
        error("Should not be called for jvm code")
    }

    override fun getModule(element: PsiElement, useSiteModule: KaModule?): KaModule {
        val containingFile = element.containingFile

        val virtualFile: VirtualFile? = containingFile.virtualFile
        virtualFile?.kaModule?.let {
            return it
        }

        error("Module not found for $virtualFile, $containingFile, $element")
    }

    fun setModule(file: VirtualFile, module: KaModule) {
        file.kaModule = module
    }

    fun setModule(file: PsiFile, module: KaModule) {
        setModule(file.virtualFile, module)
    }
}

private val module_KEY: Key<KaModule> = Key.create("module_KEY")
var VirtualFile.kaModule: KaModule?
    get() = getUserData(module_KEY)
    set(value) {
        putUserData(module_KEY, value)
    }
