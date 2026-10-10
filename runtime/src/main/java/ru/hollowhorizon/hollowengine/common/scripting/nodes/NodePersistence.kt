package ru.hollowhorizon.hollowengine.common.scripting.nodes

import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.data.DataKey
import ru.hollowhorizon.hollowengine.common.data.dataKey
import ru.hollowhorizon.hollowengine.common.data.read
import ru.hollowhorizon.hollowengine.common.data.write
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * A node-local value that survives a world reload, restored when its delegate is declared so later
 * initializers in the script body can use it:
 *
 * ```kotlin
 * var radius by persisted("radius") { 3 }
 * ```
 */
context(script: NodeScript)
inline fun <reified T : Any> persisted(name: String, noinline default: () -> T): ReadWriteProperty<Any?, T> =
    persisted(script, dataKey<T>(name), default)

/** The non-inline half of [persisted]; call the delegate form instead. */
fun <T : Any> persisted(
    script: NodeScript,
    key: DataKey<T>,
    default: () -> T,
): ReadWriteProperty<Any?, T> = PersistedValue(key, default, script.binding.initialTag).also { holder ->
    script.onSaveHandlers += { context -> holder.save(context.tag) }
}

/** The storage behind [persisted], separate from the node wiring so it can be exercised on its own. */
internal class PersistedValue<T : Any>(
    private val key: DataKey<T>,
    private val default: () -> T,
    initialTag: CompoundTag? = null,
) : ReadWriteProperty<Any?, T> {
    private var value: T? = initialTag?.read(key)

    /** Whether this value was ever loaded or assigned, as opposed to merely falling back to [default]. */
    private var stored = value != null

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = current()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = assign(value)

    fun current(): T = value ?: default().also { value = it }

    fun assign(value: T) {
        this.value = value
        stored = true
    }

    fun save(tag: CompoundTag) {
        if (stored) value?.let { tag.write(key, it) }
    }
}
