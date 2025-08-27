package ru.hollowhorizon.hc.common.events.registry

import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import ru.hollowhorizon.hc.common.events.Event

class RegisterEntityAttributesEvent : Event {
    private val attributes = HashMap<EntityType<out LivingEntity>, AttributeSupplier>()

    fun register(entity: EntityType<out LivingEntity>, attributes: AttributeSupplier) {
        this.attributes[entity] = attributes
    }

    fun getAttributes() = HashMap(attributes)
}