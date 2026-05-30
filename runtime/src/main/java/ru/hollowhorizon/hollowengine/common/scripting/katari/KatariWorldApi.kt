package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.valueproviders.IntProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.shapes.CollisionContext
import ru.hollowhorizon.hollowengine.common.geary.api.findEntityByUuid
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.*
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForVec3
import ru.hollowhorizon.hollowengine.common.utils.rl
import java.util.*

enum class KatariWeather {
    Clear,
    Rain,
    Thunder,
}

enum class KatariRaycastHitType {
    Miss,
    Block,
    Entity,
}

@ScriptBinding
data class KatariRaycastHit @ScriptIgnore constructor(
    val type: KatariRaycastHitType,
    val position: Vec3,
    val block: Vec3?,
    val entity: Entity?,
    val distance: Double,
) {
    val miss: Boolean get() = type == KatariRaycastHitType.Miss
    val hasBlock: Boolean get() = type == KatariRaycastHitType.Block
    val hasEntity: Boolean get() = type == KatariRaycastHitType.Entity
}

@Serializable
@SerialName("hollowengine:katari/raycast_hit")
@ScriptType("RaycastHit")
data class KatariRaycastHitSnapshot(
    val type: KatariRaycastHitType,
    val position: @Serializable(ForVec3::class) Vec3,
    val block: @Serializable(ForVec3::class) Vec3?,
    val entityUuid: @Serializable(ForStringUUID::class) UUID?,
    val entityLevel: @Serializable(ForResourceLocation::class) ResourceLocation?,
    val distance: Double,
) : ValueSnapshot(), ScriptSnapshot<KatariRaycastHit> {
    override suspend fun restore(context: ValueRestoreContext): KatariRaycastHit {
        val server = (context as? KatariRestoreContext)?.server
        val entity = if (server != null && entityUuid != null && entityLevel != null) {
            server.getLevel(ResourceKey.create(Registries.DIMENSION, entityLevel))?.findEntityByUuid(entityUuid)
        } else {
            null
        }
        return KatariRaycastHit(type, position, block, entity, distance)
    }

    companion object : ScriptSnapshotFactory<KatariRaycastHit, KatariRaycastHitSnapshot> {
        override fun capture(value: KatariRaycastHit): KatariRaycastHitSnapshot {
            return KatariRaycastHitSnapshot(
                type = value.type,
                position = value.position,
                block = value.block,
                entityUuid = value.entity?.uuid,
                entityLevel = value.entity?.level()?.dimension()?.location(),
                distance = value.distance,
            )
        }
    }
}

@ScriptBinding("overworld")
val MinecraftServer.scriptOverworld: ServerLevel get() = overworld()

@ScriptBinding("nether")
val MinecraftServer.scriptNether: ServerLevel? get() = getLevel(Level.NETHER)

@ScriptBinding("end")
val MinecraftServer.scriptEnd: ServerLevel? get() = getLevel(Level.END)

@ScriptBinding("dimensionIds")
val MinecraftServer.scriptDimensionIds: List<String>
    get() = allLevels.map { it.dimension().location().toString() }

@ScriptBinding("dimension")
fun MinecraftServer.scriptDimension(id: String): ServerLevel? {
    return getLevel(ResourceKey.create(Registries.DIMENSION, id.rl))
}

@ScriptBinding("dimensionOrThrow")
fun MinecraftServer.scriptDimensionOrThrow(id: String): ServerLevel {
    return scriptDimension(id) ?: error("Dimension `$id` is not loaded")
}

@ScriptBinding("time")
val ServerLevel.scriptTime: Int get() = dayTime.floorMod(DAY_TICKS).toInt()

@ScriptBinding
fun ServerLevel.setTime(time: Int) {
    dayTime = time.toLong()
}

@ScriptBinding
fun ServerLevel.addTime(time: Int) {
    dayTime += time.toLong()
}

@ScriptBinding("weather")
val ServerLevel.scriptWeather: KatariWeather
    get() = when {
        isThundering -> KatariWeather.Thunder
        isRaining -> KatariWeather.Rain
        else -> KatariWeather.Clear
    }

