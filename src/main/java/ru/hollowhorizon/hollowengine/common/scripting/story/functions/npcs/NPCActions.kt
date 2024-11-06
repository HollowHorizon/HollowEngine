package ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.TagParser
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.scripting.Ignore
import ru.hollowhorizon.hollowengine.scripting.Suspendable

@Suspendable
fun NPCEntity.move(entity: Entity, dist: Double = 1.5, speed: Double = 1.0) {
    while (distanceTo(entity) > dist) {
        navigation.moveTo(navigation.createPath(entity.x, entity.y, entity.z, 0), speed)
    }
    navigation.stop()
}

@Suspendable
infix fun NPCEntity.move(mob: Entity): Unit = move(entity = mob)

@Suspendable
fun NPCEntity.move(pos: Vec3, dist: Double = 1.5, speed: Double = 1.0) {
    while (distanceToSqr(pos) > dist * dist || !navigation.isDone) {
        navigation.moveTo(navigation.createPath(pos.x, pos.y, pos.z, 0), speed)
    }

    navigation.stop()
}

@Suspendable
infix fun NPCEntity.move(position: Vec3): Unit = move(pos = position)

@Suspendable
infix fun NPCEntity.look(position: Vec3) {
    var ticks = 30
    while (ticks > 0) {
        lookControl.setLookAt(position)
        ticks--
    }
}

@Suspendable
infix fun NPCEntity.look(entity: Entity) {
    var ticks = 30
    while (ticks > 0) {
        lookControl.setLookAt(entity)
        ticks--
    }
}

@Suspendable
infix fun NPCEntity.useBlock(pos: Vec3) {
    move(pos)
    look(pos)
    @Ignore
    val hit = level().clip(ClipContext(pos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this))
    swing(InteractionHand.MAIN_HAND)
    @Ignore val state = level().getBlockState(hit.blockPos)
    state.use(level(), fakePlayer, InteractionHand.MAIN_HAND, hit)
}

@Suspendable
infix fun NPCEntity.destroyBlock(pos: Vec3) {
    move(pos)
    look(pos)
    @Ignore val manager = fakePlayer.gameMode

    manager.destroyBlock(BlockPos(pos.x.toInt(), pos.y.toInt(), pos.z.toInt()))
    swing(InteractionHand.MAIN_HAND)
}

fun NPCEntity.dropItem(item: ItemStack) {
    val p = position()
    val entityStack = ItemEntity(level(), p.x, p.y + eyeHeight, p.z, item)
    entityStack.setDefaultPickUpDelay()
    val f8 = Mth.sin(xRot * Mth.PI / 180f)
    val f3 = Mth.sin(yHeadRot * Mth.PI / 180f)
    val f4 = Mth.cos(yHeadRot * Mth.PI / 180f)
    entityStack.setDeltaMovement(-f3 * 0.3, -f8 * 0.3 + 0.1, f4 * 0.3)
    level().addFreshEntity(entityStack)
}

@Suspendable
fun wait(time: Int) {
    var ticks = time
    while (ticks > 0) {
        ticks-- // Циклы выполняются по-тиково, не чаще 1 итерации в тик
    }
}

fun item(item: String, count: Int = 1, nbt: String) = item(item, count, TagParser.parseTag(nbt))

fun item(item: String, count: Int = 1, nbt: CompoundTag? = null) = ItemStack(
    BuiltInRegistries.ITEM.get(item.rl),
    count
).apply {
    nbt?.let {
        tag = it
        this.item.verifyTagAfterLoad(tag!!)
    }

    if (this.item.canBeDepleted()) {
        this.damageValue = this.damageValue
    }
}

val Number.sec get() = (this.toFloat() * 20).toInt()

infix fun NPCEntity.say(text: String) {
    server?.playerList?.players?.forEach {
        it.sendSystemMessage("[$name] $text".literal)
    }
}

