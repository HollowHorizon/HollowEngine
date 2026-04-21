package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.api.AutoModelType
import ru.hollowhorizon.hollowengine.api.RegistryHolder
import ru.hollowhorizon.hollowengine.api.RegistryProvider
import java.util.function.Supplier

object CommonRegistryProvider : RegistryProvider<Any> {
    private lateinit var registry: RegistryProvider<Any>

    fun init(registry: RegistryProvider<Any>) {
        this.registry = registry
    }

    override fun register(
        location: ResourceLocation,
        registry: Registry<Any>?,
        model: AutoModelType?,
        generator: Supplier<Any>,
        type: Class<Any>,
    ): RegistryHolder<Any> {
        return this.registry.register(location, registry, model, generator, type)
    }
}