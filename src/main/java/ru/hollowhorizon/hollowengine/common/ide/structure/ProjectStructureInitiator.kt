package ru.hollowhorizon.hollowengine.common.ide.structure

import com.intellij.core.CoreApplicationEnvironment
import com.intellij.mock.MockProject
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartTypePointerManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.smartPointers.SmartPointerManagerImpl
import com.intellij.psi.impl.smartPointers.SmartTypePointerManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.containers.ContainerUtil
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
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.platform.resolution.KaResolutionActivityTracker
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneAnnotationsResolverFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneDeclarationProviderMerger
import org.jetbrains.kotlin.analysis.api.standalone.base.modification.KotlinStandaloneModificationTrackerFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.packages.KotlinStandalonePackageProviderFactory
import org.jetbrains.kotlin.analysis.api.standalone.base.permissions.KotlinStandaloneAnalysisPermissionOptions
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.FirStandaloneServiceRegistrar
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.KtStaticModuleDependentsProvider
import org.jetbrains.kotlin.analysis.api.standalone.base.projectStructure.StandaloneProjectFactory
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinBuiltInDecompiler
import org.jetbrains.kotlin.analysis.decompiler.psi.KotlinClassFileDecompiler
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.extensions.ProjectExtensionDescriptor
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.scripting.compiler.plugin.definitions.CliScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.compiler.plugin.extensions.ScriptLoweringExtension
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectory
import kotlin.io.path.exists

// this class is a singleton as it initiates Application which is static itself
object ProjectStructureInitiator {
    fun initiateProjectStructure(): ProjectStructure {
        val key = "idea.home.path"
        if (System.getProperty(key) == null) {
            System.setProperty(
                key,
                File("hollowengine").toPath()
                    .resolve("ideaHomePath")
                    .also {
                        if (!it.exists()) {
                            it.createDirectory()
                        }
                    }
                    .absolutePathString(),
            )
        }

        val projectDisposable = Disposer.newDisposable("AnalysisAPI.project")
        val kotlinCoreProjectEnvironment =
            StandaloneProjectFactory.createProjectEnvironment(
                projectDisposable,
                KotlinCoreApplicationEnvironmentMode.Production,
            )

        val project: MockProject = kotlinCoreProjectEnvironment.project

        CoreApplicationEnvironment.registerExtensionPoint(
            project.extensionArea,
            KaResolveExtensionProvider.EP_NAME.name,
            KaResolveExtensionProvider::class.java,
        )

        KotlinCoreEnvironment.underApplicationLock {
            val applicationEnvironment = kotlinCoreProjectEnvironment.environment
            val application = applicationEnvironment.application
            if (application.getServiceIfCreated(KotlinAnalysisPermissionOptions::class.java) == null) {
                applicationEnvironment.registerApplicationService(
                    KotlinAnalysisPermissionOptions::class.java,
                    KotlinStandaloneAnalysisPermissionOptions(),
                )
            }

            if (application.getServiceIfCreated(KaAnalysisPermissionRegistry::class.java) == null) {
                applicationEnvironment.registerApplicationService(
                    KaAnalysisPermissionRegistry::class.java,
                     object : KaAnalysisPermissionRegistry {
                         override var explicitAnalysisRestriction: KaAnalysisPermissionRegistry.KaExplicitAnalysisRestriction? = null
                         override var isAnalysisAllowedOnEdt: Boolean = true
                         override var isAnalysisAllowedInWriteAction: Boolean = true
                     },
                )
            }

            if (application.getServiceIfCreated(KaResolutionActivityTracker::class.java) == null) {
                applicationEnvironment.registerApplicationService(
                    KaResolutionActivityTracker::class.java,
                    Class.forName("org.jetbrains.kotlin.analysis.low.level.api.fir.lazy.resolve.LLFirResolutionActivityTracker").newInstance() as KaResolutionActivityTracker,
                )
            }

            ClassFileDecompilers.getInstance().EP_NAME.point.apply {
                registerExtension(
                    KotlinClassFileDecompiler(),
                    LoadingOrder.FIRST,
                    applicationEnvironment.parentDisposable,
                )
                registerExtension(
                    KotlinBuiltInDecompiler(),
                    LoadingOrder.FIRST,
                    applicationEnvironment.parentDisposable,
                )
            }
        }

        val essentialLibraries = createEssentialLibraries(kotlinCoreProjectEnvironment)

        val projectStructureProvider = ProjectStructureProviderImpl()

        for (library in essentialLibraries.allLibraries) {
            for (file in library.files) {
                projectStructureProvider.setModule(file, library.kaModule)
            }
        }
        KotlinCoreEnvironment.registerProjectExtensionPoints(project.extensionArea)

        project.registerService(SmartTypePointerManager::class.java, SmartTypePointerManagerImpl::class.java)
        project.registerService(SmartPointerManager::class.java, SmartPointerManagerImpl::class.java)

        project.registerService(KotlinProjectStructureProvider::class.java, projectStructureProvider)
        project.registerService(
            KotlinModuleDependentsProvider::class.java,
            KtStaticModuleDependentsProvider(emptyList()),
        )

        val declarationFactory = ProjectDeclarationFactoryImpl()

        registerProjectServices(kotlinCoreProjectEnvironment, essentialLibraries, declarationFactory)

        CoreApplicationEnvironment.registerExtensionPoint(
            project.extensionArea,
            PsiTreeChangeListener.EP.name,
            PsiTreeChangeAdapter::class.java,
        )

        val structure = ProjectStructure(
            kotlinCoreProjectEnvironment,
            essentialLibraries,
            Builtins(kotlinCoreProjectEnvironment),
            projectStructureProvider,
            projectDisposable,
        )
        declarationFactory.projectStructure = structure
        return structure
    }

