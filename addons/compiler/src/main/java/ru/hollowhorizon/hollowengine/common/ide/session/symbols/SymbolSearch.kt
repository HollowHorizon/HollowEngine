package ru.hollowhorizon.hollowengine.common.ide.session.symbols

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolVisibility
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaTypeParameterOwnerSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.types.Variance
import ru.hollowhorizon.hollowengine.common.ide.session.ScriptingAnalyzerImpl
import ru.hollowhorizon.hollowengine.common.ide.session.completion.getKotlinDeclarationsFromIndex
import ru.hollowhorizon.hollowengine.common.ide.session.definition.definitionForSymbol
import ru.hollowhorizon.hollowengine.common.ide.session.index.JavaClassNameIndex
import ru.hollowhorizon.hollowengine.common.scripting.ide.DefinitionLocation
import ru.hollowhorizon.hollowengine.common.scripting.ide.SymbolKind
import ru.hollowhorizon.hollowengine.common.scripting.ide.SymbolMatch

/**
 * Classes from the class-file index and Kotlin top-level declarations from the declaration index, both
 * seen from [file], ranked by how closely their simple name matches the query.
 */
internal fun ScriptingAnalyzerImpl.searchSymbols(file: KtFile, query: String, limit: Int): List<SymbolMatch> {
    val pattern = SymbolQuery.parse(query) ?: return emptyList()
    val found = LinkedHashMap<String, RankedMatch>()

    fun offer(rank: Int, match: SymbolMatch) {
        val key = match.qualifiedName + match.signature
        val existing = found[key]
        if (existing == null || rank < existing.rank) found[key] = RankedMatch(rank, match)
    }

    analyze(file) {
        getKotlinDeclarationsFromIndex(file) { name -> pattern.rank(name) != null }.forEach { symbol ->
            val match = symbolMatch(symbol) ?: return@forEach
            if (!pattern.qualifies(match.qualifiedName)) return@forEach
            val rank = pattern.rank(match.qualifiedName.substringAfterLast('.')) ?: return@forEach
            offer(rank, match)
        }
    }

    JavaClassNameIndex.getInstance(project).classes({ name -> pattern.rank(name) != null }).forEach { javaClass ->
        if (javaClass.name.asString().first().isDigit() || !pattern.qualifies(javaClass.fqName)) return@forEach
        val rank = pattern.rank(javaClass.name) ?: return@forEach
        val kind = if (javaClass.isAnnotation) SymbolKind.ANNOTATION else SymbolKind.CLASS
        offer(rank, SymbolMatch(kind, javaClass.fqName))
    }

    return found.values
        .sortedWith(
            compareBy<RankedMatch> { it.rank }
                .thenBy { originOrder(it.match.qualifiedName) }
                .thenBy { it.match.kind }
                .thenBy { it.match.qualifiedName.length }
                .thenBy { it.match.qualifiedName },
        )
        .take(limit)
        .map(RankedMatch::match)
}

/**
 * The declaration [qualifiedName] names: a class (nested ones spelled with dots), a member of one, or a
 * top-level function or property. Overloads resolve to the first one declared.
 */
internal fun ScriptingAnalyzerImpl.symbolSource(file: KtFile, qualifiedName: String): DefinitionLocation? {
    val fqName = runCatching { FqName(qualifiedName.trim()) }.getOrNull() ?: return null
    if (fqName.isRoot) return null
    val symbol = analyze(file) {
        findClassNamed(fqName)
            ?: findMember(fqName)
            ?: findTopLevelCallables(fqName.parent(), fqName.shortName()).firstOrNull()
    } ?: return null
    return definitionForSymbol(symbol)
}

private class RankedMatch(val rank: Int, val match: SymbolMatch)

/** Among equally close names, the game's and the engine's come first and the JDK's internals last. */
private fun originOrder(qualifiedName: String): Int = when {
    PREFERRED_PACKAGES.any(qualifiedName::startsWith) -> 0
    INTERNAL_PACKAGES.any(qualifiedName::startsWith) -> 2
    else -> 1
}

private val PREFERRED_PACKAGES = listOf("net.minecraft.", "com.mojang.", "ru.hollowhorizon.")
private val INTERNAL_PACKAGES = listOf("sun.", "com.sun.", "jdk.", "org.w3c.", "org.xml.", "javax.swing.", "java.awt.")

