package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:entity/is_attackable")
class EntityIsAttackable : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isAttackable
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_attackable".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_swimming")
class EntityIsSwimming : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isSwimming
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_swimming".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_underwater")
class EntityIsUnderwater : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isUnderWater
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_underwater".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_alive")
class EntityIsAlive : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isAlive
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_alive".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_aggressive")
class EntityIsAggressive : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return if (entity is Mob) entity.isAggressive else false
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_aggressive".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_on_fire")
class EntityIsOnFire : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isOnFire
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_on_fire".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_in_lava")
class EntityIsInLava : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isInLava
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_in_lava".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_invisible")
class EntityIsInvisible : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isInvisible
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_invisible".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}


@Serializable
@SerialName("hollowengine:entity/is_invulnerable")
class EntityIsInvulnerable : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.isInvulnerable
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_invulnerable".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_standing")
class EntityIsStanding : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.STANDING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_standing".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_sleeping")
class EntityIsSleeping : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.SLEEPING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_sleeping".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_crouching")
class EntityIsCrouching : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.CROUCHING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.block.is_crouching".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_sitting")
class EntityIsSitting : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.SITTING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.is_sitting".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}



@Serializable
@SerialName("hollowengine:entity/is_running")
class EntityIsRunning : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.CROUCHING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_is_running".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/get_main_hand_item")
class EntityGetMainHandItem : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<ItemStack>()

    override suspend fun execute(): ItemStack {
        val entity = entity()
        return entity.getItemInHand(InteractionHand.MAIN_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.get_main_hand".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_off_hand_item")
class EntityGetOffHandItem : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<ItemStack>()

    override suspend fun execute(): ItemStack {
        val entity = entity()
        return entity.getItemInHand(InteractionHand.OFF_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.get_off_hand".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_look_angle")
class EntityGetLookAngle : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Vec3>()

    override suspend fun execute(): Vec3 {
        val entity = entity()
        return entity.lookAngle
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.get_look_angle".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_name")
class EntityGetName : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    override suspend fun execute(): String {
        val entity = entity()
        return entity.name.string
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.get_name".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_position")
class EntityGetPosition : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Vec3>()

    override suspend fun execute(): Vec3 {
        val entity = entity()
        return entity.position()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.get_position".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/hurt")
class EntityHurtBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()
    val amount by input<Number>()

    override suspend fun execute() {
        val npc = entity()
        npc.hurt(npc.damageSources().generic(), amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_hurt".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:entity/remove")
class RemoveEntityBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()

    override suspend fun execute() {
        entity().remove(Entity.RemovalReason.DISCARDED)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_remove".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/clear_fire")
class EntityClearFire : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()

    override suspend fun execute() {
        entity().clearFire()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_clear_fire".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/push")
class PushEntityBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()
    val position by input<Vec3>()

    override suspend fun execute() {
        val e = entity()
        val pos = position()
        e.push(pos.x, pos.y, pos.z)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_push".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(position)
    }
}

@Serializable
@SerialName("hollowengine:entity/swing_main_hand")
class SwingMainHandBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()

    override suspend fun execute() {
        val e = entity()
        e.swing(InteractionHand.MAIN_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_swing_main".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/swing_off_hand")
class SwingOffHandBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>()

    override suspend fun execute() {
        val e = entity()
        e.swing(InteractionHand.OFF_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_swing_off".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}
