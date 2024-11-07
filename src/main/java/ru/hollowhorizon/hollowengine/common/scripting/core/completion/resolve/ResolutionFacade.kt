package ru.hollowhorizon.hollowengine.common.scripting.core.completion.resolve

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForProject
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.FrontendInternals
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowValueFactory
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.resolve.lazy.BodyResolveMode

interface ResolutionFacade {
    val project: Project

    fun analyze(
        element: KtElement,
        bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
    ): BindingContext

    fun analyze(elements: Collection<KtElement>, bodyResolveMode: BodyResolveMode): BindingContext

    fun analyzeWithAllCompilerChecks(elements: Collection<KtElement>): AnalysisResult

    fun resolveToDescriptor(
        declaration: KtDeclaration,
        bodyResolveMode: BodyResolveMode = BodyResolveMode.FULL
    ): DeclarationDescriptor

    val moduleDescriptor: ModuleDescriptor

    // get service for the module this resolution was created for
    @FrontendInternals
    fun <T : Any> getFrontendService(serviceClass: Class<T>): T

    fun <T : Any> getIdeService(serviceClass: Class<T>): T

    // get service for the module defined by PsiElement/ModuleDescriptor passed as parameter
    @FrontendInternals
    fun <T : Any> getFrontendService(element: PsiElement, serviceClass: Class<T>): T

    @FrontendInternals
    fun <T : Any> tryGetFrontendService(element: PsiElement, serviceClass: Class<T>): T?

    @FrontendInternals
    fun <T : Any> getFrontendService(moduleDescriptor: ModuleDescriptor, serviceClass: Class<T>): T

    fun getResolverForProject(): ResolverForProject<out ModuleInfo>
}

@OptIn(FrontendInternals::class)
fun ResolutionFacade.getLanguageVersionSettings(): LanguageVersionSettings =
    frontendService()

@OptIn(FrontendInternals::class)
fun ResolutionFacade.getDataFlowValueFactory(): DataFlowValueFactory =
    frontendService()

@FrontendInternals
inline fun <reified T : Any> ResolutionFacade.frontendService(): T =
    this.getFrontendService(T::class.java)