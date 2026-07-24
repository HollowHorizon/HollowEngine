package ru.hollowhorizon.hollowengine.common.ide.session;

import org.jetbrains.kotlin.analysis.low.level.api.fir.providers.LLFirLibrarySessionProvider;
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession;
import org.jetbrains.kotlin.analysis.low.level.api.fir.symbolProviders.LLFirSwitchableExtensionDeclarationsSymbolProvider;
import org.jetbrains.kotlin.analysis.low.level.api.fir.symbolProviders.LLModuleWithDependenciesSymbolProvider;
import org.jetbrains.kotlin.fir.resolve.providers.FirProvider;
import org.jetbrains.kotlin.fir.resolve.providers.FirSymbolProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Java access bridge for low-level Analysis API types which are {@code internal} in Kotlin but
 * intentionally public in JVM bytecode.
 */
public final class AnalysisSymbolProviderBridge {
    private AnalysisSymbolProviderBridge() {
    }

    public static FirSymbolProvider appendProviders(
            LLFirSession session,
            FirSymbolProvider currentProvider,
            List<? extends FirSymbolProvider> additionalProviders
    ) {
        if (!(currentProvider instanceof LLModuleWithDependenciesSymbolProvider moduleProvider)) {
            throw new IllegalStateException(
                    "Expected LLModuleWithDependenciesSymbolProvider, got " + currentProvider.getClass().getName()
            );
        }

        var providers = new ArrayList<FirSymbolProvider>(
                moduleProvider.getProviders().size() + additionalProviders.size()
        );
        providers.addAll(moduleProvider.getProviders());
        providers.addAll(additionalProviders);
        return new LLModuleWithDependenciesSymbolProvider(
                session,
                providers,
                moduleProvider.getDependencyProvider()
        );
    }

    public static FirSymbolProvider createGeneratedDeclarationsProvider(LLFirSession session) {
        return LLFirSwitchableExtensionDeclarationsSymbolProvider.Companion.createIfNeeded(session);
    }

    public static FirProvider createLibrarySessionProvider(FirSymbolProvider symbolProvider) {
        return new LLFirLibrarySessionProvider(symbolProvider);
    }
}
