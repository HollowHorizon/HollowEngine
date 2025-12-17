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
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:entity/is_attackable")
class EntityIsAttackable : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isAttackable
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("можно атаковать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_swimming")
class EntityIsSwimming : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isSwimming
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("плавает") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_underwater")
class EntityIsUnderwater : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isUnderWater
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("под водой") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_alive")
class EntityIsAlive : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isAlive
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("жива") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_aggressive")
class EntityIsAggressive : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return if (entity is Mob) entity.isAggressive else false
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("в данный момент агрессивна") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_on_fire")
class EntityIsOnFire : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isOnFire
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("горит") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_in_lava")
class EntityIsInLava : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isInLava
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("в лаве") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_invisible")
class EntityIsInvisible : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isInvisible
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("невидима") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}


@Serializable
@SerialName("hollowengine:entity/is_invulnerable")
class EntityIsInvulnerable : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.isInvulnerable
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("неуязвима") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_standing")
class EntityIsStanding : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.STANDING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("стоит") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_sleeping")
class EntityIsSleeping : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.SLEEPING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("спит") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_crouching")
class EntityIsCrouching : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.CROUCHING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("крадётся") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/is_sitting")
class EntityIsSitting : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.SITTING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("сидит") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}



@Serializable
@SerialName("hollowengine:entity/is_running")
class EntityIsRunning : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Boolean>()

    override suspend fun BlockContext.execute(): Boolean {
        val entity = entity()
        return entity.hasPose(Pose.CROUCHING)
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("бежит") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
    }
}

@Serializable
@SerialName("hollowengine:entity/get_main_hand_item")
class EntityGetMainHandItem : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<ItemStack>()

    override suspend fun BlockContext.execute(): ItemStack {
        val entity = entity()
        return entity.getItemInHand(InteractionHand.MAIN_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("Предмет в главной руке") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_off_hand_item")
class EntityGetOffHandItem : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<ItemStack>()

    override suspend fun BlockContext.execute(): ItemStack {
        val entity = entity()
        return entity.getItemInHand(InteractionHand.OFF_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("Предмет в вспомогательной руке") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_look_angle")
class EntityGetLookAngle : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Vec3>()

    override suspend fun BlockContext.execute(): Vec3 {
        val entity = entity()
        return entity.lookAngle
    }

    override fun InputSlotScope.composeContent() {
        Text("Вектор взгляда сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_name")
class EntityGetName : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    override suspend fun BlockContext.execute(): String {
        val entity = entity()
        return entity.name.string
    }

    override fun InputSlotScope.composeContent() {
        Text("Имя сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/get_position")
class EntityGetPosition : ExpressionBlock() {
    val entity by input<LivingEntity>("entity")

    @Transient
    override val expressionType: ExpressionType = typeOf<Vec3>()

    override suspend fun BlockContext.execute(): Vec3 {
        val entity = entity()
        return entity.position()
    }

    override fun InputSlotScope.composeContent() {
        Text("Координаты сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/hurt")
class EntityHurtBlock : StatementBlock() {
    val entity by input<LivingEntity>()
    val amount by input<Number>()

    override suspend fun BlockContext.execute() {
        val npc = entity()
        npc.hurt(npc.damageSources().generic(), amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        InputSlot(entity)
        Text("получает урон") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:entity/remove")
class RemoveEntityBlock : StatementBlock() {
    val entity by input<LivingEntity>()

    override suspend fun BlockContext.execute() {
        entity().remove(Entity.RemovalReason.DISCARDED)
    }

    override fun InputSlotScope.composeContent() {
        Text("Удалить сущность") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/clear_fire")
class EntityClearFire : StatementBlock() {
    val entity by input<LivingEntity>()

    override suspend fun BlockContext.execute() {
        entity().clearFire()
    }

    override fun InputSlotScope.composeContent() {
        Text("Потушить сущность") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/push")
class PushEntityBlock : StatementBlock() {
    val entity by input<LivingEntity>()
    val position by input<Vec3>()

    override suspend fun BlockContext.execute() {
        val e = entity()
        val pos = position()
        e.push(pos.x, pos.y, pos.z)
    }

    override fun InputSlotScope.composeContent() {
        Text("Толкнуть") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("в направлении") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(position)
    }
}

@Serializable
@SerialName("hollowengine:entity/swing_main_hand")
class SwingMainHandBlock : StatementBlock() {
    val entity by input<LivingEntity>()

    override suspend fun BlockContext.execute() {
        val e = entity()
        e.swing(InteractionHand.MAIN_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("Махнуть главной рукой") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:entity/swing_off_hand")
class SwingOffHandBlock : StatementBlock() {
    val entity by input<LivingEntity>()

    override suspend fun BlockContext.execute() {
        val e = entity()
        e.swing(InteractionHand.OFF_HAND)
    }

    override fun InputSlotScope.composeContent() {
        Text("Махнуть вспомогательной рукой") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}
