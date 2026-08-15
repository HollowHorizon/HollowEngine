@file:Suppress("DEPRECATION")

package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.K1SpecificScriptingServiceAccessor
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.Closeable
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.asSuccess
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.implicitReceivers
import kotlin.script.experimental.api.onSuccess
import kotlin.script.experimental.api.with

private const val ATTACH_ANNOTATION = "ru.hollowhorizon.hollowengine.common.scripting.annotations.Attach"

/**
 * Kotlin 2.4's legacy IDE refinement cannot construct annotations containing class literals. It still returns the
 * otherwise valid refined configuration, so this provider restores only the missing Attach receiver from PSI.
 */
@OptIn(K1SpecificScriptingServiceAccessor::class)
internal class ClassLiteralScriptConfigurationsProvider(
    project: Project,
    private val definitions: ScriptDefinitionProvider,
    private val classpath: List<Path>,
) : ScriptConfigurationsProvider(project), Closeable {
    private val cache = object : LinkedHashMap<String, CacheEntry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 32
    }
    private val classLoader = lazy {
        URLClassLoader(
            classpath.map { path -> path.toUri().toURL() }.toTypedArray(),
            ClassLiteralScriptConfigurationsProvider::class.java.classLoader,
        )
    }

    @Synchronized
    override fun getScriptCompilationConfiguration(
        scriptSource: SourceCode,
        providedConfiguration: ScriptCompilationConfiguration?,
    ): ScriptCompilationConfigurationResult? {
        val locationId = scriptSource.locationId ?: scriptSource.name ?: return null
        val textHash = scriptSource.text.hashCode()
        cache[locationId]?.takeIf { it.textHash == textHash }?.let { return it.result }

        val definition = definitions.findDefinition(scriptSource) ?: return null
        val refined = refineScriptCompilationConfiguration(
            scriptSource,
            definition,
            project,
            providedConfiguration,
        )
        val result = refined.addClassLiteralReceiver(scriptSource)
        cache[locationId] = CacheEntry(textHash, result)
        return result
    }

    override fun close() {
        if (classLoader.isInitialized()) classLoader.value.close()
        cache.clear()
    }

    private fun ScriptCompilationConfigurationResult.addClassLiteralReceiver(
        source: SourceCode,
    ): ScriptCompilationConfigurationResult {
        val file = (source as? KtFileScriptSource)?.ktFile ?: return this
        return onSuccess { wrapper ->
            val configuration = wrapper.configuration ?: return@onSuccess wrapper.asSuccess()
            val receiver = file.findAttachReceiver(configuration) ?: return@onSuccess wrapper.asSuccess()
            if (configuration[ScriptCompilationConfiguration.implicitReceivers]
                    .orEmpty()
                    .any { it.typeName == receiver }
            ) {
                return@onSuccess wrapper.asSuccess()
            }

            ScriptCompilationConfigurationWrapper(
                wrapper.script,
                configuration.with {
                    implicitReceivers.append(KotlinType(receiver))
                },
            ).asSuccess()
        }
    }

    private fun KtFile.findAttachReceiver(baseConfiguration: ScriptCompilationConfiguration): String? {
        val attachAliases = importDirectives.mapNotNull { directive ->
            val importPath = directive.importPath ?: return@mapNotNull null
            if (importPath.fqName.asString() != ATTACH_ANNOTATION) return@mapNotNull null
            directive.aliasName ?: importPath.fqName.shortName().asString()
        }.toMutableSet()
        val attachNameIsShadowed = importDirectives.any { directive ->
            val importPath = directive.importPath ?: return@any false
            !importPath.isAllUnder &&
                    (directive.aliasName ?: importPath.fqName.shortName().asString()) == "Attach" &&
                    importPath.fqName.asString() != ATTACH_ANNOTATION
        }
        if (!attachNameIsShadowed) attachAliases += "Attach"
        val classLiteral = annotationEntries.asSequence()
            .filter { entry -> entry.isAttachAnnotation(attachAliases) }
            .mapNotNull { entry -> entry.valueArguments.singleOrNull()?.getArgumentExpression() as? KtClassLiteralExpression }
            .firstOrNull()
            ?: return null
        val typeName = classLiteral.receiverExpression?.text?.replace("`", "") ?: return null
        return resolveTypeName(typeName, baseConfiguration)
    }

    private fun KtAnnotationEntry.isAttachAnnotation(aliases: Set<String>): Boolean {
        val typeName = typeReference?.text?.replace("`", "") ?: return false
        return typeName == ATTACH_ANNOTATION || typeName in aliases
    }

    private fun KtFile.resolveTypeName(
        typeName: String,
        baseConfiguration: ScriptCompilationConfiguration,
    ): String? {
        val imported = buildList {
            importDirectives.forEach { directive ->
                val importPath = directive.importPath ?: return@forEach
                if (!importPath.isAllUnder) {
                    add(ImportCandidate(importPath.fqName.asString(), directive.aliasName))
                }
            }
            baseConfiguration[ScriptCompilationConfiguration.defaultImports].orEmpty().forEach { path ->
                if (!path.endsWith(".*")) add(ImportCandidate(path, null))
            }
        }
        imported.firstNotNullOfOrNull { candidate -> candidate.resolve(typeName) }?.let { return it }

        if (typeName.isLoadable()) return typeName

        val wildcardPackages = buildSet {
            importDirectives.mapNotNullTo(this) { directive ->
                directive.importPath?.takeIf { it.isAllUnder }?.fqName?.asString()
            }
            baseConfiguration[ScriptCompilationConfiguration.defaultImports].orEmpty()
                .filter { it.endsWith(".*") }
                .forEach { path -> add(path.removeSuffix(".*")) }
        }
        return wildcardPackages
            .map { packageName -> "$packageName.$typeName" }
            .filter { candidate -> candidate.isLoadable() }
            .singleOrNull()
    }

    private fun String.isLoadable(): Boolean {
        var candidate = this
        while (true) {
            if (runCatching { Class.forName(candidate, false, classLoader.value) }.isSuccess) return true
            val separator = candidate.lastIndexOf('.')
            if (separator < 0) return false
            candidate = candidate.substring(0, separator) + '$' + candidate.substring(separator + 1)
        }
    }

    private data class CacheEntry(val textHash: Int, val result: ScriptCompilationConfigurationResult?)

    private data class ImportCandidate(val fqName: String, val alias: String?) {
        fun resolve(typeName: String): String? {
            val firstSegment = typeName.substringBefore('.')
            val importedName = alias ?: fqName.substringAfterLast('.')
            if (firstSegment != importedName) return null
            return fqName + typeName.removePrefix(firstSegment)
        }
    }
}
