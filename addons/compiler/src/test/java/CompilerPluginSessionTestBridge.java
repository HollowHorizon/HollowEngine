import com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule;
import org.jetbrains.kotlin.analysis.low.level.api.fir.providers.LLFirLibrarySessionProvider;
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSession;
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionCache;
import org.jetbrains.kotlin.fir.resolve.providers.FirProviderKt;
import org.jetbrains.kotlin.fir.types.FirFunctionTypeKindServiceKt;
import org.jetbrains.kotlin.name.FqName;

/**
 * Test-only Java bridge for low-level Analysis API declarations which are internal to Kotlin.
 */
final class CompilerPluginSessionTestBridge {
    private CompilerPluginSessionTestBridge() {
    }

    static LLFirSession getSession(Project project, KaModule module, boolean preferBinary) {
        return LLFirSessionCache.Companion.getInstance(project).getSession(module, preferBinary);
    }

    static boolean hasBinaryLibraryProvider(LLFirSession session) {
        return FirProviderKt.getFirProvider(session) instanceof LLFirLibrarySessionProvider;
    }

    static boolean hasComposeFunctionTypeKind(LLFirSession session) {
        return FirFunctionTypeKindServiceKt.getFunctionTypeService(session)
                .getFunctionKindPackageNames()
                .contains(new FqName("androidx.compose.runtime.internal"));
    }
}
