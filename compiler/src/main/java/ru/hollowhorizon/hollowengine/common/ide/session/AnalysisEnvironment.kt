package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.mock.MockProject
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.fileTypes.BinaryFileTypeDecompilers
import com.intellij.openapi.util.Disposer
import com.intellij.psi.ClassFileViewProviderFactory
import com.intellij.psi.FileTypeFileViewProviders
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartTypePointerManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClassFileDecompiler
import com.intellij.psi.impl.compiled.ClassFileStubBuilder
import com.intellij.psi.impl.compiled.ClsDecompilerImpl
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl
import com.intellij.psi.impl.smartPointers.SmartTypePointerManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.BinaryFileStubBuilders
import org.jetbrains.kotlin.analysis.api.impl.base.util.LibraryUtils
import org.jetbrains.kotlin.analysis.api.permissions.KaAnalysisPermissionRegistry
import org.jetbrains.kotlin.analysis.api.platform.KotlinDeserializedDeclarationsOrigin
import org.jetbrains.kotlin.analysis.api.platform.KotlinMessageBusProvider
import org.jetbrains.kotlin.analysis.api.platform.KotlinPlatformSettings
import org.jetbrains.kotlin.analysis.api.platform.KotlinProjectMessageBusProvider
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.platform.lifetime.KotlinAlwaysAccessibleLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.platform.lifetime.KotlinLifetimeTokenFactory
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackagePartProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.packages.KotlinPackageProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.permissions.KotlinAnalysisPermissionOptions
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.platform.resolution.KaResolutionActivityTracker
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.standalone.base.modification.KotlinStandaloneModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.packages.KotlinStandalonePackageProviderFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.permissions.KotlinStandaloneAnalysisPermissionOptions
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.FirStandaloneServiceRegistrar
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.KtStaticModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory
import org.jetbrains.kotlin.analysis.decompiler.konan.KlibMetaFileType
import org.jetbrains.kotlin.analysis.decompiler.konan.KotlinKlibMetadataDecompiler
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInDecompiler
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInFileType
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinClassFileDecompiler
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.library.KLIB_METADATA_FILE_EXTENSION
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.serialization.deserialization.METADATA_FILE_EXTENSION
import org.jetbrains.kotlin.serialization.deserialization.builtins.BuiltInSerializerProtocol
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaJarLibraryModuleImpl
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaJdkLibraryModuleImpl
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaRekotLibraryModule
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.script.experimental.api.SourceCode

