package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDeclarationProviderFactory
import org.jetbrains.kotlin.analysis.api.platform.declarations.KotlinDirectInheritorsProvider
import org.jetbrains.kotlin.analysis.api.platform.projectStructure.KotlinProjectStructureProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.sessions.LLFirSessionCache
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.utils.superConeTypes
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.contains

/**
 * Finds the direct inheritors of a class, which the resolver needs to know the inheritors of a sealed
 * class.
 *
 * Kotlin ships `KotlinStandaloneFirDirectInheritorsProvider` for exactly this, but it insists that the
 * project's [KotlinDeclarationProviderFactory] *is* a `KotlinStandaloneDeclarationProviderFactory`,
 * and ours ([SimpleDeclarationProviderFactory]) merely wraps one so that open scripts contribute their
 * declarations too. The mismatch threw an `IllegalStateException` from deep inside resolution, which
 * surfaced as scripts losing their syntax highlighting entirely, most visibly any `*.node.kts` using
 * `onInteract`, whose resolution walks a sealed event hierarchy.
 *
 * So the same algorithm lives here, over our factory: candidates by supertype name (plus type
 * aliases), then a real check that the candidate does list the base class among its supertypes.
 */
@OptIn(LLFirInternals::class, SymbolInternals::class)
class HollowDirectInheritorsProvider(private val project: Project) : KotlinDirectInheritorsProvider {
    private val factory: SimpleDeclarationProviderFactory?
        get() = KotlinDeclarationProviderFactory.getInstance(project) as? SimpleDeclarationProviderFactory

    override fun getDirectKotlinInheritors(
        ktClass: KtClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean,
    ): Iterable<KtClassOrObject> {
        val declarations = factory ?: return emptyList()
        val classId = ktClass.getClassId() ?: return emptyList()
        val baseModule = KotlinProjectStructureProvider.getModule(project, ktClass, useSiteModule = null)
        val baseClass = classId.toFirSymbol(baseModule)?.fir as? FirClass ?: return emptyList()

        val names = mutableSetOf(classId.shortClassName)
        collectAliases(declarations, classId.shortClassName, names)

        val candidates = names.flatMap { declarations.directInheritorCandidates(it) }
        if (candidates.isEmpty()) return emptyList()

        return candidates.filter { isValidInheritor(it, baseClass, scope, includeLocalInheritors) }
    }

    /** `typealias Alias = Base` means a class may name the alias instead of the class itself. */
    private fun collectAliases(
        declarations: SimpleDeclarationProviderFactory,
        name: Name,
        collected: MutableSet<Name>,
    ) {
        declarations.inheritableTypeAliases(name).forEach { alias ->
            val aliasName = alias.nameAsSafeName
            if (collected.add(aliasName)) collectAliases(declarations, aliasName, collected)
        }
    }

    private fun isValidInheritor(
        candidate: KtClassOrObject,
        baseClass: FirClass,
        scope: GlobalSearchScope,
        includeLocalInheritors: Boolean,
    ): Boolean {
        if (!includeLocalInheritors && candidate.isLocal) return false
        if (!scope.contains(candidate)) return false

        val candidateClassId = candidate.getClassId() ?: return false
        val candidateModule = KotlinProjectStructureProvider.getModule(project, candidate, useSiteModule = null)
        val candidateClass = candidateClassId.toFirSymbol(candidateModule)?.fir as? FirClass ?: return false

        val baseClassId = baseClass.symbol.classId
        return candidateClass.superConeTypes.any { it.lookupTag.classId == baseClassId }
    }

    private fun ClassId.toFirSymbol(module: KaModule): FirClassLikeSymbol<*>? {
        val session = LLFirSessionCache.getInstance(project).getSession(module, preferBinary = true)
        return session.symbolProvider.getClassLikeSymbolByClassId(this)
    }
}
