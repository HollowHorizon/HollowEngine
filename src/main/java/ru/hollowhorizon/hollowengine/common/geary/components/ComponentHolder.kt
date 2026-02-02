package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.KSerializer
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

class ComponentHolder<T: Any>(val value: KClass<T>, val serializer: KSerializer<T>) {
    fun create(): T = value.createInstance()
}