package ru.hollowhorizon.hollowengine.common.utils

import kotlin.reflect.KProperty


class MutableLazy<T>(private val initializer: () -> T) {
    private var _value: Any? = UNINITIALIZED
    private var initialized = false

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (!initialized) {
            _value = initializer()
            initialized = true
        }
        @Suppress("UNCHECKED_CAST")
        return _value as T
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        _value = value
        initialized = true
    }

    fun isInitialized(): Boolean = initialized

    companion object {
        private object UNINITIALIZED
    }
}

fun <T> mutableLazy(initializer: () -> T) = MutableLazy(initializer)