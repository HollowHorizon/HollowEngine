package ru.hollowhorizon.hollowengine.common.geary.tracking.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityType
import ru.hollowhorizon.hollowengine.common.utils.rl

@JvmInline
@Serializable
@SerialName("geary:bind.entity_type")
value class BindToEntityType(val key: String) {
    val entityTypeFromRegistry: EntityType<*>
        get() = BuiltInRegistries.ENTITY_TYPE
            .get(key.rl)
}