package ru.hollowhorizon.hollowengine.common.registry

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Items
import ru.hollowhorizon.hollowengine.HollowCore.MODID
import ru.hollowhorizon.hollowengine.common.utils.HollowCreativeTab
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.properties.ReadOnlyProperty

open class HollowRegistry(val modId: String = MODID) {
    /**
     * Avoid fake NotNulls parameters like BlockEntityType.Builder::build
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> promise(): T = null as T

    inline fun <reified T : Any> register(
        location: ResourceLocation,
        autoModel: AutoModelType? = AutoModelType.DEFAULT,
        registry: Registry<in T>? = null,
        noinline registryEntry: (ResourceLocation) -> T,
    ): IRegistryHolder<T> {
        return createRegistry(
            location,
            registry,
            autoModel,
            { registryEntry(location) },
            T::class.java
        ) as IRegistryHolder<T>
    }

    inline fun <reified T : Any> register(
        id: String,
        autoModel: AutoModelType? = AutoModelType.DEFAULT,
        registry: Registry<in T>? = null,
        noinline registryEntry: (ResourceLocation) -> T,
    ): IRegistryHolder<T> = register("$modId:$id".rl, autoModel, registry, registryEntry)

    fun creativeTab(name: String, block: CreativeModeTab.Builder.() -> Unit = {}) = register(name) {
        HollowCreativeTab.builder()
            .icon { Items.DIRT.defaultInstance }
            .title("itemGroup.$name".mcTranslate)
            .apply { block() }
            .build()
    }
}

lateinit var createRegistry: (ResourceLocation, Registry<*>?, AutoModelType?, () -> Any, Class<*>) -> IRegistryHolder<*>

typealias IRegistryHolder<T> = ReadOnlyProperty<Any?, T>