/** A query split into the simple name to match and the part of the qualifier it was written with. */
private class SymbolQuery(private val name: String, private val qualifier: String) {
    /** Lower is closer; null when [simpleName] does not match at all. */
    fun rank(simpleName: Name): Int? = rank(simpleName.asString())

    fun rank(simpleName: String): Int? = when {
        simpleName == name -> 0
        simpleName.equals(name, ignoreCase = true) -> 1
        simpleName.startsWith(name, ignoreCase = true) -> 2
        simpleName.contains(name, ignoreCase = true) -> 3
        else -> null
    }

    fun qualifies(qualifiedName: String): Boolean =
        qualifier.isEmpty() || qualifiedName.substringBeforeLast('.', "").contains(qualifier, ignoreCase = true)

    companion object {
        fun parse(query: String): SymbolQuery? {
            val trimmed = query.trim().trim('.')
            val name = trimmed.substringAfterLast('.')
            if (name.isEmpty()) return null
            return SymbolQuery(name, trimmed.substringBeforeLast('.', ""))
        }
    }
}

/** Null for what a script cannot reach: anything not public, and local or anonymous declarations. */
context(session: KaSession)
private fun symbolMatch(symbol: KaDeclarationSymbol): SymbolMatch? = with(session) {
    if (symbol.visibility != KaSymbolVisibility.PUBLIC) return null
    when (symbol) {
        is KaClassLikeSymbol -> SymbolMatch(SymbolKind.CLASS, symbol.classId?.asFqNameString() ?: return null)
        is KaCallableSymbol -> SymbolMatch(
            kind = if (symbol is KaFunctionSymbol) SymbolKind.FUNCTION else SymbolKind.PROPERTY,
            qualifiedName = symbol.callableId?.asSingleFqName()?.asString() ?: return null,
            signature = signature(symbol),
        )
        else -> null
    }
}

/**
 * A one-line declaration with its receiver and type parameters, and `= …` after every parameter that
 * has a default, which is what tells a caller which arguments it can leave out.
 */
context(session: KaSession)
private fun signature(symbol: KaCallableSymbol): String = with(session) {
    fun render(type: KaType) = type.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.INVARIANT)
    buildString {
        append(
            when (symbol) {
                is KaFunctionSymbol -> "fun "
                is KaPropertySymbol -> if (symbol.isVal) "val " else "var "
                else -> ""
            }
        )
        val typeParameters = (symbol as? KaTypeParameterOwnerSymbol)?.typeParameters.orEmpty()
        if (typeParameters.isNotEmpty()) {
            append(typeParameters.joinToString(", ", "<", "> ") { it.name.asString() })
        }
        symbol.receiverParameter?.let { receiver -> append(render(receiver.returnType)).append('.') }
        append(symbol.callableId?.callableName?.asString().orEmpty())
        if (symbol is KaFunctionSymbol) {
            append(symbol.valueParameters.joinToString(", ", "(", ")") { parameter ->
                val vararg = if (parameter.isVararg) "vararg " else ""
                val default = if (parameter.hasDefaultValue) " = …" else ""
                "$vararg${parameter.name.asString()}: ${render(parameter.returnType)}$default"
            })
        }
        append(": ").append(render(symbol.returnType))
    }
}

/** Tries every split of [fqName] into package and class, the longest package first. */
context(session: KaSession)
private fun findClassNamed(fqName: FqName): KaClassSymbol? = with(session) {
    val segments = fqName.pathSegments()
    for (packageSize in segments.size - 1 downTo 0) {
        val packageFqName = FqName.fromSegments(segments.take(packageSize).map(Name::asString))
        val relativeName = FqName.fromSegments(segments.drop(packageSize).map(Name::asString))
        findClass(ClassId(packageFqName, relativeName, isLocal = false))?.let { return it }
    }
    null
}

context(session: KaSession)
private fun findMember(fqName: FqName): KaDeclarationSymbol? = with(session) {
    val owner = findClassNamed(fqName.parent()) ?: return null
    val scope = owner.combinedDeclaredMemberScope
    scope.callables(fqName.shortName()).firstOrNull() ?: scope.classifiers(fqName.shortName()).firstOrNull()
}
