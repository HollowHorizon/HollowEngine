package ru.hollowhorizon.hollowengine.common.components.property

import ru.hollowhorizon.hollowengine.client.kool.addons.Renderer
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty


class Property<V : Any>(
    val component: Component<*>,
    name: String?,
    internal val serializer: Class<V>,
    private val initializer: () -> V,
) :
    ReadWriteProperty<Any?, V> {
    lateinit var name: String

    init {
        name?.let { this.name = it }
    }

    private var value: V? = null
    private var renderCreator: (() -> Renderer<V>)? = null
    var hasRenderer = false
        private set
    val renderer by lazy {
        assert(hasRenderer) { "No renderer set for property $name" }
        renderCreator!!()
    }
    internal var sync: Sync = Sync.ON_CHANGE
    internal var save: Save = Save.ALWAYS

    internal var copyOnDeath: Boolean = false
    private var onChange: Set<(V?, V) -> Unit> = hashSetOf()

    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>): Property<V> {
        if (!this::name.isInitialized) this.name = property.name
        component.properties[name] = this
        return this
    }

    fun get(): V {
        val value = this.value ?: initializer().also { this.value = it }
        return value
    }

    fun set(value: V) {
        onChange.forEach { it.invoke(this.value, value) }
        this.value = value
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): V {
        return get()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
        set(value)
        if (sync == Sync.ON_CHANGE) component.changedProperties.add(name)
    }

    fun sync(sync: Sync) = apply { this.sync = sync }
    fun sync(sync: Boolean = true) = apply { this.sync = if (sync) Sync.ON_CHANGE else Sync.NEVER }
    fun save(save: Save) = apply { this.save = save }
    fun save(save: Boolean = true) = apply { this.save = if (save) Save.ALWAYS else Save.NEVER }

    fun copyOnDeath() = apply { this.copyOnDeath = true }
    fun onChange(block: (old: V?, new: V) -> Unit) = apply { this.onChange += block }

    fun renderer(renderer: () -> Renderer<V>) = apply { this.renderCreator = renderer; this.hasRenderer = true }

    fun <T> serialize(format: Format<T>): T? {
        val value = this.value ?: initializer().also { this.value = it }
        return format.serializeNoInline(value, serializer)
    }

    fun <T> deserialize(format: Format<T>, tag: T) {
        val newValue = format.deserializeNoInline(tag, serializer)
        onChange.forEach { it.invoke(this.value, newValue) }
        this.value = newValue
    }
}