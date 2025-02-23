package ru.hollowhorizon.hollowengine.compiler.coroutine

import kotlinx.serialization.KSerializer
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import ru.hollowhorizon.hollowengine.compiler.pluginContext

class NonSerializableProperty<T>(
    private val initializers: Array<() -> T>,
) {
    private var value: T? = null
    var index = 0

    fun init() {
        value = initializers[index]()
    }

    fun get(): T {
        if (value == null) init()
        return value ?: error("Value cannot be deserialized!")
    }

    fun set(index: Int) {
        this.index = index
        init()
    }

    companion object {
        internal val TYPE by lazy {
            pluginContext.referenceClass(
                ClassId(
                    FqName("ru.hollowhorizon.hollowengine.compiler.coroutine"),
                    Name.identifier("NonSerializableProperty")
                )
            ) ?: error("Unreachable")
        }
    }
}