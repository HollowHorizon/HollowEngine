package ru.hollowhorizon.hollowengine.common.codeblocks.variables

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializerOrNull
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import kotlin.coroutines.coroutineContext


interface VariableContainer<T : Any> {
    val type: String
    var value: T?

    fun save(tag: CompoundTag)
    suspend fun load(tag: CompoundTag)
}

class SerializableVariableContainer<T : Any>(val serializer: KSerializer<T>) : VariableContainer<T> {
    override val type = "hollowengine:serializable_value"
    override var value: T? = null

    override fun save(tag: CompoundTag) {
        value?.let {
            tag.put("value", NBTFormat.serialize(serializer, it))
        }
    }

    override suspend fun load(tag: CompoundTag) {
        value = if (tag.contains("value")) {
            NBTFormat.deserialize(serializer, tag.get("value")!!)
        } else {
            null
        }
    }
}

class LivingEntityContainer<T : LivingEntity> : VariableContainer<T> {
    override val type = "hollowengine:living_entity"
    override var value: T? = null

    override fun save(tag: CompoundTag) {
        value?.let {
            tag.putUUID("uuid", it.uuid)
            tag.putString("level", it.level().dimension().location().toString())
        }
    }

    override suspend fun load(tag: CompoundTag) {
        if (tag.isEmpty) return

        val uuid = tag.getUUID("uuid")
        val levelId = tag.getString("level")
        val level = currentServer.getLevel(ResourceKey.create(Registries.DIMENSION, levelId.rl))
            ?: error("Level $levelId not found!")

        while (coroutineContext.isActive) {
            val findEntity = level.getEntity(uuid)
            if (findEntity != null) {
                value = findEntity as T
                return
            }

            delay(50)
        }
    }
}

@OptIn(InternalSerializationApi::class)
fun Any.isSerializable(): Boolean {
    return this::class.serializerOrNull() != null
}