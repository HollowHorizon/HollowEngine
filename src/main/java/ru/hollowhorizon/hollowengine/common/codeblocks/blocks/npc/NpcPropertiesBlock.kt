package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.models.internal.controller.calculateSpeedViaDeltaMovement
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

@Serializable
@SerialName("hollowengine:npcs/get_health")
class NpcHealthBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()

    override suspend fun BlockContext.execute(): Any? {
        return npc().health
    }

    override fun InputSlotScope.composeContent() {
        Text("Здоровье НИПа") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
    }
}

@Serializable
@SerialName("hollowengine:npcs/get_max_health")
class NpcMaxHealthBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()

    override suspend fun BlockContext.execute(): Any? {
        return npc().maxHealth
    }

    override fun InputSlotScope.composeContent() {
        Text("Макс. Здоровье НИПа") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
    }
}

@Serializable
@SerialName("hollowengine:npcs/get_speed")
class NpcSpeedBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()

    override suspend fun BlockContext.execute(): Any? {
        return calculateSpeedViaDeltaMovement(npc())
    }

    override fun InputSlotScope.composeContent() {
        Text("Скорость НИПа") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
    }
}

@Serializable
@SerialName("hollowengine:npcs/distance_to")
class NpcDistanceToBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()
    val target by input<Any>()

    override suspend fun BlockContext.execute(): Any? {
        val npc = npc()
        val target = target()
        return when (target) {
            is LivingEntity -> npc.position().distanceTo(target.position())
            is Vec3 -> npc.position().distanceTo(target)
            else -> throw IllegalArgumentException("Target must be LivingEntity or Vec3")
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Расстояние от") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("до") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(
            "target", ExpressionType.anyOf(
                typeOf<LivingEntity>(),
                typeOf<Vec3>()
            )
        )
    }
}

@Serializable
@SerialName("hollowengine:npcs/teleport")
class NpcTeleportBlock : StatementBlock() {
    val npc by input<NpcEntity>()
    val position by input<Vec3>()

    override suspend fun BlockContext.execute() {
        val npc = npc()
        val pos = position()
        npc.teleportTo(pos.x, pos.y, pos.z)
    }

    override fun InputSlotScope.composeContent() {
        Text("Телепортировать НИПа") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(position)
    }
}

@Serializable
@SerialName("hollowengine:npcs/set_target")
class NpcSetTargetBlock : StatementBlock() {
    val npc by input<NpcEntity>()
    val target by input<LivingEntity>()

    override suspend fun BlockContext.execute() {
        npc().target = target()
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(npc)
        Text("атакует") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(target)
    }
}

@Serializable
@SerialName("hollowengine:npcs/clear_target")
class NpcClearTargetBlock : StatementBlock() {
    val npc by input<NpcEntity>()

    override suspend fun BlockContext.execute() {
        npc().target = null
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(npc)
        Text("перестаёт атаковать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}


@Serializable
@SerialName("hollowengine:npcs/set_name")
class NpcSetNameBlock : StatementBlock() {
    val npc by input<NpcEntity>()
    val name by input<String>()

    override suspend fun BlockContext.execute() {
        npc().name = name()
    }

    override fun InputSlotScope.composeContent() {
        Text("Установить имя") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(name)
    }
}

@Serializable
@SerialName("hollowengine:npcs/heal")
class NpcHealBlock : StatementBlock() {
    val npc by input<NpcEntity>()
    val amount by input<Number>()

    override suspend fun BlockContext.execute() {
        npc().heal(amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(npc)
        Text("восстанавливает здоровье на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:npcs/hurt")
class NpcHurtBlock : StatementBlock() {
    val npc by input<NpcEntity>()
    val amount by input<Number>()

    override suspend fun BlockContext.execute() {
        val npc = npc()
        npc.hurt(npc.damageSources().generic(), amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(npc)
        Text("получает урон") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:npcs/set_health")
class NpcSetHealthBlock : StatementBlock() {
    val npc by input<NpcEntity>()
    val health by input<Number>()

    override suspend fun BlockContext.execute() {
        npc().health = health().toFloat()
    }

    override fun InputSlotScope.composeContent() {
        Text("Установить здоровье") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("равным") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(health)
    }
}