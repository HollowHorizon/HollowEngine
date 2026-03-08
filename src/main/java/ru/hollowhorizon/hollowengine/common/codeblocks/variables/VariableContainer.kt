package ru.hollowhorizon.hollowengine.common.codeblocks.variables

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.serializer
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.KTypeExpressionType
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import java.util.*
import kotlin.reflect.KType

interface VariableContainer {
    fun set(value: Any?)
    suspend fun get(expectedType: ExpressionType): Any?
    fun save(tag: CompoundTag)
    fun load(tag: CompoundTag)
}

class LazyNbtVariableContainer : VariableContainer {
    private var rawValue: Tag? = null
    private var cachedType: KType? = null
    private var cachedValue: Any? = null

    override fun set(value: Any?) {
        rawValue = value?.let { NBTFormat.serializeNoInline(it, it.javaClass) }
        cachedType = null
        cachedValue = value
    }

    override suspend fun get(expectedType: ExpressionType): Any? {
        val requestedType = when (expectedType) {
            AnyType -> error("Variable type is ambiguous. Connect the variable block to a typed slot.")
            is KTypeExpressionType -> expectedType.kType
            else -> error("Unsupported variable type: $expectedType")
        }

        if (cachedType == requestedType) return cachedValue

        val storedValue = rawValue ?: error("Variable is not initialized")
        val deserialized = NBTFormat.deserialize(
            NBTFormat.serializersModule.serializer(requestedType),
            storedValue,
        )
        cachedType = requestedType
        cachedValue = deserialized
        return deserialized
    }

    override fun save(tag: CompoundTag) {
        rawValue?.let { tag.put("value", it) }
    }

    override fun load(tag: CompoundTag) {
        rawValue = tag.get("value")
        cachedType = null
        cachedValue = null
    }

    override fun toString(): String {
        return cachedValue?.toString() ?: rawValue?.toString() ?: "Uninitialized variable"
    }
}

class LivingEntityContainer<T : LivingEntity> : VariableContainer {
    var uuid: UUID? = null
    var levelKey: ResourceKey<Level>? = null

    override fun save(tag: CompoundTag) {
        uuid?.let { tag.putUUID("uuid", it) }
        levelKey?.let { tag.putString("level", it.location().toString()) }
    }

    override fun load(tag: CompoundTag) {
        if (tag.isEmpty) return
        uuid = tag.getUUID("uuid")
        levelKey = ResourceKey.create(Registries.DIMENSION, tag.getString("level").rl)
    }

    override fun set(value: Any?) {
        val entity = value as? T ?: error("Expected living entity, got ${value?.javaClass?.name}")
        uuid = entity.uuid
        levelKey = entity.level().dimension()
    }

    override suspend fun get(expectedType: ExpressionType): Any? {
        val level = levelKey?.let(currentServer::getLevel) ?: error("Level $levelKey is null!")
        while (currentCoroutineContext().isActive) {
            val entity = uuid?.let(level::getEntity)
            if (entity != null) return entity
            delay(50)
        }
        error("Entity $uuid not found!")
    }

    override fun toString(): String = "Entity $uuid"
}
