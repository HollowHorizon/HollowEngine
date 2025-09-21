package ru.hollowhorizon.hollowengine.common.components.property

import ru.hollowhorizon.hollowengine.client.kool.addons.Renderer
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


class Property<V : Any>(var name: String?, internal val serializer: Class<V>, private val initializer: () -> V) :
    ReadWriteProperty<Component<*>, V> {
    internal var value: V? = null
    internal var renderer: Renderer<V>? = null
    internal var sync: Sync = Sync.ON_CHANGE
    internal var save: Save = Save.ALWAYS

    internal var copyOnDeath: Boolean = false
    private var onChange: Set<(V?, V) -> Unit> = hashSetOf()
    internal var changed: Boolean = false

    operator fun provideDelegate(thisRef: Component<*>, property: KProperty<*>): Property<V> {
        val name = name ?: property.name
        thisRef.properties[name] = this
        return this
    }

    override fun getValue(thisRef: Component<*>, property: KProperty<*>): V {
        if (value == null) value = initializer()
        return value!!
    }

    override fun setValue(thisRef: Component<*>, property: KProperty<*>, value: V) {
        onChange.forEach { it.invoke(this.value, value) }
        this.value = value
        changed = true
    }

    fun sync(sync: Sync) = apply { this.sync = sync }
    fun sync(sync: Boolean = true) = apply { this.sync = if (sync) Sync.ON_CHANGE else Sync.NEVER }
    fun save(save: Save) = apply { this.save = save }
    fun save(save: Boolean = true) = apply { this.save = if (save) Save.ALWAYS else Save.NEVER }

    fun copyOnDeath() = apply { this.copyOnDeath = true }
    fun onChange(block: (old: V?, new: V) -> Unit) = apply { this.onChange += block }

    fun renderer(renderer: Renderer<V>) = apply { this.renderer = renderer }

    fun <T> serialize(format: Format<T>): T? {
        val value = this.value ?: initializer().also { this.value = it }
        return format.serializeNoInline(value, serializer)
    }

    fun <T> deserialize(format: Format<T>, tag: T) {
        val newValue = format.deserializeNoInline(tag, serializer)
        onChange.forEach { it.invoke(this.value, newValue) }
        this.value = newValue
        changed = true
    }
}