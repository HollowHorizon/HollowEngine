package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.LazyNbtVariableContainer
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer

class VariableMap(
    private val onDirty: (() -> Unit)? = null,
) {
    private val variables = mutableMapOf<String, VariableContainer>()

    fun serialize(tag: CompoundTag) {
        variables.forEach { (name, value) ->
            tag.put(name, CompoundTag().also(value::save))
        }
    }

    fun deserialize(tag: CompoundTag) {
        tag.allKeys.forEach { name ->
            val variable = variables.getOrPut(name, ::LazyNbtVariableContainer)
            variable.load(tag.getCompound(name))
        }
    }

    operator fun set(name: String, value: VariableContainer) {
        variables[name] = value
        onDirty?.invoke()
    }

    operator fun get(name: String): VariableContainer? = variables[name]

    operator fun contains(name: String): Boolean = variables.containsKey(name)

    fun getOrPut(name: String, defaultValue: () -> VariableContainer): VariableContainer {
        return variables.getOrPut(name, defaultValue)
    }

    fun remove(name: String) {
        variables.remove(name)
        onDirty?.invoke()
    }

    val keys: Set<String>
        get() = variables.keys

    fun toList(): List<Pair<String, Any?>> = variables.map { (name, container) -> name to container.toString() }

    fun entries(): Set<Map.Entry<String, VariableContainer>> = variables.entries
}
