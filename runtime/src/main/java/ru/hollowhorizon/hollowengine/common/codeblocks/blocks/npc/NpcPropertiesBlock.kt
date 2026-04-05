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
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity

@Serializable
@SerialName("hollowengine:npcs/get_health")
class NpcHealthBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()

    override suspend fun execute(): Any? {
        return npc().health
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.health".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
    }
}

@Serializable
@SerialName("hollowengine:npcs/get_max_health")
class NpcMaxHealthBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()

    override suspend fun execute(): Any? {
        return npc().maxHealth
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.max_health".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
    }
}

@Serializable
@SerialName("hollowengine:npcs/get_speed")
class NpcSpeedBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()

    override suspend fun execute(): Any? {
        return calculateSpeedViaDeltaMovement(npc())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.speed".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
    }
}

@Serializable
@SerialName("hollowengine:npcs/distance_to")
class NpcDistanceToBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val npc by input<NpcEntity>()
    val target by input<Any>()

    override suspend fun execute(): Any? {
        val npc = npc()
        val target = target()
        return when (target) {
            is LivingEntity -> npc.position().distanceTo(target.position())
            is Vec3 -> npc.position().distanceTo(target)
            else -> throw IllegalArgumentException("Target must be LivingEntity or Vec3")
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.distance_between".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("hollowengine.gui.codeblocks.label.entity_and".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
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
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val position by input<Vec3>()

    override suspend fun execute() {
        val npc = npc()
        val pos = position()
        npc.teleportTo(pos.x, pos.y, pos.z)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.npc_teleport".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("hollowengine.gui.codeblocks.label.to".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(position)
    }
}

@Serializable
@SerialName("hollowengine:npcs/set_target")
class NpcSetTargetBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val target by input<LivingEntity>()

    override suspend fun execute() {
        npc().target = target()
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(npc)
        Text("hollowengine.gui.codeblocks.label.npc_attacks".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(target)
    }
}

@Serializable
@SerialName("hollowengine:npcs/clear_target")
class NpcClearTargetBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()

    override suspend fun execute() {
        npc().target = null
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(npc)
        Text("hollowengine.gui.codeblocks.label.npc_stops_attacking".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}


@Serializable
@SerialName("hollowengine:npcs/set_name")
class NpcSetNameBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val name by input<String>()

    override suspend fun execute() {
        npc().name = name()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.npc_set_name".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("hollowengine.gui.codeblocks.label.to".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(name)
    }
}

@Serializable
@SerialName("hollowengine:npcs/heal")
class NpcHealBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val amount by input<Number>()

    override suspend fun execute() {
        npc().heal(amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(npc)
        Text("hollowengine.gui.codeblocks.block.heal".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:npcs/hurt")
class NpcHurtBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val amount by input<Number>()

    override suspend fun execute() {
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
    override val color: Color get() = CodeBlocksColors.NPCS

    val npc by input<NpcEntity>()
    val health by input<Number>()

    override suspend fun execute() {
        npc().health = health().toFloat()
    }

    override fun InputSlotScope.composeContent() {
        Text("Установить здоровье") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Text("равным") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(health)
    }
}