package ru.hollowhorizon.hollowengine.common.project

import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.renderer.ClassifierNamePolicy
import org.jetbrains.kotlin.renderer.ParameterNameRenderingPolicy
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.extensions.AnalysisHandlerExtension
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.MemberScope
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.ScriptDiagnosticsMessageCollector
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.asFlexibleType
import org.jetbrains.kotlin.types.isFlexible
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstance
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.KotlinResolutionFacade
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.codeInsight.ReferenceVariantsHelper
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.resolve.getResolutionScope
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.util.IdeDescriptorRenderersScripting
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.util.importableFqName
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.util.isVisible
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.classpath
import ru.hollowhorizon.hollowengine.common.scripting.index.SymbolIndex
import java.io.File
import kotlin.system.measureTimeMillis


class Project private constructor(val name: String) {
    val disposable: Disposable = Disposer.newDisposable()
    val environment: KotlinCoreEnvironment

    init {
        val configuration = CompilerConfiguration()
        configuration.messageCollector = ScriptDiagnosticsMessageCollector(null)
        configuration.put(JVMConfigurationKeys.JVM_TARGET, org.jetbrains.kotlin.config.JvmTarget.JVM_17)
        configuration.put(CommonConfigurationKeys.MODULE_NAME, "HollowEngine.IDE")
        configuration.addJvmClasspathRoots(classpath())

        environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
    }

    private val kotlinFiles = mutableMapOf<String, KotlinFile>()

    fun delete() {
        Disposer.dispose(disposable)
    }

    fun updateKotlinFile(name: String, contents: String): KotlinFile {
        val kotlinFile = KotlinFile.from(environment.project, name, contents)
        kotlinFiles[name] = kotlinFile
        return kotlinFile
    }


