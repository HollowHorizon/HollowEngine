package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeHostReferenceSnapshot
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeHostValue
import com.sunnychung.lib.multiplatform.kotlite.model.SymbolTable
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots.*
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import java.util.*
import kotlin.reflect.KClass

object KatariHostReferences {
    private val referenceTypes = listOf(
        HostReferenceType(Player::class) { PlayerSnapshot.capture(it as Player) },
        HostReferenceType(NpcEntity::class) { NpcEntitySnapshot.capture(it as NpcEntity) },
        HostReferenceType(LivingEntity::class) { LivingEntitySnapshot.capture(it as LivingEntity) },
        HostReferenceType(Entity::class) { EntitySnapshot.capture(it as Entity) },
        HostReferenceType(MinecraftServer::class) { ServerSnapshot.capture(it as MinecraftServer) },
        HostReferenceType(Level::class) { LevelSnapshot.capture(it as Level) },
    )

    fun capture(value: Any, symbolTable: SymbolTable): NarrativeHostValue? {
        val snapshot = referenceTypes
            .firstOrNull { it.type.isInstance(value) }
            ?.capture(value)
            ?: return null
        return NarrativeHostValue(snapshot.typeId, snapshot, symbolTable)
    }

    suspend fun resolve(value: Any): Any {
        return when (value) {
            is NarrativeHostReferenceSnapshot -> value.restoreReference(KatariRestoreContext(currentServer))
            else -> value
        }
    }

    fun <T : Entity> resolveEntity(
        server: MinecraftServer,
        uuid: UUID,
        expectedClass: Class<T>,
    ): T? {
        return server.allLevels
            .asSequence()
            .mapNotNull { level -> level.getEntity(uuid) }
            .firstOrNull(expectedClass::isInstance)
            ?.let(expectedClass::cast)
    }

    suspend fun <T : Entity> awaitEntity(
        server: MinecraftServer,
        uuid: UUID,
        expectedClass: Class<T>,
    ): T {
        resolveEntity(server, uuid, expectedClass)?.let { return it }

        return EntityLoadedEvent.await { event ->
            event.entity.uuid == uuid && expectedClass.isInstance(event.entity)
        }.entity.let(expectedClass::cast)
    }

    suspend fun awaitPlayer(server: MinecraftServer, uuid: UUID): Player {
        server.playerList.getPlayer(uuid)?.let { return it }
        return PlayerEvent.Join.await { event -> event.player.uuid == uuid }.player
    }
}

private data class HostReferenceType(
    val type: KClass<*>,
    val capture: (Any) -> NarrativeHostReferenceSnapshot,
)
