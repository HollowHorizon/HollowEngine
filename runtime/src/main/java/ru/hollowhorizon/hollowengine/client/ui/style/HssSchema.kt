package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.StyleModifier
import ru.hollowhorizon.hollowengine.client.ui.StylePropModifier
import ru.hollowhorizon.hollowengine.client.ui.UiStyleProperty

/** An inlay label for one token of a declaration value, positioned inside that value. */
data class HssValueHint(val offset: Int, val label: String)

/**
 * Everything the engine and the IDE know about one HSS property: its canonical name and
 * aliases, what its value looks like, and how it turns into a [Modifier].
 *
 * Declaring a property here is the only step needed to make it compile, complete,
 * validate and show inlay hints.
 */
class HssProperty internal constructor(
    val name: String,
    val aliases: List<String>,
    val summary: String,
    val syntax: HssSyntax,
    val examples: List<String>,
    private val compiler: HssDeclarationScope.() -> Modifier?,
) {
    /** `margin: <top> <right> <bottom> <left>` - the form shown in completion popups. */
    val signature: String get() = "$name: ${syntax.signature()}"

    fun compile(value: String): Modifier? = HssDeclarationScope(name, value).compiler()

    /** Splits [value] into the entries and words the grammar binds to slots. */
    fun entriesOf(value: String): List<HssValueEntry> {
        val entries = if (syntax.repeated) splitValueTokens(value, ',') else listOfNotNull(wholeValue(value))
        val separator = syntax.entry.wordSeparator
        return entries.map { entry ->
            val slices = if (separator == null) splitValueWords(entry.text) else splitValueTokens(entry.text, separator)
            val words = slices.map { word ->
                HssValueToken(word.text, entry.start + word.start, entry.start + word.end)
            }
            HssValueEntry(entry, words)
        }
    }

    /** Slot the token containing [offset] binds to; used to complete a value in place. */
    fun slotAt(value: String, offset: Int): HssSlot? {
        val entry = entriesOf(value).lastOrNull { it.token.start <= offset } ?: return syntax.slots.firstOrNull()
        val words = entry.words.map { it.text }
        val index = entry.words.indexOfFirst { offset in it.start..it.end }
            .takeIf { it >= 0 }
            ?: entry.words.count { it.end < offset }
        return syntax.entry.slotAt(words, index)
    }

    /**
     * Labels for the tokens of [value] whose meaning comes from their position. Values of a
     * single token say enough on their own, so they stay unannotated.
     */
    fun hints(value: String): List<HssValueHint> {
        val hints = ArrayList<HssValueHint>()
        for (entry in entriesOf(value)) {
            if (entry.words.size < 2) continue
            val words = entry.words.map { it.text }
            entry.words.forEachIndexed { index, token ->
                syntax.entry.labelAt(words, index)?.let { hints += HssValueHint(token.start, it) }
            }
        }
        return hints
    }

    private fun wholeValue(value: String): HssValueToken? {
        val start = value.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return null
        val end = value.indexOfLast { !it.isWhitespace() } + 1
        return HssValueToken(value.substring(start, end), start, end)
    }
}

/** One comma-separated entry of a value together with its whitespace-separated words. */
data class HssValueEntry(val token: HssValueToken, val words: List<HssValueToken>)

/** Receiver of a property's compile handler: the declaration being compiled. */
class HssDeclarationScope internal constructor(val property: String, val value: String) {
    /**
     * Read-modify-write contribution (composite props, structural patches). Keyed by the
     * declaration text so two identical declarations compare equal and share style caches.
     */
    fun style(vararg explicit: UiStyleProperty, writer: (UiStylePatch) -> Unit): Modifier =
        StyleModifier(explicit.toSet(), key = "$property:$value", writer)

    /** Single-value contribution; stacks across simultaneously active state rules. */
    fun <T> set(prop: UiStyleProp<T>, value: T): Modifier = StylePropModifier(prop, value)
}

/** Registry of every HSS property, assembled from the per-category declaration files. */
object HssSchema {
    val properties: List<HssProperty> by lazy {
        layoutHssProperties() +
                visualHssProperties() +
                interactionHssProperties() +
                widgetHssProperties() +
                textHssProperties() +
                motionHssProperties()
    }

    private val byName: Map<String, HssProperty> by lazy {
        buildMap {
            for (property in properties) {
                put(property.name, property)
                for (alias in property.aliases) put(alias, property)
            }
        }
    }

    /** Canonical names, in declaration order. */
    val names: List<String> get() = properties.map { it.name }

    /** Canonical names and aliases, sorted; what property completion offers. */
    val allNames: List<String> by lazy {
        properties.flatMap { listOf(it.name) + it.aliases }.distinct().sorted()
    }

    /** Property names (and groups) accepted by a `transitions` declaration. */
    val transitionTargets: List<String> by lazy { UiStyleProp.transitionPropertyNames }

    fun find(name: String): HssProperty? = byName[name.trim().lowercase()]

    fun compile(property: String, value: String): Modifier? = find(property)?.compile(value)
}

/** Collects the properties of one category; see the `Hss*Properties.kt` files. */
internal class HssPropertyBuilder {
    private val properties = ArrayList<HssProperty>()

    fun property(
        name: String,
        vararg aliases: String,
        summary: String,
        syntax: HssSyntax,
        examples: List<String> = emptyList(),
        compile: HssDeclarationScope.() -> Modifier?,
    ) {
        properties += HssProperty(name, aliases.toList(), summary, syntax, examples, compile)
    }

    fun build(): List<HssProperty> = properties
}

internal fun hssProperties(block: HssPropertyBuilder.() -> Unit): List<HssProperty> =
    HssPropertyBuilder().apply(block).build()
