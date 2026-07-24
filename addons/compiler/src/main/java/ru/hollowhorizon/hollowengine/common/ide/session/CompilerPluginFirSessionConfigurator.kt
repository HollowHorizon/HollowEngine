package ru.hollowhorizon.hollowengine.common.ide.session

import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaLibrarySourceModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaScriptModule
import org.jetbrains.kotlin.analysis.api.standalone.base.declarations.KotlinStandaloneFirCompilerPluginsProvider
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionConfigurator
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.getCompilerExtensions
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.extensions.*
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.resolve.providers.FirProvider
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirExtensionSyntheticFunctionInterfaceProvider
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.scopes.kotlinScopeProvider
import org.jetbrains.kotlin.fir.session.registerCommonComponentsAfterExtensionsAreConfigured
import ru.hollowhorizon.hollowengine.common.compiler.createHollowEngineCompilerPluginRegistrars
import kotlin.reflect.KClass

/**
 * Compiler-plugin integration shared by the Analysis API service and low-level FIR sessions.
 *
 * Kotlin's standalone plugin provider correctly supplies extensions to source and script modules.
 * Kotlin 2.4, however, registers them too late in script sessions to rebuild plugin-dependent
 * services and does not attach generated/synthetic symbol providers. Library sessions also need
 * library-safe plugin extensions to deserialize types such as `ComposableFunctionN`.
 */
internal class AnalysisCompilerPluginSupport private constructor(
    val provider: KotlinCompilerPluginsProvider,
    val sessionConfigurator: LLFirSessionConfigurator,
) {
    companion object {
        @OptIn(
            CompilerConfiguration.Internals::class,
            ExperimentalCompilerApi::class,
            PluginServicesInitialization::class,
        )
        fun create(): AnalysisCompilerPluginSupport {
            val extensionStorage = CompilerPluginRegistrar.ExtensionStorage()
            val configuration = CompilerConfiguration().apply {
                extensionsStorage = extensionStorage
                addAll(
                    CompilerPluginRegistrar.COMPILER_PLUGIN_REGISTRARS,
                    createHollowEngineCompilerPluginRegistrars(),
                )
            }
            val provider = KotlinStandaloneFirCompilerPluginsProvider(configuration)
            val configuredExtensions = configuration
                .getCompilerExtensions(FirExtensionRegistrar)
                .map(FirExtensionRegistrar::configure)
                .fold(BunchOfRegisteredExtensions.empty(), BunchOfRegisteredExtensions::plus)
            val libraryExtensions = configuredExtensions.extensions
                .filterKeys(LIBRARY_SAFE_PLUGIN_EXTENSION_TYPES::contains)

            return AnalysisCompilerPluginSupport(
                provider,
                CompilerPluginFirSessionConfigurator(libraryExtensions),
            )
        }
    }
}

private class CompilerPluginFirSessionConfigurator(
    private val libraryExtensions: Map<
        KClass<out FirExtension>,
        List<FirExtension.Factory<FirExtension>>,
    >,
) : LLFirSessionConfigurator {
    @OptIn(PluginServicesInitialization::class, SessionConfiguration::class)
    override fun configure(session: LLFirSession) {
        when {
            session.ktModule is KaScriptModule -> configureScriptSession(session)
            session.requiresLibraryPluginExtensions() -> configureLibrarySession(session)
        }
    }

    @OptIn(PluginServicesInitialization::class, SessionConfiguration::class)
    private fun configureLibrarySession(session: LLFirSession) {
        libraryExtensions.forEach { (extensionType, factories) ->
            session.extensionService.registerExtensions(extensionType, factories)
        }
        refreshPluginDependentComponents(session)
        installAdditionalProviders(
            session,
            listOfNotNull(createSyntheticFunctionProvider(session)),
            replaceWithBinaryLibraryProvider = session.kind == FirSession.Kind.Library,
        )
    }

    @OptIn(SessionConfiguration::class)
    private fun configureScriptSession(session: LLFirSession) {
        session.register(
            FirPredicateBasedProvider::class,
            FirPredicateBasedProviderImpl(session),
        )
        refreshPluginDependentComponents(session)

        val generatedDeclarations = AnalysisSymbolProviderBridge
            .createGeneratedDeclarationsProvider(session) as? FirSwitchableExtensionDeclarationsSymbolProvider
        if (generatedDeclarations != null) {
            session.register(
                FirSwitchableExtensionDeclarationsSymbolProvider::class,
                generatedDeclarations,
            )
        }
        installAdditionalProviders(
            session,
            listOfNotNull(
                generatedDeclarations,
                createSyntheticFunctionProvider(session),
            ),
            replaceWithBinaryLibraryProvider = false,
        )
    }

    private fun refreshPluginDependentComponents(session: LLFirSession) {
        session.registerCommonComponentsAfterExtensionsAreConfigured()
    }

    private fun createSyntheticFunctionProvider(
        session: LLFirSession,
    ): FirExtensionSyntheticFunctionInterfaceProvider? {
        return FirExtensionSyntheticFunctionInterfaceProvider.createIfNeeded(
            session,
            session.moduleData,
            session.kotlinScopeProvider,
        )
    }

    @OptIn(SessionConfiguration::class)
    private fun installAdditionalProviders(
        session: LLFirSession,
        additionalProviders: List<FirSymbolProvider>,
        replaceWithBinaryLibraryProvider: Boolean,
    ) {
        if (additionalProviders.isEmpty()) return

        val symbolProvider = AnalysisSymbolProviderBridge.appendProviders(
            session,
            session.symbolProvider,
            additionalProviders,
        )
        session.register(FirSymbolProvider::class, symbolProvider)
        if (replaceWithBinaryLibraryProvider) {
            session.register(
                FirProvider::class,
                AnalysisSymbolProviderBridge.createLibrarySessionProvider(symbolProvider),
            )
        }
    }
}

private fun LLFirSession.requiresLibraryPluginExtensions(): Boolean {
    return kind == FirSession.Kind.Library ||
        ktModule is KaLibraryModule ||
        ktModule is KaLibrarySourceModule
}

private val LIBRARY_SAFE_PLUGIN_EXTENSION_TYPES = setOf(
    FirFunctionTypeKindExtension::class,
    FirTypeAttributeExtension::class,
)
