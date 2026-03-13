package ru.hollowhorizon.hollowengine.common.codeblocks.variables

import kotlinx.serialization.serializer
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.KTypeExpressionType
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.utils.serialization.serializeNoInline
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import kotlin.reflect.KClass

fun serializeVariableValue(value: Any?): Tag? {
    return when (value) {
        null -> null
        is Entity -> EntityVariableContainer.toTag(value)
        else -> NBTFormat.serializeNoInline(value, value.javaClass)
    }
}

suspend fun deserializeVariableValue(rawValue: Tag?, expectedType: ExpressionType, server: MinecraftServer): Any? {
    val requestedType = when (expectedType) {
        AnyType -> error("Variable type is ambiguous. Connect the variable block to a typed slot.")
        is KTypeExpressionType -> expectedType.kType
        else -> error("Unsupported variable type: $expectedType")
    }

    val storedValue = rawValue ?: return null
    val requestedClass = requestedType.classifier as? KClass<*>
    if (requestedClass != null && Entity::class.java.isAssignableFrom(requestedClass.java)) {
        return EntityVariableContainer.resolve(server, storedValue, requestedClass)
    }

    return NBTFormat.deserialize(
        NBTFormat.serializersModule.serializer(requestedType),
        storedValue,
    )
}

private object EntityVariableContainer {
    private const val KIND_KEY = "kind"
    private const val ENTITY_KIND = "entity_ref"
    private const val UUID_KEY = "uuid"
    private const val DIMENSION_KEY = "dimension"

    fun toTag(entity: Entity): CompoundTag {
        return CompoundTag().apply {
            putString(KIND_KEY, ENTITY_KIND)
            putUUID(UUID_KEY, entity.uuid)
            putString(DIMENSION_KEY, entity.level().dimension().location().toString())
        }
    }

    suspend fun resolve(server: MinecraftServer, rawValue: Tag, requestedClass: KClass<*>): Entity? {
        val reference = fromTag(rawValue as? CompoundTag) ?: return null
        resolveNow(server, reference, requestedClass)?.let { return it }

        return await<EntityLoadedEvent> { event ->
            event.uuid == reference.uuid && requestedClass.java.isInstance(event.entity)
        }.entity
    }

    private fun fromTag(tag: CompoundTag?): EntityReference? {
        if (tag == null || tag.getString(KIND_KEY) != ENTITY_KIND || !tag.hasUUID(UUID_KEY)) return null
        val dimension = tag.getString(DIMENSION_KEY).takeIf(String::isNotBlank)?.let(ResourceLocation::tryParse)
        return EntityReference(tag.getUUID(UUID_KEY), dimension)
    }

    private fun resolveNow(
        server: MinecraftServer,
        reference: EntityReference,
        requestedClass: KClass<*>,
    ): Entity? {
        reference.dimension
            ?.let { ResourceKey.create(Registries.DIMENSION, it) }
            ?.let(server::getLevel)
            ?.getEntity(reference.uuid)
            ?.takeIf { requestedClass.java.isInstance(it) }
            ?.let { return it }

        return server.allLevels
            .asSequence()
            .mapNotNull { it.getEntity(reference.uuid) }
            .firstOrNull { requestedClass.java.isInstance(it) }
    }

    private data class EntityReference(
        val uuid: java.util.UUID,
        val dimension: ResourceLocation?,
    )
}
