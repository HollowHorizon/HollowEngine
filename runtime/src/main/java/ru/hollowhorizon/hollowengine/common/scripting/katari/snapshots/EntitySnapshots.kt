package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariRestoreContext
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.*

@Serializable
@SerialName("hollowengine:katari/npc")
@ScriptType("NpcEntity", Entity::class)
data class NpcEntitySnapshot(
    val uuid: @Serializable(ForStringUUID::class) UUID,
    val level: @Serializable(ForResourceLocation::class) ResourceLocation,
) : ValueSnapshot(), ScriptSnapshot<NpcEntity> {
    override suspend fun restore(context: ValueRestoreContext): NpcEntity {
        val server = (context as KatariRestoreContext).server
        val world = server.getLevel(ResourceKey.create(Registries.DIMENSION, level))

        world?.getEntity(uuid)?.let { return it as NpcEntity }

        val event =
            EntityLoadedEvent.await { it.entity.uuid == uuid && it.entity.level().dimension().location() == level }
        return event.entity as NpcEntity
    }

    companion object : ScriptSnapshotFactory<NpcEntity, NpcEntitySnapshot> {
        override fun capture(value: NpcEntity): NpcEntitySnapshot {
            return NpcEntitySnapshot(value.uuid, value.level().dimension().location())
        }
    }
}

@Serializable
@SerialName("hollowengine:katari/entity")
@ScriptType("Entity")
data class EntitySnapshot(
    val uuid: @Serializable(ForStringUUID::class) UUID,
    val level: @Serializable(ForResourceLocation::class) ResourceLocation,
) : ValueSnapshot(), ScriptSnapshot<Entity> {
    override suspend fun restore(context: ValueRestoreContext): Entity {
        val server = (context as KatariRestoreContext).server
        val world = server.getLevel(ResourceKey.create(Registries.DIMENSION, level))

        world?.getEntity(uuid)?.let { return it }

        val event =
            EntityLoadedEvent.await { it.entity.uuid == uuid && it.entity.level().dimension().location() == level }
        return event.entity
    }

    companion object : ScriptSnapshotFactory<Entity, EntitySnapshot> {
        override fun capture(value: Entity): EntitySnapshot {
            return EntitySnapshot(value.uuid, value.level().dimension().location())
        }
    }
}

@Serializable
@SerialName("hollowengine:katari/player")
@ScriptType("Player", Entity::class)
data class PlayerSnapshot(
    val uuid: @Serializable(ForStringUUID::class) UUID,
) : ValueSnapshot(), ScriptSnapshot<Player> {
    override suspend fun restore(context: ValueRestoreContext): Player {
        val server = (context as KatariRestoreContext).server
        server.playerList.getPlayer(uuid)?.let { return it as Player }
        val event = PlayerEvent.Join.await { it.player.uuid == uuid }
        return event.player
    }

    companion object : ScriptSnapshotFactory<Player, PlayerSnapshot> {
        override fun capture(value: Player): PlayerSnapshot {
            return PlayerSnapshot(value.uuid)
        }
    }
}
