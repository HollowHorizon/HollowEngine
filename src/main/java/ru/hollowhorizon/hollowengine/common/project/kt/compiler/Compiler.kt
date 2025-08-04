@file:OptIn(ExperimentalCompilerApi::class)
@file:Suppress("DEPRECATION")

package ru.hollowhorizon.hollowengine.common.project.kt.compiler

import org.jetbrains.kotlin.backend.jvm.FacadeClassSourceShimForFragmentCompilation
import org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensionsImpl
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.output.writeAllTo
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoots
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.com.intellij.lang.Language
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.StandardFileSystems
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.container.getService
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.extensions.StorageComponentContainerContributor
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.load.kotlin.toSourceElement
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTraceContext
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.TopDownAnalysisMode
import org.jetbrains.kotlin.resolve.calls.components.InferenceSession
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.lazy.declarations.FileBasedDeclarationProviderFactory
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.source.PsiSourceFile
import org.jetbrains.kotlin.samWithReceiver.CliSamWithReceiverComponentContributor
import org.jetbrains.kotlin.scripting.compiler.plugin.ScriptingCompilerConfigurationComponentRegistrar
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.CliScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys
import org.jetbrains.kotlin.scripting.definitions.KotlinScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedContainerSource
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.ExpressionTypingServices
import org.jetbrains.kotlin.util.KotlinFrontEndException
import ru.hollowhorizon.hc.common.utils.UnsafeTools
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.project.kt.CodegenConfiguration
import ru.hollowhorizon.hollowengine.common.project.kt.CompilerConfiguration
import ru.hollowhorizon.hollowengine.common.project.kt.ScriptsConfiguration
import ru.hollowhorizon.hollowengine.common.project.kt.util.LoggingMessageCollector
import ru.hollowhorizon.hollowengine.common.scripting.ScriptTypes
import java.io.Closeable
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.createCompilationConfigurationFromTemplate
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.JvmGetScriptingClass
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import org.jetbrains.kotlin.config.CompilerConfiguration as KotlinCompilerConfiguration

private val GRADLE_DSL_DEPENDENCY_PATTERN = Regex("^gradle-(?:kotlin-dsl|core).*\\.jar$")
val STUB_UNBOUND_IR_SYMBOLS: CompilerConfigurationKey<Boolean> =
    CompilerConfigurationKey<Boolean>("stub unbound IR symbols")

/**
 * Kotlin compiler APIs used to parse, analyze and compile
 * files and expressions.
 */
