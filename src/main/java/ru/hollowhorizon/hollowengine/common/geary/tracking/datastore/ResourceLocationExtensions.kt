package ru.hollowhorizon.hollowengine.common.geary.tracking.datastore

import com.mineinabyss.geary.datatypes.GearyComponent
import com.mineinabyss.geary.serialization.ComponentSerializers
import kotlinx.serialization.DeserializationStrategy
import net.minecraft.resources.ResourceLocation
import kotlin.reflect.KClass

inline fun <reified T : GearyComponent> ComponentSerializers.getResourceLocationFor(): ResourceLocation? =
    getSerialNameFor(T::class)?.toComponentKey()

fun <T : GearyComponent> ComponentSerializers.getSerializerForResource(
    location: ResourceLocation,
    baseClass: KClass<in T> = GearyComponent::class
): DeserializationStrategy<out T>? =
    getSerializerFor(location.toSerialName(), baseClass)


internal const val COMPONENT_PREFIX = "component."

fun String.isComponentKey(): Boolean {
    return if (this.contains(":")) {
        this.split(":")[1].startsWith(COMPONENT_PREFIX)
    } else {
        this.startsWith(COMPONENT_PREFIX)
    }
}
fun String.toComponentKey(): ResourceLocation {
    val location = ResourceLocation.tryParse(this)
        ?: error("Invalid resource location format: $this")

    return if (location.path.startsWith(COMPONENT_PREFIX)) location
    else ResourceLocation(location.namespace, "$COMPONENT_PREFIX${location.path}")
}

fun ResourceLocation.toSerialName(): String = "$namespace:${path.removePrefix(COMPONENT_PREFIX)}"