class AnalysisEnvironment(
    val classpath: List<Path>,
    val scriptDefinitions: List<ScriptDefinition> = emptyList(),
    val javaHome: Path? = null,
) {
    private val projectDisposable = Disposer.newDisposable("AnalysisEnvironment")
    val kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment
    val project: MockProject
    val analyzer: ScriptingAnalyzerImpl

    init {
        // Установка временной директории для Idea
        setupIdeaHome()

        // Создание окружения проекта
        kotlinCoreProjectEnvironment = StandaloneProjectFactory.createProjectEnvironment(
            projectDisposable,
            KotlinCoreApplicationEnvironmentMode.Production,
        )

        project = kotlinCoreProjectEnvironment.project

        // Регистрация расширений и сервисов
        registerExtensionPoints()
        registerApplicationServices()
        KotlinCoreEnvironment.registerProjectExtensionPoints(project.extensionArea)
        registerDecompilers()

        // Создание библиотечных модулей
        val libraries = createLibraryModules()

        // Создание и настройка провайдера структуры проекта
        val projectStructureProvider = ProjectStructureProviderImpl()
        libraries.forEach { projectStructureProvider.registerLibraryModule(it) }

        val builtins = Builtins(kotlinCoreProjectEnvironment)
        // Регистрация сервисов проекта
        registerProjectServices(builtins, libraries, projectStructureProvider)

        // Создание структуры проекта
        analyzer = ScriptingAnalyzerImpl(
            kotlinCoreProjectEnvironment,
            libraries,
            builtins,
            projectStructureProvider
        )

        // Настройка провайдера скриптов
        setupScriptDefinitions()
    }

    private fun setupIdeaHome() {
        val key = "idea.home.path"
        if (System.getProperty(key) == null) {
            val tempDir = System.getProperty("java.io.tmpdir")
            val ideaHome = Path.of(tempDir, "ideaHomePath").apply {
                if (!exists()) createDirectory()
            }
            System.setProperty(key, ideaHome.absolutePathString())
        }
    }

    private fun registerExtensionPoints() {
        CoreApplicationEnvironment.registerExtensionPoint(
            project.extensionArea,
            KaResolveExtensionProvider.EP_NAME.name,
            KaResolveExtensionProvider::class.java
        )
    }

    private fun registerApplicationServices() {
        KotlinCoreEnvironment.underApplicationLock {
            val applicationEnvironment = kotlinCoreProjectEnvironment.environment
            val application = applicationEnvironment.application

            applicationEnvironment.registerApplicationService(
                KotlinAnalysisPermissionOptions::class.java,
                KotlinStandaloneAnalysisPermissionOptions(),
            )

            applicationEnvironment.registerApplicationService(
                KaAnalysisPermissionRegistry::class.java,
                object : KaAnalysisPermissionRegistry {
                    override var explicitAnalysisRestriction: KaAnalysisPermissionRegistry.KaExplicitAnalysisRestriction? =
                        null
                    override var isAnalysisAllowedOnEdt: Boolean = true
                    override var isAnalysisAllowedInWriteAction: Boolean = true
                },
            )

            applicationEnvironment.registerApplicationService(
                KaResolutionActivityTracker::class.java,
                Class.forName("org.jetbrains.kotlin.analysis.low.level.api.fir.lazy.resolve.LLFirResolutionActivityTracker")
                    .newInstance() as KaResolutionActivityTracker,
            )
        }
    }

    private fun registerDecompilers() {
        val applicationEnvironment = kotlinCoreProjectEnvironment.environment
        val applicationDisposable = kotlinCoreProjectEnvironment.environment.parentDisposable

        applicationEnvironment.registerFileType(
            KotlinBuiltInFileType,
            BuiltInSerializerProtocol.BUILTINS_FILE_EXTENSION
        )
        applicationEnvironment.registerFileType(KotlinBuiltInFileType, METADATA_FILE_EXTENSION)
        applicationEnvironment.registerFileType(KlibMetaFileType, KLIB_METADATA_FILE_EXTENSION)

        for (fileType in listOf(JavaClassFileType.INSTANCE, KotlinBuiltInFileType, KlibMetaFileType)) {
            FileTypeFileViewProviders.INSTANCE.addExplicitExtension(
                fileType,
                ClassFileViewProviderFactory(),
                applicationDisposable,
            )

            BinaryFileStubBuilders.INSTANCE.addExplicitExtension(
                fileType,
                ClassFileStubBuilder(),
                applicationDisposable
            )
            BinaryFileTypeDecompilers.getInstance()
                .addExplicitExtension(fileType, ClassFileDecompiler(), applicationDisposable)
        }

        ClassFileDecompilers.getInstance().EP_NAME.point.apply {
            registerExtension(KotlinClassFileDecompiler(), LoadingOrder.FIRST, applicationDisposable)
            registerExtension(KotlinBuiltInDecompiler(), LoadingOrder.FIRST, applicationDisposable)
            registerExtension(
                KotlinKlibMetadataDecompiler(),
                LoadingOrder.FIRST,
                applicationDisposable,
            )

            registerExtension(ClsDecompilerImpl(), LoadingOrder.FIRST, applicationDisposable)
        }
    }

    private fun createLibraryModules(): List<KaRekotLibraryModule> {
        val libraries = mutableListOf<KaRekotLibraryModule>()

        // Добавление JDK
        val jdkHome = javaHome ?: Path.of(System.getProperty("java.home"))
        val jdkClasses = LibraryUtils.findClassesFromJdkHome(jdkHome, isJre = true)
            .ifEmpty { LibraryUtils.findClassesFromJdkHome(jdkHome, isJre = false) }

        libraries.add(
            KaJdkLibraryModuleImpl(
                jdkHome,
                jdkClasses.distinct(),
                "JDK",
                kotlinCoreProjectEnvironment.project,
            )
        )

        // Добавление пользовательских библиотек из classpath
        libraries.add(
            KaJarLibraryModuleImpl(
                classpath,
                "ClassPath",
                kotlinCoreProjectEnvironment.project,
            )
        )

        return libraries
    }

    private fun registerProjectServices(
        builtins: Builtins,
        libraries: List<KaRekotLibraryModule>,
        projectStructureProvider: ProjectStructureProviderImpl,
    ) {
        project.apply {
            registerService(KotlinMessageBusProvider::class.java, KotlinProjectMessageBusProvider::class.java)
            FirStandaloneServiceRegistrar.registerProjectServices(project)
            FirStandaloneServiceRegistrar.registerProjectExtensionPoints(project)
            FirStandaloneServiceRegistrar.registerProjectModelServices(
                project,
                kotlinCoreProjectEnvironment.parentDisposable,
            )

            registerService(
                KotlinModificationTrackerFactory::class.java,
                KotlinStandaloneModificationTrackerFactory::class.java,
            )
            registerService(
                KotlinLifetimeTokenFactory::class.java,
                KotlinAlwaysAccessibleLifetimeTokenFactory::class.java,
            )

            registerService(
                KotlinAnnotationsResolverFactory::class.java,
                KotlinStandaloneAnnotationsResolverFactory(project, emptyList()),
            )

            registerService(
                KotlinDeclarationProviderFactory::class.java,
                SimpleDeclarationProviderFactory(kotlinCoreProjectEnvironment, builtins, classpath)
            )

            registerService(
                KotlinDeclarationProviderMerger::class.java,
                KotlinStandaloneDeclarationProviderMerger(this),
            )

            registerService(
                KotlinPackageProviderFactory::class.java,
                KotlinStandalonePackageProviderFactory(project, emptyList()),
            )

            registerService(
                KotlinPackagePartProviderFactory::class.java,
                object : KotlinPackagePartProviderFactory {
                    private val cache = WeakHashMap<GlobalSearchScope, PackagePartProvider>()

                    override fun createPackagePartProvider(scope: GlobalSearchScope): PackagePartProvider {
                        return cache.getOrPut(scope) {
                            StandaloneProjectFactory.createPackagePartsProvider(
                                StandaloneProjectFactory.getAllBinaryRoots(
                                    libraries,
                                    kotlinCoreProjectEnvironment.environment,
                                )
                            )(scope)
                        }
                    }
                },
            )

            registerService(SmartTypePointerManager::class.java, SmartTypePointerManagerImpl::class.java)
            registerService(SmartPointerManager::class.java, SmartPointerManagerImpl::class.java)
            registerService(KotlinProjectStructureProvider::class.java, projectStructureProvider)
            registerService(
                KotlinModuleDependentsProvider::class.java,
                KtStaticModuleDependentsProvider(emptyList()),
            )

            // Инициализация сервисов для работы с виртуальными файлами
            StandaloneProjectFactory::class.java.getDeclaredMethod(
                "initialiseVirtualFileFinderServices",
                KotlinCoreProjectEnvironment::class.java,
                List::class.java,
                List::class.java,
                org.jetbrains.kotlin.config.LanguageVersionSettings::class.java,
                Path::class.java
            ).apply {
                isAccessible = true
            }.invoke(
                StandaloneProjectFactory,
                kotlinCoreProjectEnvironment,
                libraries,
                emptyList<Any>(),
                HollowEngineLanguageSettings.INSTANCE,
                null,
            )

            registerService(
                KotlinPlatformSettings::class.java,
                object : KotlinPlatformSettings {
                    override val deserializedDeclarationsOrigin: KotlinDeserializedDeclarationsOrigin
                        get() = KotlinDeserializedDeclarationsOrigin.STUBS
                },
            )
        }
    }

    private fun setupScriptDefinitions() {
        if (scriptDefinitions.isNotEmpty()) {
            val prioritizedDefinitions = scriptDefinitions.sortedByDescending { it.fileExtension.length }

            project.registerService(
                ScriptDefinitionProvider::class.java,
                object : ScriptDefinitionProvider {
                    override fun findDefinition(script: SourceCode): ScriptDefinition? {
                        return prioritizedDefinitions.firstOrNull { it.isScript(script) }
                    }

                    override fun getDefaultDefinition(): ScriptDefinition {
                        return prioritizedDefinitions.first()
                    }

                    override fun getKnownFilenameExtensions(): Sequence<String> {
                        return prioritizedDefinitions
                            .asSequence()
                            .map { it.fileExtension.removePrefix(".") }
                            .distinct()
                    }

                    override val currentDefinitions: Sequence<ScriptDefinition>
                        get() = prioritizedDefinitions.asSequence()

                    override fun isScript(script: SourceCode): Boolean {
                        return prioritizedDefinitions.any { it.isScript(script) }
                    }
                }
            )
        }
    }

    fun dispose() {
        Disposer.dispose(projectDisposable)
    }
}