    fun analysisOf(vararg files: KtFile) = analysisOf(files.toList())
    fun analysisOf(files: List<KtFile>): Analysis {
        val trace = CliBindingTrace(environment.project)
        val project = files.first().project
        val componentProvider = TopDownAnalyzerFacadeForJVM.createContainer(
            environment.project,
            files,
            trace,
            environment.configuration,
            environment::createPackagePartProvider,
            ::FileBasedDeclarationProviderFactory,
            sourceModuleSearchScope = TopDownAnalyzerFacadeForJVM.newModuleSearchScope(project, files)
        )

        componentProvider.getService(LazyTopDownAnalyzer::class.java)
            .analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files, DataFlowInfo.EMPTY)
        val moduleDescriptor = componentProvider.getService(ModuleDescriptor::class.java)
        AnalysisHandlerExtension.getInstances(project)
            .find { it.analysisCompleted(project, moduleDescriptor, trace, files) != null }
        return Analysis(
            componentProvider,
            AnalysisResult.success(trace.bindingContext, moduleDescriptor)
        )
    }

    private data class DescriptorInfo(
        val isTipsManagerCompletion: Boolean,
        val descriptors: List<DeclarationDescriptor>
    )

    fun complete(file: KotlinFile, line: Int, character: Int) =
        with(file.insert("$COMPLETION_SUFFIX ", line, character)) {
            kotlinFiles[file.name] = this

            elementAt(line, character)?.let { element ->
                val descriptorInfo = descriptorsFrom(element)
                val prefix = getPrefix(element)
                descriptorInfo.descriptors.toMutableList().apply {
                    sortWith { a, b ->
                        val (a1, a2) = a.presentableName()
                        val (b1, b2) = b.presentableName()
                        ("$a1$a2").compareTo("$b1$b2", true)
                    }
                }.mapNotNull { descriptor ->
                    completionVariantFor(prefix, descriptor)
                }
            } ?: emptyList()
        }

    fun completableElement(element: PsiElement, cursor: Int): KtElement? {
        val el = element as? KtElement ?: return null
        return el.findParent<KtImportDirective>()
        // package x.y.?
            ?: el.findParent<KtPackageDirective>()
            // :?
            ?: el.parent as? KtTypeElement
            // .?
            ?: el as? KtQualifiedExpression
            ?: el.parent as? KtQualifiedExpression
            // something::?
            ?: el as? KtCallableReferenceExpression
            ?: el.parent as? KtCallableReferenceExpression
            // something.foo() with cursor in the method
            ?: el.parent?.parent as? KtQualifiedExpression
            // ?
            ?: el as? KtNameReferenceExpression
    }

    private fun indexedCompletionVariants(bindingContext: BindingContext, element: PsiElement, prefix: String, cursor: Int): List<String> {
        val receiver = (completableElement(element, cursor) as? KtQualifiedExpression)?.receiverExpression


        SymbolIndex.query(prefix)

        return emptyList()
    }

    private fun completionVariantFor(
        prefix: String,
        descriptor: DeclarationDescriptor
    ): String? {
        val (name, tail) = descriptor.presentableName()
        var completionText = name
        var position = completionText.indexOf('(')
        if (position != -1) {
            if (completionText[position - 1] == ' ') position -= 2
            if (completionText[position + 1] == ')') position++
            completionText = completionText.substring(0, position + 1)
        }
        position = completionText.indexOf(":")
        if (position != -1) completionText = completionText.substring(0, position - 1)
        return if (prefix.isEmpty() || name.startsWith(prefix)) {
            name
        } else null
    }

    private fun descriptorsFrom(element: PsiElement): DescriptorInfo {
        val files = kotlinFiles.values.map { it.kotlinFile }.toList()
        val analysis = analysisOf(files)
        return with(analysis) {
            (referenceVariantsFrom(element)
                ?: referenceVariantsFrom(element.parent))?.let { descriptors ->
                DescriptorInfo(true, descriptors)
            } ?: element.parent.let { parent ->
                DescriptorInfo(
                    isTipsManagerCompletion = false,
                    descriptors = when (parent) {
                        is KtQualifiedExpression -> {
                            analysisResult.bindingContext.get(
                                BindingContext.EXPRESSION_TYPE_INFO,
                                parent.receiverExpression
                            )?.type?.let { expressionType ->
                                analysisResult.bindingContext.get(
                                    BindingContext.LEXICAL_SCOPE,
                                    parent.receiverExpression
                                )?.let {
                                    expressionType.memberScope.getContributedDescriptors(
                                        DescriptorKindFilter.ALL,
                                        MemberScope.ALL_NAME_FILTER
                                    )
                                }
                            }?.toList() ?: emptyList()
                        }

                        else -> analysisResult.bindingContext.get(
                            BindingContext.LEXICAL_SCOPE,
                            element as KtExpression
                        )
                            ?.getContributedDescriptors(
                                DescriptorKindFilter.ALL,
                                MemberScope.ALL_NAME_FILTER
                            )
                            ?.toList() ?: emptyList()
                    }
                )
            }
        }
    }

    private fun Analysis.referenceVariantsFrom(element: PsiElement): List<DeclarationDescriptor>? {
        val prefix = getPrefix(element)
        val elementKt = element as? KtElement ?: return emptyList()
        val bindingContext = analysisResult.bindingContext
        val resolutionFacade = KotlinResolutionFacade(
            project = element.project,
            provider = componentProvider,
            moduleDescriptor = analysisResult.moduleDescriptor
        )
        val inDescriptor: DeclarationDescriptor =
            elementKt.getResolutionScope(bindingContext, resolutionFacade).ownerDescriptor
        return when (element) {
            is KtSimpleNameExpression -> ReferenceVariantsHelper(
                analysisResult.bindingContext,
                resolutionFacade = resolutionFacade,
                moduleDescriptor = analysisResult.moduleDescriptor,
                visibilityFilter = VisibilityFilter(
                    inDescriptor,
                    bindingContext,
                    element,
                    resolutionFacade
                )
            ).getReferenceVariants(
                element,
                DescriptorKindFilter.ALL,
                nameFilter = {
                    if (prefix.isNotEmpty()) {
                        it.identifier.startsWith(prefix)
                    } else {
                        true
                    }
                },
                filterOutJavaGettersAndSetters = true,
                filterOutShadowed = true,
                excludeNonInitializedVariable = true,
                useReceiverType = null
            ).toList()
            else -> null
        }
    }

    fun getPrefix(element: PsiElement): String {
        var text = (element as? KtSimpleNameExpression)?.text
        if (text == null) {
            val type = element.parentsWithSelf.firstIsInstanceOrNull<KtSimpleNameExpression>()
            if (type != null) {
                text = type.text
            }
        }
        if (text == null) {
            text = element.text
        }
        return (text ?: "").substringBefore(COMPLETION_SUFFIX)
            .let {
                if (it.endsWith(".")) "" else it
            }
    }

    private fun DeclarationDescriptor.presentableName() = when (this) {
        is FunctionDescriptor -> name.asString() + RENDERER.renderFunctionParameters(this) to when {
            returnType != null -> RENDERER.renderType(returnType!!)
            else -> (extensionReceiverParameter?.let { param ->
                " for ${RENDERER.renderType(param.type)} in ${DescriptorUtils.getFqName(containingDeclaration)}"
            } ?: "")
        }
        else -> name.asString() to when (this) {
            is VariableDescriptor -> RENDERER.renderType(type)
            is ClassDescriptor -> " (${DescriptorUtils.getFqName(containingDeclaration)})"
            else -> RENDERER.render(this)
        }
    }

    private inner class VisibilityFilter(
        private val inDescriptor: DeclarationDescriptor,
        private val bindingContext: BindingContext,
        private val element: KtElement,
        private val resolutionFacade: KotlinResolutionFacade
    ) : (DeclarationDescriptor) -> Boolean {
        override fun invoke(descriptor: DeclarationDescriptor): Boolean {
            if (descriptor is TypeParameterDescriptor && !isTypeParameterVisible(descriptor)) return false

            if (descriptor is DeclarationDescriptorWithVisibility) {
                return descriptor.isVisible(element, null, bindingContext, resolutionFacade)
            }

            if (descriptor.isInternalImplementationDetail()) return false

            return true
        }

        private fun isTypeParameterVisible(typeParameter: TypeParameterDescriptor): Boolean {
            val owner = typeParameter.containingDeclaration
            var parent: DeclarationDescriptor? = inDescriptor
            while (parent != null) {
                if (parent == owner) return true
                if (parent is ClassDescriptor && !parent.isInner) return false
                parent = parent.containingDeclaration
            }
            return true
        }

        private fun DeclarationDescriptor.isInternalImplementationDetail(): Boolean =
            importableFqName?.asString() in excludedFromCompletion
    }


    companion object {
        private const val COMPLETION_SUFFIX = "IntellijIdeaRulezzz"
        private val excludedFromCompletion: List<String> = listOf(
            "kotlin.jvm.internal",
            "kotlin.coroutines.experimental.intrinsics",
            "kotlin.coroutines.intrinsics",
            "kotlin.coroutines.experimental.jvm.internal",
            "kotlin.coroutines.jvm.internal",
            "kotlin.reflect.jvm.internal"
        )
        private val RENDERER = IdeDescriptorRenderersScripting.SOURCE_CODE.withOptions {
            classifierNamePolicy = ClassifierNamePolicy.SHORT
            typeNormalizer = IdeDescriptorRenderersScripting.APPROXIMATE_FLEXIBLE_TYPES
            parameterNameRenderingPolicy = ParameterNameRenderingPolicy.NONE
            typeNormalizer = { kotlinType: KotlinType ->
                if (kotlinType.isFlexible()) {
                    kotlinType.asFlexibleType().lowerBound
                } else kotlinType
            }
        }

        fun test() = Project("TestProject")

        fun fromName(name: String): Project {

            DirectoryManager.HOLLOW_ENGINE.resolve("projects").resolve(name).toFile().apply {
                if (!exists()) {
                    createProject(this)
                }
            }

            return Project(name)
        }

        private fun createProject(file: File) {
            file.mkdirs()
            File(file, "assets").mkdirs()
            File(file, "data").mkdirs()
            val index = File(file, "index.mod")
            if (!index.exists()) {
                val name = file.name
                index.writeBytes(ModIndex.create(name, name.replaceFirstChar { it.uppercase() }, "1.0").save())
            }
        }
    }
}

inline fun <reified Find> PsiElement.findParent() =
    parentsWithSelf.filterIsInstance<Find>().firstOrNull()

fun main() {
    val project = Project.test()

    val file = project.updateKotlinFile("Test.kt", "fun main() { println(\"Hello, World!\") }")

    val module = project.analysisOf(file.kotlinFile).analysisResult.moduleDescriptor

    SymbolIndex.refresh(module)

    repeat(100) {
        val time = measureTimeMillis {
            println(project.complete(file, 0, 12).joinToString("\n"))
        }
        println("Completion took $time ms")
    }
}