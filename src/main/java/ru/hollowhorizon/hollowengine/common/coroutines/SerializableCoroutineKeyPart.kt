package ru.hollowhorizon.hollowengine.common.coroutines

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import kotlin.coroutines.CoroutineContext

sealed interface SerializableCoroutineKeyPart {
    fun token(): String

    data class Context(val key: CoroutineContext.Key<*>) : SerializableCoroutineKeyPart {
        override fun token(): String = key::class.java.name
    }

    data class Value(val key: CoroutineContext.Key<*>, val value: Any) : SerializableCoroutineKeyPart {
        override fun token(): String = "${key::class.java.name}=$value"
    }
}

infix fun CoroutineContext.Key<*>.with(value: Any): SerializableCoroutineKeyPart =
    SerializableCoroutineKeyPart.Value(this, value)

operator fun CoroutineContext.Key<*>.unaryPlus(): SerializableCoroutineKeyPart =
    SerializableCoroutineKeyPart.Context(this)

class SerializableCoroutineKey private constructor(
    private val parts: List<String>,
) {
    init {
        require(parts.isNotEmpty()) { "Coroutine key must contain at least one part" }
        require(parts.none { it.isBlank() }) { "Coroutine key parts must not be blank" }
    }

    fun save(tag: CompoundTag) {
        val list = ListTag()
        parts.forEach { list.add(StringTag.valueOf(it)) }
        tag.put("parts", list)
    }

    operator fun plus(part: SerializableCoroutineKeyPart): SerializableCoroutineKey {
        return SerializableCoroutineKey(parts + part.token())
    }

    override fun equals(other: Any?): Boolean = other is SerializableCoroutineKey && parts == other.parts
    override fun hashCode(): Int = parts.hashCode()
    override fun toString(): String = "SerializableCoroutineKey(parts=$parts)"

    companion object {
        fun of(vararg parts: SerializableCoroutineKeyPart): SerializableCoroutineKey {
            return SerializableCoroutineKey(parts.map { it.token() })
        }

        fun of(vararg keys: CoroutineContext.Key<*>): SerializableCoroutineKey {
            return SerializableCoroutineKey(keys.map { it::class.java.name })
        }

        fun fromTag(tag: CompoundTag): SerializableCoroutineKey? {
            val list = tag.getList("parts", 8)
            if (!list.isEmpty()) {
                return SerializableCoroutineKey(list.map { it.asString })
            }

            // Backward compatibility for previous experimental format
            val owner = tag.getString("owner")
            val name = tag.getString("name")
            if (owner.isNotBlank() && name.isNotBlank()) {
                return SerializableCoroutineKey(listOf(owner, name))
            }

            val id = tag.getString("id")
            if (id.isNotBlank()) {
                return SerializableCoroutineKey(listOf(id))
            }

            return null
        }
    }
}

operator fun SerializableCoroutineKeyPart.plus(other: SerializableCoroutineKeyPart): SerializableCoroutineKey =
    SerializableCoroutineKey.of(this, other)

operator fun CoroutineContext.Key<*>.plus(other: CoroutineContext.Key<*>): SerializableCoroutineKey =
    SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(this), SerializableCoroutineKeyPart.Context(other))

operator fun CoroutineContext.Key<*>.plus(part: SerializableCoroutineKeyPart): SerializableCoroutineKey =
    SerializableCoroutineKey.of(SerializableCoroutineKeyPart.Context(this), part)

operator fun SerializableCoroutineKey.plus(key: CoroutineContext.Key<*>): SerializableCoroutineKey =
    plus(SerializableCoroutineKeyPart.Context(key))
