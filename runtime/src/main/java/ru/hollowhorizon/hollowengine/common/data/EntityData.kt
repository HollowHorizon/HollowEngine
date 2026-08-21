package ru.hollowhorizon.hollowengine.common.data

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.attachments.api.AttachmentRegistry

/**
 * The persistent typed NBT document attached to any entity, players included:
 *
 * ```kotlin
 * val Visits = dataKey<Int>("mypack:visits") { 0 }
 * player.data.update(Visits) { it + 1 }
 * ```
 * */
@JvmInline
value class EntityData internal constructor(private val entity: Entity) {
    private val existing: NbtDataStore? get() = AttachmentRegistry.entityDataOrNull(entity)
    private val orCreate: NbtDataStore get() = AttachmentRegistry.entityData(entity)

    operator fun <T : Any> get(key: DataKey<T>): T? = existing?.get(key) ?: key.default()

    fun <T : Any> getOrPut(key: DataKey<T>, defaultValue: () -> T): T = orCreate.getOrPut(key, defaultValue)

    fun <T : Any> getOrPut(key: DataKey<T>): T = orCreate.getOrPut(key)

    operator fun <T : Any> set(key: DataKey<T>, value: T) {
        orCreate[key] = value
    }

    fun <T : Any> update(key: DataKey<T>, transform: (T) -> T): T = orCreate.update(key, transform)

    operator fun contains(key: DataKey<*>): Boolean = existing?.contains(key) == true

    fun remove(key: DataKey<*>): Boolean = existing?.remove(key) == true

    fun clear() {
        existing?.clear()
    }

    fun isEmpty(): Boolean = existing?.isEmpty() != false
}

/** The persistent typed NBT document attached to this entity. See [EntityData]. */
val Entity.data: EntityData get() = EntityData(this)
