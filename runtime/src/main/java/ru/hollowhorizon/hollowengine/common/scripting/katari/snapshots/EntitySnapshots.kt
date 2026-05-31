package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.NarrativeHostReferenceSnapshot
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.entity.EntityLoadedEvent
import ru.hollowhorizon.hollowengine.common.events.factory.await
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariHostReferences
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariRestoreContext
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForStringUUID
import java.util.UUID

@Serializable
@SerialName("hollowengine:katari/npc")
@ScriptType("NpcEntity", LivingEntity::class)
data class NpcEntitySnapshot(
    val uuid: @Serializable(ForStringUUID::class) UUID,
    val level: @Serializable(ForResourceLocation::class) ResourceLocation,
) : ValueSnapshot(), ScriptSnapshot<NpcEntity>, NarrativeHostReferenceSnapshot {
    override val typeId: String = "NpcEntity"

    override suspend fun restore(context: ValueRestoreContext): NpcEntity {
        val server = (context as KatariRestoreContext).server
        KatariHostReferences.resolveEntity(server, uuid, NpcEntity::class.java)?.let { return it }

        val event =
            EntityLoadedEvent.await { it.entity.uuid == uuid && it.entity is NpcEntity }
        return event.entity as NpcEntity
    }

    override suspend fun restoreReference(context: ValueRestoreContext): Any = restore(context)

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
) : ValueSnapshot(), ScriptSnapshot<Entity>, NarrativeHostReferenceSnapshot {
    override val typeId: String = "Entity"

    override suspend fun restore(context: ValueRestoreContext): Entity {
        val server = (context as KatariRestoreContext).server
        KatariHostReferences.resolveEntity(server, uuid, Entity::class.java)?.let { return it }

        val event =
            EntityLoadedEvent.await { it.entity.uuid == uuid }
        return event.entity
    }

    override suspend fun restoreReference(context: ValueRestoreContext): Any = restore(context)

    companion object : ScriptSnapshotFactory<Entity, EntitySnapshot> {
        override fun capture(value: Entity): EntitySnapshot {
            return EntitySnapshot(value.uuid, value.level().dimension().location())
        }
    }
}

@Serializable
@SerialName("hollowengine:katari/living_entity")
@ScriptType("LivingEntity", Entity::class)
data class LivingEntitySnapshot(
    val uuid: @Serializable(ForStringUUID::class) UUID,
    val level: @Serializable(ForResourceLocation::class) ResourceLocation,
) : ValueSnapshot(), ScriptSnapshot<LivingEntity>, NarrativeHostReferenceSnapshot {
    override val typeId: String = "LivingEntity"

    override suspend fun restore(context: ValueRestoreContext): LivingEntity {
        val server = (context as KatariRestoreContext).server
        KatariHostReferences.resolveEntity(server, uuid, LivingEntity::class.java)?.let { return it }

        val event =
            EntityLoadedEvent.await { it.entity.uuid == uuid && it.entity is LivingEntity }
        return event.entity as LivingEntity
    }

    override suspend fun restoreReference(context: ValueRestoreContext): Any = restore(context)

    companion object : ScriptSnapshotFactory<LivingEntity, LivingEntitySnapshot> {
        override fun capture(value: LivingEntity): LivingEntitySnapshot {
            return LivingEntitySnapshot(value.uuid, value.level().dimension().location())
        }
    }
}

@Serializable
@SerialName("hollowengine:katari/player")
@ScriptType("Player", LivingEntity::class)
data class PlayerSnapshot(
    val uuid: @Serializable(ForStringUUID::class) UUID,
) : ValueSnapshot(), ScriptSnapshot<Player>, NarrativeHostReferenceSnapshot {
    override val typeId: String = "Player"

    override suspend fun restore(context: ValueRestoreContext): Player {
        val server = (context as KatariRestoreContext).server
        return KatariHostReferences.awaitPlayer(server, uuid)
    }

    override suspend fun restoreReference(context: ValueRestoreContext): Any = restore(context)

    companion object : ScriptSnapshotFactory<Player, PlayerSnapshot> {
        override fun capture(value: Player): PlayerSnapshot {
            return PlayerSnapshot(value.uuid)
        }
    }
}