private class CompilationEnvironment(
    javaSourcePath: Set<Path>,
    classPath: Set<Path>,
    scriptsConfig: ScriptsConfiguration,
) : Closeable {
    private val disposable = Disposer.newDisposable()

    val environment: KotlinCoreEnvironment
    val parser: KtPsiFactory
    val scripts: ScriptDefinitionProvider

    init {
        environment = KotlinCoreEnvironment.createForProduction(
            projectDisposable = disposable,
            // Not to be confused with the CompilerConfiguration in the language server Configuration
            configuration = KotlinCompilerConfiguration().apply {
                val langFeatures = mutableMapOf<LanguageFeature, LanguageFeature.State>()
                for (langFeature in LanguageFeature.values()) {
                    langFeatures[langFeature] = LanguageFeature.State.ENABLED
                }
                val languageVersionSettings = LanguageVersionSettingsImpl(
                    LanguageVersion.LATEST_STABLE,
                    ApiVersion.createByLanguageVersion(LanguageVersion.LATEST_STABLE),
                    emptyMap(),
                    langFeatures
                )

                put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)
                put(CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS, languageVersionSettings)
                put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, LoggingMessageCollector)
                add(ComponentRegistrar.PLUGIN_COMPONENT_REGISTRARS, ScriptingCompilerConfigurationComponentRegistrar())
                put(JVMConfigurationKeys.USE_PSI_CLASS_FILES_READING, true)

                // configure jvm runtime classpaths
                configureJdkClasspathRoots()

                // Kotlin 1.8.20 requires us to specify the JDK home, otherwise java.* classes won't resolve
                // See https://github.com/JetBrains/kotlin-compiler-server/pull/626
                val jdkHome = File(System.getProperty("java.home"))
                put(JVMConfigurationKeys.JDK_HOME, jdkHome)

                addJvmClasspathRoots(classPath.map { it.toFile() })
                addJavaSourceRoots(javaSourcePath.map { it.toFile() })

                if (scriptsConfig.enabled) {
                    // Setup script templates (e.g. used by Gradle's Kotlin DSL)
                    val scriptDefinitions: MutableList<ScriptDefinition> =
                        mutableListOf(ScriptDefinition.getDefault(defaultJvmScriptingHostConfiguration))

                    val fileClassPath = classPath.map { it.toFile() }
                    val scriptHostConfig = ScriptingHostConfiguration(defaultJvmScriptingHostConfiguration) {
                        getScriptingClass(JvmGetScriptingClass())
                        configurationDependencies(JvmDependency(fileClassPath))
                    }

                    scriptDefinitions.addAll(ScriptTypes.SCRIPTS.values.map {
                        ScriptDefinition.FromConfigurations(
                            scriptHostConfig,
                            createCompilationConfigurationFromTemplate(
                                KotlinType(it.kotlin),
                                scriptHostConfig,
                                Compiler::class
                            ),
                            null
                        )
                    })

                    HollowEngine.LOGGER.info("Adding script definitions ${scriptDefinitions.map { it.name }}")
                    addAll(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS, scriptDefinitions)
                }
            },
            configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        // hacky way to support SamWithReceiverAnnotations for scripts
        val scriptDefinitions: List<ScriptDefinition> =
            environment.configuration.getList(ScriptingConfigurationKeys.SCRIPT_DEFINITIONS)
        scriptDefinitions.takeIf { it.isNotEmpty() }?.let {
            val annotations = scriptDefinitions.map { it.definitionId }
            StorageComponentContainerContributor.registerExtension(
                environment.project,
                CliSamWithReceiverComponentContributor(annotations)
            )
        }
        val project = environment.project
        parser = KtPsiFactory(project)
        scripts = ScriptDefinitionProvider.getInstance(project)!! as CliScriptDefinitionProvider
    }

    fun updateConfiguration(config: CompilerConfiguration) {
        JvmTarget.fromString(config.jvm.target)
            ?.let { environment.configuration.put(JVMConfigurationKeys.JVM_TARGET, it) }
    }

    fun createContainer(sourcePath: Collection<KtFile>): Pair<ComponentProvider, BindingTraceContext> {
        val trace = CliBindingTrace(environment.project)
        val container = TopDownAnalyzerFacadeForJVM.createContainer(
            project = environment.project,
            files = sourcePath,
            trace = trace,
            configuration = environment.configuration,
            packagePartProvider = environment::createPackagePartProvider,
            // TODO FileBasedDeclarationProviderFactory keeps indices, re-use it across calls
            declarationProviderFactory = ::FileBasedDeclarationProviderFactory
        )
        UnsafeTools.setField(ScriptConfigurationsProvider.getInstance(environment.project)!!, "cache", hashMapOf<String, ScriptCompilationConfigurationResult?>())
        return Pair(container, trace)
    }

    override fun close() {
        Disposer.dispose(disposable)
    }
}

/**
 * Determines the compilation environment used
 * by the compiler (and thus the class path).
 */
enum class CompilationKind {
    /** Uses the default class path. */
    DEFAULT,

    /** Uses the Kotlin DSL class path if available. */
    BUILD_SCRIPT
}

/**
 * Incrementally compiles files and expressions.
 * The basic strategy for compiling one file at-a-time is outlined in OneFilePerformance.
 */
