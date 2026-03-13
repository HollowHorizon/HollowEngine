package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

class VariableMap(
    private val onDirty: (() -> Unit)? = null,
) {
    private val variables = mutableMapOf<String, CompoundTag>()

    fun serialize(tag: CompoundTag) {
        copyTo(tag)
    }

    fun deserialize(tag: CompoundTag) {
        variables.clear()
        tag.allKeys.forEach { name ->
            variables[name] = tag.getCompound(name).copy()
        }
    }

    fun copyTo(tag: CompoundTag) {
        variables.forEach { (name, value) ->
            tag.put(name, value.copy())
        }
    }

    fun asCompoundTag(): CompoundTag = CompoundTag().also(::copyTo)

    fun getRawTag(name: String): Tag? = variables[name]?.get(VALUE_KEY)

    fun setRawTag(name: String, value: Tag?) {
        val wrapper = CompoundTag()
        value?.let { wrapper.put(VALUE_KEY, it.copy()) }
        variables[name] = wrapper
        onDirty?.invoke()
    }

    fun getTag(name: String): CompoundTag? = getRawTag(name) as? CompoundTag

    fun setTag(name: String, tag: CompoundTag) {
        setRawTag(name, tag.copy())
    }

    fun remove(name: String) {
        variables.remove(name)
        onDirty?.invoke()
    }

    operator fun contains(name: String): Boolean = variables.containsKey(name)

    val keys: Set<String>
        get() = variables.keys

    fun toList(): List<Pair<String, Any?>> = variables.map { (name, container) -> name to container.get(VALUE_KEY)?.toString() }

    fun entries(): Set<Map.Entry<String, CompoundTag>> = variables.entries

    companion object {
        const val VALUE_KEY = "value"
    }
}
