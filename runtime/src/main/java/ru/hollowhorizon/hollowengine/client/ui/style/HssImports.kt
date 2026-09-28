package ru.hollowhorizon.hollowengine.client.ui.style

import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess

/** Reads the text of a stylesheet an `@import` names, or null when there is none. */
fun interface HssImportReader {
    fun read(location: String): String?
}

/**
 * Where imported stylesheets come from: the resource packs and the project folder, the way every
 * stylesheet is read, and the classpath when there is no game around (tests, tools).
 */
object HssImports {
    var reader: HssImportReader = HssImportReader { location ->
        val id = ResourceLocation.tryParse(location) ?: return@HssImportReader null
        runCatching { HollowUiResourceAccess.readText(id) }.getOrNull()
            ?: HssImports::class.java.getResourceAsStream("/assets/${id.namespace}/${id.path}")
                ?.bufferedReader()?.use { it.readText() }
    }
}

/** A `$name` inside a value. */
internal val HssVariableReference = Regex("""\$([A-Za-z_][A-Za-z0-9_-]*)""")

/**
 * What a stylesheet sees once its imports are read: every variable in scope, and the rules and
 * keyframes the imports bring. Imports are read depth first and each only once, so two sheets that
 * import the same palette, or a palette imported in a cycle, are not read twice.
 */
class HssScope internal constructor(
    val variables: Map<String, HssScopedVariable>,
    val sets: Map<String, HssDeclarationSet>,
    val importedRules: List<HssRule>,
    val importedKeyframes: List<HssKeyframes>,
    val imported: List<String>,
    val failures: List<Pair<HssImport, String>>,
) {
    /**
     * [value] with every variable written in, variables inside variables included. A name that is
     * not in scope, or that refers back to itself, is left as it is and fails where it is used.
     */
    fun substitute(value: String): String = substitute(value, HashSet())

    /**
     * [declarations] with every `@apply $set;` replaced by the set's declarations, sets inside sets
     * included, and every value with its variables written in.
     */
    fun expand(declarations: List<HssDeclaration>): List<HssDeclaration> = expand(declarations, HashSet())

    private fun expand(declarations: List<HssDeclaration>, applying: MutableSet<String>): List<HssDeclaration> =
        declarations.flatMap { declaration ->
            if (!declaration.isApply) return@flatMap listOf(declaration.copy(value = substitute(declaration.value)))
            val name = declaration.value.trim().removePrefix("$")
            val set = sets[name] ?: return@flatMap emptyList()
            if (!applying.add(name)) return@flatMap emptyList()
            expand(set.declarations, applying).also { applying.remove(name) }
        }

    private fun substitute(value: String, visiting: MutableSet<String>): String {
        if ('$' !in value) return value
        return HssVariableReference.replace(value) { match ->
            val name = match.groupValues[1]
            val variable = variables[name]
            if (variable == null || !visiting.add(name)) return@replace match.value
            substitute(variable.value, visiting).also { visiting.remove(name) }
        }
    }

    companion object {
        /** The scope of [document], reading its imports with [reader]. */
        fun of(document: HssDocument, reader: HssImportReader = HssImports.reader): HssScope {
            val variables = LinkedHashMap<String, HssScopedVariable>()
            val sets = LinkedHashMap<String, HssDeclarationSet>()
            val rules = ArrayList<HssRule>()
            val keyframes = ArrayList<HssKeyframes>()
            val read = LinkedHashSet<String>()
            val failures = ArrayList<Pair<HssImport, String>>()

            fun visit(imports: List<HssImport>) {
                for (import in imports) {
                    if (!read.add(import.location)) continue
                    val text = reader.read(import.location)
                    if (text == null) {
                        failures += import to "No stylesheet '${import.location}'"
                        continue
                    }
                    val imported = try {
                        parseHss(text)
                    } catch (error: HssParseException) {
                        failures += import to "'${import.location}' does not parse: ${error.messageText}"
                        continue
                    }
                    visit(imported.imports)
                    imported.variables.forEach { variables[it.name] = HssScopedVariable(it.name, it.value, import.location) }
                    imported.sets.forEach { sets[it.name] = it }
                    rules += imported.rules
                    keyframes += imported.keyframes
                }
            }

            visit(document.imports)
            document.variables.forEach { variables[it.name] = HssScopedVariable(it.name, it.value, null) }
            document.sets.forEach { sets[it.name] = it }
            return HssScope(variables, sets, rules, keyframes, read.toList(), failures)
        }
    }
}

/** A variable in scope, and the stylesheet it came from; null for the stylesheet itself. */
data class HssScopedVariable(val name: String, val value: String, val source: String?)

/**
 * The document ready to compile: the imported rules first, as if they were written at the top, and
 * every value with its variables written in.
 */
fun HssDocument.resolved(scope: HssScope = HssScope.of(this)): HssDocument {
    val imported = scope.importedRules.mapIndexed { index, rule -> rule.copy(order = index) }
    val own = rules.map { rule -> rule.copy(order = imported.size + rule.order) }
    return HssDocument(
        rules = (imported + own).map { rule -> rule.copy(declarations = scope.expand(rule.declarations)) },
        keyframes = (scope.importedKeyframes + keyframes).map { keyframes ->
            keyframes.copy(frames = keyframes.frames.map { frame -> frame.copy(declarations = scope.expand(frame.declarations)) })
        },
    )
}