@ScriptBinding
fun ServerLevel.setWeather(weather: KatariWeather, duration: Int = -1) {
    when (weather) {
        KatariWeather.Clear -> setWeatherParameters(weatherDuration(duration, ServerLevel.RAIN_DELAY), 0, false, false)
        KatariWeather.Rain -> setWeatherParameters(0, weatherDuration(duration, ServerLevel.RAIN_DURATION), true, false)
        KatariWeather.Thunder -> setWeatherParameters(0, weatherDuration(duration, ServerLevel.THUNDER_DURATION), true, true)
    }
}

@ScriptBinding
fun ServerLevel.setBlock(position: Vec3, block: String): Boolean {
    val state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), block, true).blockState
    return setBlock(BlockPos.containing(position), state, UPDATE_ALL)
}

@ScriptBinding
fun ServerLevel.getBlock(position: Vec3): String {
    return getBlockState(BlockPos.containing(position)).let { state ->
        BuiltInRegistries.BLOCK.getKey(state.block).toString()
    }
}

@ScriptBinding
fun ServerLevel.destroyBlock(position: Vec3, drop: Boolean = true): Boolean {
    return destroyBlock(BlockPos.containing(position), drop)
}

@ScriptBinding
fun ServerLevel.setWorldSpawn(position: Vec3, angle: Double = 0.0) {
    setDefaultSpawnPos(BlockPos.containing(position), angle.toFloat())
}

@ScriptBinding
fun Player.setSpawnPoint(level: ServerLevel, position: Vec3, angle: Double = 0.0, forced: Boolean = true) {
    val serverPlayer = this as? ServerPlayer ?: error("setSpawnPoint receiver must be a server player")
    serverPlayer.setRespawnPosition(level.dimension(), BlockPos.containing(position), angle.toFloat(), forced, false)
}

@ScriptBinding("raycast")
fun ServerLevel.raycastKatari(from: Vec3, to: Vec3, includeEntities: Boolean = true): KatariRaycastHit {
    return raycast(from, to, source = null, includeEntities = includeEntities)
}

@ScriptBinding("raycast")
fun Entity.raycastKatari(distance: Double = 32.0, includeEntities: Boolean = true): KatariRaycastHit {
    val level = level() as? ServerLevel ?: error("raycast is only available on server levels")
    val from = getEyePosition()
    val to = from.add(lookAngle.scale(distance))
    return level.raycast(from, to, source = this, includeEntities = includeEntities)
}

@ScriptBinding("spawn")
fun <T : Entity> ServerLevel.spawnKatariEntity(type: String, position: Vec3): T {
    @Suppress("UNCHECKED_CAST")
    return spawnEntity(type, position) as T
}

@ScriptBinding("entities")
fun <T : Entity> ServerLevel.scriptEntities(type: String? = null): List<T> {
    @Suppress("UNCHECKED_CAST")
    return entitiesByType(type) as List<T>
}

@ScriptBinding("entity")
fun <T : Entity> ServerLevel.scriptEntity(uuid: String, type: String? = null): T? {
    val entity = findEntityByUuid(UUID.fromString(uuid)) ?: return null
    if (!entity.matchesEntityType(type)) return null
    @Suppress("UNCHECKED_CAST")
    return entity as T
}

@ScriptBinding("entityOrThrow")
fun <T : Entity> ServerLevel.scriptEntityOrThrow(uuid: String, type: String? = null): T {
    return scriptEntity<T>(uuid, type)
        ?: error("Entity `$uuid` is not loaded in `${dimension().location()}`")
}

@ScriptBinding("entitiesIn")
fun <T : Entity> ServerLevel.scriptEntitiesIn(from: Vec3, to: Vec3, type: String? = null): List<T> {
    @Suppress("UNCHECKED_CAST")
    return entitiesIn(AABB(from, to), type) as List<T>
}

