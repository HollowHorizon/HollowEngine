package ru.hollowhorizon.hollowengine.common.attachments.api

/**
 * State that hangs off an entity for as long as that entity exists, created on demand and keyed by
 * whatever the caller likes.
 */
class RuntimeAttachments {
    private val values = LinkedHashMap<Any, Any>()

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrPut(key: Any, create: () -> T): T = values.getOrPut(key, create) as T

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(key: Any): T? = values[key] as T?

    fun remove(key: Any) {
        values.remove(key)
    }

    /** Drops everything whose key is not in [keys]; for callers that rebuild their set every frame. */
    fun retain(keys: Set<Any>) {
        values.keys.removeIf { it !in keys }
    }

    val isEmpty: Boolean get() = values.isEmpty()
}
