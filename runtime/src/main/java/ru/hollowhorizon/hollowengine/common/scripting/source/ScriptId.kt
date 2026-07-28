package ru.hollowhorizon.hollowengine.common.scripting.source

/**
 * Identity of a single script: the namespace that owns it plus the path of the file relative to that
 * namespace's `scripts` directory.
 *
 * Node states are persisted in the world save under the string form of an id, so the sandbox namespace
 * is deliberately rendered without a prefix (see [ScriptRegistry.display]) and old save keys keep
 * resolving to the same script.
 */
data class ScriptId(val namespace: String, val path: String) {
    init {
        require(namespace.isNotBlank()) { "Script namespace must not be blank" }
        require(path.isNotBlank()) { "Script path must not be blank" }
    }

    /** Always qualified, suitable for cache keys and log messages. */
    val qualified: String get() = "$namespace$SEPARATOR$path"

    val fileName: String get() = path.substringAfterLast('/')

    override fun toString(): String = qualified

    companion object {
        const val SEPARATOR = ':'

        /**
         * Splits [raw] into a namespace and a path. An unqualified string belongs to [fallbackNamespace],
         * which is how paths written before namespaces existed keep working.
         */
        fun parse(raw: String, fallbackNamespace: String): ScriptId {
            val normalized = raw.replace('\\', '/').trim().removePrefix("/")
            val separator = normalized.indexOf(SEPARATOR)
            if (separator < 0) return ScriptId(fallbackNamespace, normalized)
            return ScriptId(
                namespace = normalized.substring(0, separator),
                path = normalized.substring(separator + 1).removePrefix("/"),
            )
        }
    }
}