@ScriptBinding("entitiesNear")
fun <T : Entity> ServerLevel.scriptEntitiesNear(center: Vec3, radius: Double, type: String? = null): List<T> {
    require(radius >= 0.0) { "Entity search radius must be non-negative" }
    val bounds = AABB(center, center).inflate(radius)
    @Suppress("UNCHECKED_CAST")
    return entitiesIn(bounds, type) as List<T>
}

fun ServerLevel.spawnEntity(typeId: String, position: Vec3): Entity {
    val entityType = entityTypeOrThrow(typeId)
    val entity = entityType.create(this) ?: error("Entity type `$typeId` cannot be spawned")
    entity.setPos(position)
    addFreshEntity(entity)
    return entity
}

fun ServerLevel.entitiesByType(typeId: String? = null): List<Entity> {
    return getAllEntities()
        .filterIsInstance<Entity>()
        .filter { it.matchesEntityType(typeId) }
        .toList()
}

fun ServerLevel.entitiesIn(bounds: AABB, typeId: String? = null): List<Entity> {
    typeId?.let(::entityTypeOrThrow)
    return getEntities(null, bounds) { entity -> entity.matchesEntityType(typeId) }
}

private fun Entity.matchesEntityType(typeId: String?): Boolean {
    return typeId == null || type == entityTypeOrThrow(typeId)
}

private fun entityTypeOrThrow(typeId: String): EntityType<*> {
    return BuiltInRegistries.ENTITY_TYPE.getOptional(typeId.rl)
        .orElseThrow { IllegalArgumentException("Unknown entity type `$typeId`") }
}

private fun ServerLevel.raycast(
    from: Vec3,
    to: Vec3,
    source: Entity?,
    includeEntities: Boolean,
): KatariRaycastHit {
    val collisionContext = source?.let(CollisionContext::of) ?: CollisionContext.empty()
    val blockHit = clip(ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, collisionContext))
    val blockDistance = if (blockHit.type == HitResult.Type.MISS) Double.POSITIVE_INFINITY
    else from.distanceToSqr(blockHit.location)
    val entityHit = if (includeEntities) nearestEntityHit(from, to, source, blockDistance) else null

    if (entityHit != null) {
        return KatariRaycastHit(
            type = KatariRaycastHitType.Entity,
            position = entityHit.location,
            block = null,
            entity = entityHit.entity,
            distance = from.distanceTo(entityHit.location),
        )
    }

    if (blockHit.type != HitResult.Type.MISS) {
        return KatariRaycastHit(
            type = KatariRaycastHitType.Block,
            position = blockHit.location,
            block = Vec3.atLowerCornerOf(blockHit.blockPos),
            entity = null,
            distance = from.distanceTo(blockHit.location),
        )
    }

    return KatariRaycastHit(
        type = KatariRaycastHitType.Miss,
        position = to,
        block = null,
        entity = null,
        distance = from.distanceTo(to),
    )
}

private fun ServerLevel.nearestEntityHit(
    from: Vec3,
    to: Vec3,
    source: Entity?,
    maxDistanceSqr: Double,
): EntityRayHit? {
    val bounds = AABB(from, to).inflate(1.0)
    return getEntities(source, bounds) { entity -> entity.isPickable && !entity.isSpectator }
        .asSequence()
        .mapNotNull { entity ->
            val hit = entity.boundingBox.inflate(entity.pickRadius.toDouble()).clip(from, to).orElse(null)
                ?: return@mapNotNull null
            EntityRayHit(entity, hit, from.distanceToSqr(hit))
        }
        .filter { it.distanceSqr <= maxDistanceSqr }
        .minByOrNull { it.distanceSqr }
}

private fun ServerLevel.weatherDuration(duration: Int, provider: IntProvider): Int {
    return if (duration < 0) provider.sample(random) else duration
}

private fun Long.floorMod(divisor: Long): Long = ((this % divisor) + divisor) % divisor

private data class EntityRayHit(
    val entity: Entity,
    val location: Vec3,
    val distanceSqr: Double,
)

private const val DAY_TICKS = 24000L
private const val UPDATE_ALL = 3
