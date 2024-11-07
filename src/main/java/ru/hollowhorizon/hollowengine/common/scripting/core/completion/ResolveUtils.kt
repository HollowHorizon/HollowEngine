package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import ru.hollowhorizon.hollowengine.common.scripting.core.AfterCodeAnalysisEvent

object ResolveUtils {
    @Synchronized
    fun analyzeFileForJvm(event: AfterCodeAnalysisEvent, files: List<KtFile>, project: Project): Pair<AnalysisResult, ComponentProvider> {
        val environment = event.context.environment
        val trace = CliBindingTrace(project)
        val configuration = environment.configuration

        val container = TopDownAnalyzerFacadeForJVM.createContainer(
            environment.project,
            files,
            trace,
            configuration,
            environment::createPackagePartProvider,
            ::FileBasedDeclarationProviderFactory
        )

        container.getService(LazyTopDownAnalyzer::class.java).analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files, DataFlowInfo.EMPTY)

        val moduleDescriptor = container.getService(ModuleDescriptor::class.java)
        for (extension in AnalysisHandlerExtension.getInstances(project)) {
            val result = extension.analysisCompleted(project, moduleDescriptor, trace, files)
            if (result != null) break
        }

        return Pair(
            AnalysisResult.success(trace.bindingContext, moduleDescriptor),
            container)
    }
}