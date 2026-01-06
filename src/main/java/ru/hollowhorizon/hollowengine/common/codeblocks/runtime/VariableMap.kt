package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.codeblocks.variables.VariableContainer

class VariableMap {
    private val variables = mutableMapOf<String, VariableContainer<*>>()

    fun serialize(tag: CompoundTag) {
        variables.forEach { (name, value) ->
            tag.put(name, CompoundTag().apply(value::save))
        }
    }

    fun deserialize(tag: CompoundTag) {
        variables.forEach { (name, value) ->
            val variableTag = tag.getCompound(name)
            value.load(variableTag)
        }
    }

    operator fun set(name: String, value: VariableContainer<*>) {
        variables[name] = value
    }

    operator fun get(name: String): VariableContainer<*>? {
        return variables[name]
    }

    operator fun contains(name: String): Boolean {
        return variables.containsKey(name)
    }
}