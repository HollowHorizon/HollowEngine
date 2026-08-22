package ru.hollowhorizon.hollowengine.common.utils.expressions

/**
 * How freely values of one type may stand in for another.
 *
 * Molang treats every value as a number, so `q.is_alive * 2` and `if (q.health)` have to keep working.
 * A dialogue condition comparing a string to a number is a mistake worth reporting.
 */
enum class Casts {
    IMPLICIT,
    EXPLICIT,
}

/** What to do with a name that no declaration answers to. */
fun interface References {
    fun resolve(name: String, span: Span, diagnostics: Diagnostics): Float?

    companion object {
        val FAIL = References { name, span, diagnostics ->
            diagnostics.error("Unresolved reference '$name'", span)
            null
        }

        fun logWithDefault(default: Float, onMissing: (String) -> Unit = {}) =
            References { name, _, _ ->
                onMissing(name)
                default
            }

        fun warnWithDefault(default: Float) = References { name, span, diagnostics ->
            diagnostics.warn("Unresolved reference '$name', using $default", span)
            default
        }
    }
}

class Options internal constructor() {
    var casts: Casts = Casts.IMPLICIT
        private set

    var unresolvedReferences: References = References.warnWithDefault(0f)
        private set

    var numberSuffixes: Map<String, Float> = emptyMap()
        private set

    fun casts(value: Casts) {
        casts = value
    }

    fun unresolvedReferences(value: References) {
        unresolvedReferences = value
    }

    fun numberSuffixes(value: Map<String, Float>) {
        numberSuffixes = value
    }
}
