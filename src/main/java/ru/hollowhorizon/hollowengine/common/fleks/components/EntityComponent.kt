package ru.hollowhorizon.hollowengine.common.fleks.components

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.ComponentType
import net.minecraft.world.entity.Entity

class EntityComponent(val entity: Entity) : Component<EntityComponent> {
    override fun type() = EntityComponent

    companion object : ComponentType<EntityComponent>()
}