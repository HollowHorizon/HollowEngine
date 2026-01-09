package ru.hollowhorizon.hollowengine.common.codeblocks.variables

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.KSerializer
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.*
import kotlin.coroutines.coroutineContext


interface VariableContainer<T : Any> {
    fun set(value: T)
    suspend fun get(): T

    fun save(tag: CompoundTag)
    fun load(tag: CompoundTag)
}

class SerializableVariableContainer<T : Any>(val serializer: KSerializer<T>, defaultValue: T?) : VariableContainer<T> {
    var value: T? = defaultValue

    override fun set(value: T) {
        this.value = value
    }

    override suspend fun get(): T {
        return value ?: error("Variable ($serializer) is null!")
    }

    override fun save(tag: CompoundTag) {
        value?.let {
            tag.put("value", NBTFormat.serialize(serializer, it))
        }
    }

    override fun load(tag: CompoundTag) {
        if (tag.contains("value")) {
            value = NBTFormat.deserialize(serializer, tag.get("value")!!)
        }
    }

    override fun toString(): String {
        return value?.toString() ?: "Variable of type ${serializer::class.simpleName} (not yet initialized)"
    }
}

class LivingEntityContainer<T : LivingEntity> : VariableContainer<T> {
    var uuid: UUID? = null
    var levelKey: ResourceKey<Level>? = null

    override fun save(tag: CompoundTag) {
        uuid?.let { tag.putUUID("uuid", it) }
        levelKey?.let { tag.putString("level", it.location().toString()) }
    }

    override fun load(tag: CompoundTag) {
        if (tag.isEmpty) return

        uuid = tag.getUUID("uuid")
        val levelId = tag.getString("level")
        levelKey = ResourceKey.create(Registries.DIMENSION, levelId.rl)
    }

    override fun set(value: T) {
        uuid = value.uuid
        levelKey = value.level().dimension()
    }

    override suspend fun get(): T {
        val level = levelKey?.let(currentServer::getLevel) ?: error("Level $levelKey is null!")

        while (coroutineContext.isActive) {
            val findEntity = level.getEntity(uuid)
            if (findEntity != null) {
                return findEntity as T
            }

            delay(50)
        }

        error("Entity $uuid not found!")
    }

    override fun toString(): String {
        return "Entity $uuid"
    }
}