@file:JvmName("GearyHelper")

package ru.hollowhorizon.hollowengine.common.geary.api

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import java.util.LinkedHashMap
import kotlin.reflect.KClass

const val UNINITIALIZED_ENTITY_ID: Long = -1L

/**
 * Runtime component view over a Minecraft entity.
 * Keeps descriptor-aware storage so existing editor/codeblocks APIs can stay unchanged.
 */
class RuntimeEntityComponents internal constructor(
    private val entity: MCEntity,
) {
    fun allById(): LinkedHashMap<ResourceLocation, Any> = GearyRuntimeState.componentsById(entity)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: KClass<T>): T? {
        val id = ComponentDescriptorRegistry.idFor(type) ?: return null
        return allById()[id] as? T
    }

    inline fun <reified T : Any> get(): T? = get(T::class)

    fun <T : Any> set(component: T, type: KClass<out T>, noEvent: Boolean = false): T {
        val id = ComponentDescriptorRegistry.idFor(type)
            ?: error("Component descriptor not found for ${type.qualifiedName}")
        allById()[id] = component
        if (!noEvent) GearyRuntimeState.markDirty(entity)
        return component
    }

    inline fun <reified T : Any> set(component: T, noEvent: Boolean = false): T = set(component, T::class, noEvent)

    fun remove(type: KClass<*>, noEvent: Boolean = false) {
        val id = ComponentDescriptorRegistry.idFor(type) ?: return
        allById().remove(id)
        if (!noEvent) GearyRuntimeState.markDirty(entity)
    }
}

val MCEntity.entityId: Long
    get() = GearyRuntimeState.entityId(this)

val MCEntity.entity: RuntimeEntityComponents
    get() = RuntimeEntityComponents(this)

fun ensureEntity(level: Level, entity: MCEntity): Long = GearyRuntimeState.ensureEntity(level, entity)

fun bind(level: Level, entity: MCEntity, entityId: Int = entity.id, previousEntityId: Int = entity.id): Long =
    GearyRuntimeState.bind(level, entity, entityId, previousEntityId)

fun bindIfInitialized(level: Level, entity: MCEntity): Long? =
    GearyRuntimeState.bindIfInitialized(level, entity)

fun move(old: Level, new: Level, entity: Long, mcEntity: MCEntity): Long =
    GearyRuntimeState.move(old, new, entity, mcEntity)

fun removeEntity(level: Level, entity: Int, gearyEntity: Long = UNINITIALIZED_ENTITY_ID) {
    GearyRuntimeState.removeEntity(level, entity, gearyEntity)
}

@SubscribeEvent
fun onEntityLoaded(event: EntityLoadedEvent) {
    bindIfInitialized(event.entity.level(), event.entity)
}
