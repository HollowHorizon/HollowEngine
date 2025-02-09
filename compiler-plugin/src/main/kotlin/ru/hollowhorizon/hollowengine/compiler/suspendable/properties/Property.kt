package ru.hollowhorizon.hollowengine.compiler.suspendable.properties

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

interface Property<T> {
    fun get(): T
    fun init()
}

@Serializable
class SerializableProperty<T>(val value: @Contextual T) : Property<T> {
    override fun get(): T = value

    override fun init() {}

    override fun toString(): String {
        return value.toString()
    }
}

class NonSerializableProperty<T>(val loader: () -> T) : Property<T> {
    var value: T? = null

    override fun init() {
        value = loader()
    }

    override fun get(): T {
        if (value == null) init()
        return value ?: loader()
    }
}