class Compiler(
    javaSourcePath: Set<Path>,
    classPath: Set<Path>,
    buildScriptClassPath: Set<Path> = emptySet(),
    scriptsConfig: ScriptsConfiguration,
    private val codegenConfig: CodegenConfiguration,
    private val outputDirectory: File,
) : Closeable {
    private var closed = false
    private val localFileSystem: VirtualFileSystem

    private val defaultCompileEnvironment = CompilationEnvironment(javaSourcePath, classPath, scriptsConfig)
    private val buildScriptCompileEnvironment = buildScriptClassPath
        .takeIf { it.isNotEmpty() && scriptsConfig.enabled && scriptsConfig.buildScriptsEnabled }
        ?.let { CompilationEnvironment(emptySet(), it, scriptsConfig) }
    private val compileLock = ReentrantLock() // TODO: Lock at file-level

    companion object {
        init {
            setIdeaIoUseFallback()
        }
    }

    init {
        localFileSystem = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL)
    }

    /**
     * Updates the compiler environment using the given
     * configuration (which is a class from this project).
     */
    fun updateConfiguration(config: CompilerConfiguration) {
        defaultCompileEnvironment.updateConfiguration(config)
        buildScriptCompileEnvironment?.updateConfiguration(config)
    }

    fun createPsiFile(
        content: String,
        file: Path = Paths.get("dummy.virtual.kt"),
        language: Language = KotlinLanguage.INSTANCE,
        kind: CompilationKind = CompilationKind.DEFAULT,
    ): PsiFile {
        assert(!content.contains('\r'))

        val new = psiFileFactoryFor(kind).createFileFromText(
            file.toString().substringAfterLast('/').substringAfterLast('\\'),
            language,
            content,
            true,
            false
        )
        assert(new.virtualFile != null)
        (new.virtualFile as? LightVirtualFile)?.originalFile = LightVirtualFile(file.toString())

        return new
    }

    fun createKtFile(
        content: String,
        file: Path = Paths.get("dummy.virtual.kt"),
        kind: CompilationKind = CompilationKind.DEFAULT,
    ): KtFile =
        createPsiFile(content, file, language = KotlinLanguage.INSTANCE, kind = kind) as KtFile

    fun createKtExpression(
        content: String,
        file: Path = Paths.get("dummy.virtual.kt"),
        kind: CompilationKind = CompilationKind.DEFAULT,
    ): KtExpression {
        val property = createKtDeclaration("val x = $content", file, kind) as KtProperty
        return property.initializer!!
    }

    fun createKtDeclaration(
        content: String,
        file: Path = Paths.get("dummy.virtual.kt"),
        kind: CompilationKind = CompilationKind.DEFAULT,
    ): KtDeclaration {
        val parse = createKtFile(content, file, kind)
        val declarations = parse.declarations

        assert(declarations.size == 1) { "${declarations.size} declarations in $content" }

        val onlyDeclaration = declarations.first()

        if (onlyDeclaration is KtScript) {
            val scriptDeclarations = onlyDeclaration.declarations

            assert(declarations.size == 1) { "${declarations.size} declarations in script in $content" }

            return scriptDeclarations.first()
        } else return onlyDeclaration
    }

    private fun compileEnvironmentFor(kind: CompilationKind): CompilationEnvironment = when (kind) {
        CompilationKind.DEFAULT -> defaultCompileEnvironment
        CompilationKind.BUILD_SCRIPT -> buildScriptCompileEnvironment ?: defaultCompileEnvironment
    }

    fun psiFileFactoryFor(kind: CompilationKind): PsiFileFactory =
        PsiFileFactory.getInstance(compileEnvironmentFor(kind).environment.project)

    fun compileKtFile(
        file: KtFile,
        sourcePath: Collection<KtFile>,
        kind: CompilationKind = CompilationKind.DEFAULT,
    ): Pair<BindingContext, ModuleDescriptor> =
        compileKtFiles(listOf(file), sourcePath, kind)

    fun compileKtFiles(
        files: Collection<KtFile>,
        sourcePath: Collection<KtFile>,
        kind: CompilationKind = CompilationKind.DEFAULT,
    ): Pair<BindingContext, ModuleDescriptor> {
        if (kind == CompilationKind.BUILD_SCRIPT) {
            // Print the (legacy) script template used by the compiled Kotlin DSL build file
            files.forEach {
                HollowEngine.LOGGER.debug(
                    "{} -> ScriptDefinition: {}",
                    it,
                    it.findScriptDefinition()?.asLegacyOrNull<KotlinScriptDefinition>()?.template?.simpleName
                )
            }
        }

        compileLock.withLock {
            val compileEnv = compileEnvironmentFor(kind)
            val (container, trace) = compileEnv.createContainer(sourcePath)
            val module = container.getService(ModuleDescriptor::class.java)
            container.get<LazyTopDownAnalyzer>().analyzeDeclarations(TopDownAnalysisMode.TopLevelDeclarations, files)
            return Pair(trace.bindingContext, module)
        }
    }

    fun compileKtExpression(
        expression: KtExpression,
        scopeWithImports: LexicalScope,
        sourcePath: Collection<KtFile>,
        kind: CompilationKind = CompilationKind.DEFAULT,
    ): Pair<BindingContext, ComponentProvider>? =
        try {
            // Use same lock as 'compileFile' to avoid concurrency issues such as #42
            compileLock.withLock {
                val compileEnv = compileEnvironmentFor(kind)
                val (container, trace) = compileEnv.createContainer(sourcePath)
                val incrementalCompiler = container.get<ExpressionTypingServices>()
                incrementalCompiler.getTypeInfo(
                    scopeWithImports,
                    expression,
                    TypeUtils.NO_EXPECTED_TYPE,
                    DataFlowInfo.EMPTY,
                    InferenceSession.default,
                    trace,
                    true
                )
                Pair(trace.bindingContext, container)
            }
        } catch (e: KotlinFrontEndException) {
            HollowEngine.LOGGER.error(
                """
                Error while analyzing expression: ${describeExpression(expression.text)}
                Message: ${e.message}
                Cause: ${e.cause?.message}
                Stack trace: ${e.attachments.joinToString("\n") { it.displayText }}
            """.trimIndent()
            )
            null
        }

    fun removeGeneratedCode(files: Collection<KtFile>) {
        files.forEach { file ->
            file.declarations.forEach { declaration ->
                outputDirectory.resolve(
                    file.packageFqName.asString()
                        .replace(".", File.separator) + File.separator + declaration.name + ".class"
                ).delete()
            }
        }
    }

    fun generateCode(module: ModuleDescriptor, bindingContext: BindingContext, files: Collection<KtFile>) {
        outputDirectory.takeIf { codegenConfig.enabled }?.let {
            compileLock.withLock {
                val compileEnv = compileEnvironmentFor(CompilationKind.DEFAULT)
                val codegenFactory = createJvmIrCodegenFactory(compileEnv.environment.configuration)

                val state = GenerationState(
                    project = compileEnv.environment.project,
                    builderFactory = ClassBuilderFactories.BINARIES,
                    module = module,
                    configuration = compileEnv.environment.configuration
                )
                codegenFactory.convertAndGenerate(files, state, bindingContext)
                state.factory.writeAllTo(it)
            }
        }
    }

    private fun createJvmIrCodegenFactory(configuration: KotlinCompilerConfiguration): JvmIrCodegenFactory {
        val stubUnboundIrSymbols = configuration[STUB_UNBOUND_IR_SYMBOLS] == true
        val jvmGeneratorExtensions = if (stubUnboundIrSymbols) {
            object : JvmGeneratorExtensionsImpl(configuration) {
                override fun getContainerSource(descriptor: DeclarationDescriptor): DeserializedContainerSource? {
                    // Stubbed top-level function IR symbols (from other source files in the module) require a parent facade class to be
                    // generated, which requires a container source to be provided. Without a facade class, function IR symbols will have
                    // an `IrExternalPackageFragment` parent, which trips up code generation during IR lowering.
                    //descriptor.toSourceElement.containingFile
                    val psiSourceFile =
                        descriptor.toSourceElement.containingFile as? PsiSourceFile ?: return super.getContainerSource(
                            descriptor
                        )
                    return FacadeClassSourceShimForFragmentCompilation(psiSourceFile)
                }
            }
        } else {
            JvmGeneratorExtensionsImpl(configuration)
        }
        val ideCodegenSettings = JvmIrCodegenFactory.IdeCodegenSettings(
            shouldStubAndNotLinkUnboundSymbols = stubUnboundIrSymbols,
            shouldDeduplicateBuiltInSymbols = stubUnboundIrSymbols,
            // Because the file to compile may be contained in a "common" multiplatform module, an `expect` declaration doesn't necessarily
            // have an obvious associated `actual` symbol. `shouldStubOrphanedExpectSymbols` generates stubs for such `expect` declarations.
            shouldStubOrphanedExpectSymbols = true,
            // Likewise, the file to compile may be contained in a "platform" multiplatform module, where the `actual` declaration is
            // referenced in the symbol table automatically, but not its `expect` counterpart, because it isn't contained in the files to
            // compile. `shouldReferenceUndiscoveredExpectSymbols` references such `expect` symbols in the symbol table so that they can
            // subsequently be stubbed.
            shouldReferenceUndiscoveredExpectSymbols = true,
        )

        return JvmIrCodegenFactory(
            configuration,
            jvmGeneratorExtensions = jvmGeneratorExtensions,
            ideCodegenSettings = ideCodegenSettings,
        )
    }

    override fun close() {
        if (!closed) {
            defaultCompileEnvironment.close()
            buildScriptCompileEnvironment?.close()
            closed = true
        } else {
            HollowEngine.LOGGER.warn("Compiler is already closed!")
        }
    }
}

private fun describeExpression(expression: String): String = expression.lines().let { lines ->
    if (lines.size < 5) {
        expression
    } else {
        (lines.take(3) + listOf("...", lines.last())).joinToString(separator = "\n")
    }
}