package ru.hollowhorizon.hollowengine.fabric.internal


import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.registry.system.Holder
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryState
import ru.hollowhorizon.hollowengine.common.registry.system.RegistryVersion
import kotlin.jvm.optionals.getOrNull


class FabricRegistry<T : Any>(val registry: Registry<T>) :
    ru.hollowhorizon.hollowengine.common.registry.system.MutableRegistry<T> {
    override val key: ResourceLocation = registry.key().location()
    override val state: RegistryState = RegistryState.REGISTERING
    override val size: Int get() = registry.size()

    override fun getId(value: T): Int = registry.getId(value)

    override fun getById(id: Int): T? = registry.getHolder(id).getOrNull()?.value()

    override fun getHolder(id: Int): Holder<T>? {
        val holder = registry.getHolder(id).getOrNull() ?: return null
        return Holder<T>(holder.key().location(), id).apply {
            this.value = holder.value()
        }
    }

    override fun getOrNull(key: ResourceLocation): T? {
        return registry.get(key)
    }

    override fun getHolder(key: ResourceLocation): Holder<T>? {
        val holder =
            registry.getHolder(ResourceKey.create(registry.key(), key)).getOrNull()
                ?: return null
        return Holder<T>(key, registry.getId(holder.value())).apply {
            this.value = holder.value()
        }
    }

    override fun contains(key: ResourceLocation): Boolean {
        return registry.containsKey(key)
    }

    override fun iterator(): Iterator<Holder<T>> {
        return registry.holders().map {
            Holder<T>(it.key().location(), getId(it.value())).apply {
                this.value = it.value()
            }
        }.iterator()
    }

    override val version: RegistryVersion = RegistryVersion(1, 0, 0)

    override fun register(
        key: ResourceLocation,
        supplier: () -> T,
    ): Holder<T> {
        val item = supplier()
        Registry.register(registry, key, item)
        return Holder<T>(key, registry.getId(item)).apply {
            this.value = item
        }
    }

    override fun unregister(key: ResourceLocation): Boolean {
        throw UnsupportedOperationException("Unregister is not supported in Fabric")
    }

    override fun bake() {
        // NO-OP
    }

    override fun freeze() {
        // NO-OP
    }

    override fun unfreeze() {
        // NO-OP
    }

    override fun unbake() {
        // NO-OP
    }
}