package ru.hollowhorizon.hollowengine.scripting

/**
 * Используется для переменных в скриптах
 */
open class DelegateProperty<T>(val initializer: () -> T) {
    var value: T? = null

    open fun get(): T {
        if (value == null) value = initializer()
        return value ?: throw IllegalStateException("Value is null")
    }

    open fun set(value: T) {
        this.value = value
    }

    fun serialize() {
    }

    fun deserialize() {
    }
}