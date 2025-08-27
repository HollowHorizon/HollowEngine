package ru.hollowhorizon.hollowengine.common.utils.molang.runtime

import kotlin.reflect.KProperty

interface Variables {
    fun getOrNull(name: String): Variable?
    fun getOrPut(name: String, initialValue: Float = 0f): Variable
    operator fun get(name: String): Float = getOrNull(name)?.get() ?: Float.NaN
    operator fun set(name: String, value: Float) = getOrPut(name).set(value)
    fun fallbackBackTo(fallback: Variables): Variables = VariablesWithFallback(this, fallback)

    interface Variable {
        fun get(): Float
        fun set(value: Float)

        operator fun getValue(thisRef: Any?, property: KProperty<*>): Float = get()
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) = set(value)
    }
}

class VariablesMap : Variables {
    private val map = mutableMapOf<String, Variable>()

    override fun getOrNull(name: String): Variables.Variable? = map[name]

    override fun getOrPut(name: String, initialValue: Float): Variables.Variable =
        map.getOrPut(name) { Variable(initialValue) }

    private class Variable(var field: Float) : Variables.Variable {
        override fun get(): Float = field
        override fun set(value: Float) {
            field = value
        }
    }
}

private class VariablesWithFallback(val primary: Variables, val fallback: Variables) : Variables {
    override fun getOrNull(name: String): Variables.Variable? = primary.getOrNull(name) ?: fallback.getOrNull(name)

    override fun getOrPut(name: String, initialValue: Float): Variables.Variable =
        getOrNull(name) ?: primary.getOrPut(name, initialValue)
}