    private fun createEssentialLibraries(
        kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
    ): ProjectEssentialLibraries =
        ProjectEssentialLibraries(
            stdlib =
                EssentialLibrary.Companion.create(
                    listOf(Paths.get(System.getProperty("kotlin.java.stdlib.jar"))),
                    kotlinCoreProjectEnvironment,
                    "stdlib",
                    isSdk = false,
                ),
            jdk =
                EssentialLibrary.Companion.create(
                    buildList {
                        addAll(LibraryUtils.findClassesFromJdkHome(Paths.get(System.getProperty("java.home")), isJre = true))
                        addAll(LibraryUtils.findClassesFromJdkHome(Paths.get(System.getProperty("java.home")), isJre = false))
                    },
                    kotlinCoreProjectEnvironment,
                    "JDK",
                    isSdk = true,
                ),
        )

    private fun registerProjectServices(
        kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
        essentialLibraries: ProjectEssentialLibraries,
        declarationFactory: ProjectDeclarationFactoryImpl,
    ) {
        val project = kotlinCoreProjectEnvironment.project
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

            registerService(KotlinDeclarationProviderFactory::class.java, declarationFactory)
            registerService(
                ScriptDefinitionProvider::class.java,
                CliScriptDefinitionProvider()
            )
            //        registerService(
            //            KotlinDirectInheritorsProvider::class.java,
            //            KotlinStandaloneFirDirectInheritorsProvider(this),
            //        )
            registerService(
                KotlinDeclarationProviderMerger::class.java,
                KotlinStandaloneDeclarationProviderMerger(this),
            )
            registerService(
                KotlinPackageProviderFactory::class.java,
                KotlinStandalonePackageProviderFactory(project, emptyList()), /*TODO ???*/
            )


            registerService(
                KotlinPackagePartProviderFactory::class.java,
                KotlinStaticPackagePartProviderFactory(
                    StandaloneProjectFactory.createPackagePartsProvider(
                        StandaloneProjectFactory.getAllBinaryRoots(
                            essentialLibraries.kaModules,
                            kotlinCoreProjectEnvironment.environment,
                        ))),
            )


            /*
            private fun initialiseVirtualFileFinderServices(
                environment: KotlinCoreProjectEnvironment,
                modules: List<KaModule>,
                sourceFiles: List<PsiFileSystemItem>,
                languageVersionSettings: LanguageVersionSettings,
                jdkHome: Path?,
            )
             */
            StandaloneProjectFactory::class.java.getDeclaredMethod(
                "initialiseVirtualFileFinderServices",
                KotlinCoreProjectEnvironment::class.java,
                List::class.java,
                List::class.java,
                LanguageVersionSettings::class.java,
                Path::class.java
            ).apply {
                isAccessible = true
            }.invoke(
                StandaloneProjectFactory,
                kotlinCoreProjectEnvironment,
                essentialLibraries.kaModules,
                emptyList<Any>(),
                LanguageVersionSettingsImpl.DEFAULT,
                null,
            )

            registerService(KotlinCompilerPluginsProvider::class.java, KotlinCompilerPluginsProviderImpl::class.java)

            registerService(
                KotlinPlatformSettings::class.java,
                object : KotlinPlatformSettings {
                    override val deserializedDeclarationsOrigin: KotlinDeserializedDeclarationsOrigin
                        get() = KotlinDeserializedDeclarationsOrigin.STUBS
                },
            )
        }
    }
}

private class KotlinCompilerPluginsProviderImpl : KotlinCompilerPluginsProvider {
    override fun <T : Any> getRegisteredExtensions(
        module: KaModule,
        extensionType: ProjectExtensionDescriptor<T>
    ): List<T> {
        return when (extensionType) {
            IrGenerationExtension -> {
                listOf(
                    ScriptLoweringExtension(),
                )
            }

            else -> emptyList()
        } as List<T>
    }

    override fun isPluginOfTypeRegistered(
        module: KaModule,
        pluginType: KotlinCompilerPluginsProvider.CompilerPluginType
    ): Boolean {
        return pluginType == KotlinCompilerPluginsProvider.CompilerPluginType.ASSIGNMENT
    }
}

private class KotlinStaticPackagePartProviderFactory(
    private val packagePartProvider: (GlobalSearchScope) -> PackagePartProvider,
) : KotlinPackagePartProviderFactory {
    private val cache = ContainerUtil.createConcurrentSoftMap<GlobalSearchScope, PackagePartProvider>()

    override fun createPackagePartProvider(scope: GlobalSearchScope): PackagePartProvider {
        return cache.getOrPut(scope) {
            packagePartProvider(scope)
        }
    }
}
