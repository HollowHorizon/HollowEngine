package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import dev.ftb.mods.ftbteams.FTBTeamsAPI
import dev.ftb.mods.ftbteams.data.Team
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.Node
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.next

class NpcLookToBlockNode(npcConsumer: NPCProperty, var pos: () -> Vec3, var speed: Vec2 = Vec2(10f, 30f)) : Node() {
    val npc by lazy { npcConsumer() }
    private var ticks = 30

    override fun tick(): Boolean {
        val look = npc.lookControl

        val newPos = pos()

        look.setLookAt(newPos.x, newPos.y, newPos.z, speed.x, speed.y)

        return ticks-- > 0
    }

    override fun serializeNBT() = CompoundTag().apply {
        val newPos = pos()
        putDouble("pos_x", newPos.x)
        putDouble("pos_y", newPos.y)
        putDouble("pos_z", newPos.z)
        putFloat("speed_x", speed.x)
        putFloat("speed_y", speed.y)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        pos = { Vec3(nbt.getDouble("pos_x"), nbt.getDouble("pos_y"), nbt.getDouble("pos_z")) }
        speed = Vec2(nbt.getFloat("speed_x"), nbt.getFloat("speed_y"))
    }
}

class NpcLookToEntityNode(npcConsumer: NPCProperty, var target: () -> Entity?, var speed: Vec2 = Vec2(10f, 30f)) : Node() {
    val npc by lazy { npcConsumer() }
    private var ticks = 30

    override fun tick(): Boolean {
        val look = npc.lookControl

        look.setLookAt(target() ?: return true, speed.x, speed.y)

        return ticks-- > 0
    }

    override fun serializeNBT() = CompoundTag()

    override fun deserializeNBT(nbt: CompoundTag) {
    }
}

class NpcLookToTeamNode(npcConsumer: NPCProperty, var target: () -> Team?, var speed: Vec2 = Vec2(10f, 30f)) : Node() {
    val npc by lazy { npcConsumer() }
    private var ticks = 30

    override fun tick(): Boolean {
        val look = npc.lookControl

        val team = target()?.onlineMembers?.minByOrNull { it.distanceToSqr(npc) } ?: return true

        look.setLookAt(team, speed.x, speed.y)

        return ticks-- > 0
    }

    override fun serializeNBT() = CompoundTag().apply {
        val team = target() ?: return@apply

        putUUID("team", team.id)
    }

    override fun deserializeNBT(nbt: CompoundTag) {
        val team = FTBTeamsAPI.getManager().getTeamByID(nbt.getUUID("team"))
        if (team == null) HollowCore.LOGGER.warn("Team ${nbt.getUUID("team")} not found!")
        target = { team }
    }
}

@Suppress("UNCHECKED_CAST")
inline infix fun <reified T> NPCProperty.lookAt(target: NpcTarget<T>) {
    builder.apply {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> +NpcLookToBlockNode(this@lookAt, target as NpcTarget<Vec3>)
            Entity::class.java.isAssignableFrom(type) -> +NpcLookToEntityNode(this@lookAt, target as NpcTarget<Entity>)
            Team::class.java.isAssignableFrom(type) -> +NpcLookToTeamNode(this@lookAt, target as NpcTarget<Team>)
            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline infix fun <reified T> NPCProperty.lookAlwaysAt(target: NpcTarget<T>) {
    builder.apply {
        val type = T::class.java
        when {
            Vec3::class.java.isAssignableFrom(type) -> next { this@lookAlwaysAt().npcTarget.lookingPos = target() as Vec3 }
            Entity::class.java.isAssignableFrom(type) -> next { this@lookAlwaysAt().npcTarget.lookingEntity = target() as Entity }
            Team::class.java.isAssignableFrom(type) -> next { this@lookAlwaysAt().npcTarget.lookingTeam = target() as Team }
            else -> throw IllegalArgumentException("Can't move to ${type.name} target!")
        }
    }
}

fun NPCProperty.stopLookAlways() = next {
    this@stopLookAlways().npcTarget.lookingPos = null
    this@stopLookAlways().npcTarget.lookingEntity = null
    this@stopLookAlways().npcTarget.lookingTeam = null
}

fun NPCProperty.lookAtEntityType(entity: () -> String) {
    val entityType = ForgeRegistries.ENTITY_TYPES.getValue(entity().rl)!!

    lookAt {
        val npc = this()
        val level = npc.level

        level.getEntitiesOfClass(LivingEntity::class.java, AABB.ofSize(npc.position(), 25.0, 25.0, 25.0)) {
            it.type == entityType
        }.minByOrNull { it.distanceTo(npc) } ?: npc
    }
}