package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.*
import kotlin.coroutines.coroutineContext

@Serializable
@SerialName("hollowengine:katari/entity_ref")
data class KatariEntityRefSnapshot(
    val uuid: String,
    val dimension: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val kind: String,
) : ValueSnapshot()

@Serializable
@SerialName("hollowengine:katari/player_ref")
data class KatariPlayerRefSnapshot(
    val uuid: String,
    val dimension: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val kind: String,
) : ValueSnapshot()

@Serializable
@SerialName("hollowengine:katari/npc_ref")
data class KatariNpcRefSnapshot(
    val uuid: String,
    val dimension: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val kind: String,
) : ValueSnapshot()

@Serializable
@SerialName("hollowengine:katari/position")
data class KatariPositionSnapshot(
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String? = null,
) : ValueSnapshot()

data class KatariChatMessage(
    val player: KatariEntityRef,
    val message: String,
)

sealed interface KatariTarget {
    suspend fun position(server: MinecraftServer): Vec3
}

data class KatariPositionRef(
    val value: Vec3,
    val dimension: ResourceLocation? = null,
) : KatariTarget {
    override suspend fun position(server: MinecraftServer) = value

    fun snapshot() = KatariPositionSnapshot(value.x, value.y, value.z, dimension?.toString())
}

open class KatariEntityRef(
    val uuid: UUID,
    val dimension: ResourceLocation,
    val lastPosition: Vec3,
) : KatariTarget {
    override suspend fun position(server: MinecraftServer): Vec3 = resolve(server).position()

    open suspend fun resolve(server: MinecraftServer): Entity {
        resolveNow(server)?.let { return it }
        while (coroutineContext.isActive) {
            delay(50)
            resolveNow(server)?.let { return it }
        }
        error("Entity $uuid is not available")
    }

    fun resolveNow(server: MinecraftServer): Entity? {
        val levelKey = ResourceKey.create(Registries.DIMENSION, dimension)
        server.getLevel(levelKey)?.getEntity(uuid)?.let { return it }
        return server.allLevels.firstNotNullOfOrNull { it.getEntity(uuid) }
    }

    override fun toString() = "EntityRef($uuid)"
}

fun KatariEntityRef.snapshot() = KatariEntityRefSnapshot(
    uuid = uuid.toString(),
    dimension = dimension.toString(),
    x = lastPosition.x,
    y = lastPosition.y,
    z = lastPosition.z,
    kind = "entity",
)
fun KatariNpcRef.snapshot(): KatariNpcRefSnapshot = KatariNpcRefSnapshot(uuid.toString(), dimension.toString(), lastPosition.x, lastPosition.y, lastPosition.z, "npc")
fun KatariPlayerRef.snapshot(): KatariPlayerRefSnapshot = KatariPlayerRefSnapshot(uuid.toString(), dimension.toString(), lastPosition.x, lastPosition.y, lastPosition.z, "player")


class KatariNpcRef(
    uuid: UUID,
    dimension: ResourceLocation,
    lastPosition: Vec3,
) : KatariEntityRef(uuid, dimension, lastPosition) {
    override fun toString() = "NpcRef($uuid)"
}

class KatariPlayerRef(
    uuid: UUID,
    dimension: ResourceLocation,
    lastPosition: Vec3,
) : KatariEntityRef(uuid, dimension, lastPosition) {
    suspend fun resolvePlayer(server: MinecraftServer): Player = resolve(server) as? Player
        ?: error("Entity $uuid is not a player")

    override fun toString() = "PlayerRef($uuid)"
}

class KatariRestoreContext(val server: MinecraftServer) : ValueRestoreContext

fun Entity.toKatariRef(): KatariEntityRef {
    val dimension = level().dimension().location()
    val position = position()
    return when (this) {
        is NpcEntity -> KatariNpcRef(uuid, dimension, position)
        is Player -> KatariPlayerRef(uuid, dimension, position)
        else -> KatariEntityRef(uuid, dimension, position)
    }
}

fun Entity.toKatariHost(): com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue.HostObject {
    val ref = toKatariRef()
    val type = when (ref) {
        is KatariNpcRef -> "NpcRef"
        is KatariPlayerRef -> "PlayerRef"
        else -> "EntityRef"
    }
    return com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue.HostObject(type, ref)
}

fun KatariChatMessage.toKatariHost() =
    com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue.HostObject("ChatMessage", this)

fun KatariPositionRef.toKatariHost() =
    com.sunnychung.lib.multiplatform.kotlite.katari.KatariValue.HostObject("Position", this)

suspend fun KatariEntityRefSnapshot.restore(context: ValueRestoreContext): KatariEntityRef {
    val ref = KatariEntityRef(UUID.fromString(uuid), dimension.rl, Vec3(x, y, z))
    val server = (context as? KatariRestoreContext)?.server ?: return ref
    ref.resolve(server)
    return ref
}

suspend fun KatariNpcRefSnapshot.restore(context: ValueRestoreContext): KatariEntityRef {
    val ref = KatariNpcRef(UUID.fromString(uuid), dimension.rl, Vec3(x, y, z))
    val server = (context as? KatariRestoreContext)?.server ?: return ref
    ref.resolve(server)
    return ref
}

suspend fun KatariPlayerRefSnapshot.restore(context: ValueRestoreContext): KatariEntityRef {
    val ref = KatariPlayerRef(UUID.fromString(uuid), dimension.rl, Vec3(x, y, z))
    val server = (context as? KatariRestoreContext)?.server ?: return ref
    ref.resolve(server)
    return ref
}

fun KatariPositionSnapshot.restore(): KatariPositionRef {
    return KatariPositionRef(Vec3(x, y, z), dimension?.rl)
}

suspend fun Any.toTargetPosition(server: MinecraftServer): Vec3 {
    return when (this) {
        is KatariTarget -> position(server)
        is Entity -> position()
        else -> error("Unsupported target `$this`")
    }
}

suspend fun Any.toEntityOrNull(server: MinecraftServer): Entity? {
    return when (this) {
        is KatariEntityRef -> resolve(server)
        is Entity -> this
        else -> null
    }
}

fun Vec3.toPositionRef() = KatariPositionRef(this)

fun LivingEntity.dimensionId(): String = level().dimension().location().toString()
