package ru.hollowhorizon.hollowengine.common.components

import de.fabmax.kool.modules.ui2.UiScope
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.client.kool.addons.Renderer
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.registry.ComponentRegistry
import ru.hollowhorizon.hollowengine.common.components.system.ComponentEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.registry.system.keyOf
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.utils.serialization.Format
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation

abstract class Component<T : Any> {
    lateinit var provider: T
    val isClient get() = (provider as? Entity)?.level()?.isClientSide == true
    val properties: MutableMap<String, PropertyDelegate<*>> = mutableMapOf()
    val dependencies: MutableList<KClass<out Component<*>>> = mutableListOf()
    var enabled: Boolean = true

    inline fun <reified V : Any> property(noinline initializer: () -> V) = PropertyDelegate(V::class.java, initializer)


    inline fun <reified C : Component<*>> component(): C {
        val location = C::class.findAnnotation<ComponentMeta>()?.location?.rl
            ?: error("No ComponentMeta annotation found for ${C::class.simpleName}")
        val component = (provider as ComponentDispatcher).`hollowcore$components`.getOrPut(location) {
            ComponentRegistry[keyOf(location)]()
        } as C
        dependencies.add(component::class)
        return component
    }

    inline fun <reified C : Component<*>> requires(): C {
        val location = C::class.findAnnotation<ComponentMeta>()?.location?.rl
            ?: error("No ComponentMeta annotation found for ${C::class.simpleName}")
        val component = (provider as ComponentDispatcher).`hollowcore$components`[location] as? C
            ?: throw IllegalStateException("Required component ${C::class.simpleName} not found")
        dependencies.add(component::class)
        return component
    }

    open fun onAttach() {}
    open fun onDetach() {}

    open fun onEnabled() {}
    open fun onDisabled() {}

    fun enable() {
        if (!enabled) {
            enabled = true
            onEnabled()
            ComponentEvent.Enabled(this).post()
        }
    }

    fun disable() {
        if (enabled) {
            enabled = false
            onDisabled()
            ComponentEvent.Disabled(this).post()
        }
    }
}

fun interface Sync {
    fun shouldSync(property: PropertyDelegate<*>): Boolean

    object NEVER : Sync {
        override fun shouldSync(property: PropertyDelegate<*>): Boolean = false
    }

    object ON_CHANGE : Sync {
        override fun shouldSync(property: PropertyDelegate<*>): Boolean = property.changed
    }
}

fun interface Save {
    fun shouldSave(property: PropertyDelegate<*>): Boolean

    object ALWAYS : Save {
        override fun shouldSave(property: PropertyDelegate<*>): Boolean = true
    }

    object NEVER : Save {
        override fun shouldSave(property: PropertyDelegate<*>): Boolean = false
    }
}



class PropertyDelegate<V : Any>(internal val serializer: Class<V>, private val initializer: () -> V) :
    ReadWriteProperty<Component<*>, V> {
    internal var value: V? = null
    internal var renderer: Renderer<V>? = null
    internal var sync: Sync = Sync.ON_CHANGE
    internal var save: Save = Save.ALWAYS

    lateinit var name: String

    private var copyOnDeath: Boolean = false
    private var onChange: Set<(V?, V) -> Unit> = hashSetOf()
    internal var changed: Boolean = false

    operator fun provideDelegate(thisRef: Component<*>, property: KProperty<*>): PropertyDelegate<V> {
        thisRef.properties[property.name] = this
        name = property.name
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
        return if (value == null) null
        else format.serializeNoInline(value!!, serializer)
    }

    fun <T> deserialize(format: Format<T>, tag: T) {
        val newValue = format.deserializeNoInline(tag, serializer)
        onChange.forEach { it.invoke(this.value, newValue) }
        this.value = newValue
        changed = true
    }
}