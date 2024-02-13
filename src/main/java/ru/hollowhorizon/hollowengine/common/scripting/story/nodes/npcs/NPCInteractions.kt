package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util.TeleportContainer

infix fun NPCProperty.useBlock(target: () -> Vec3) {
    this moveTo target
    this lookAt target
    next {
        val entity = this@useBlock()
        val pos = target()
        val hit = entity.level.clip(
            ClipContext(
                pos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity
            )
        )
        entity.swing(InteractionHand.MAIN_HAND)
        val state = entity.level.getBlockState(hit.blockPos)
        state.use(entity.level, entity.fakePlayer, InteractionHand.MAIN_HAND, hit)
    }
}

infix fun NPCProperty.destroyBlock(target: () -> Vec3) {
    this moveTo target
    this lookAt target
    next {
        val entity = this@destroyBlock()
        val manager = entity.fakePlayer.gameMode

        manager.destroyBlock(BlockPos(target()))
        entity.swing(InteractionHand.MAIN_HAND)
    }
}

infix fun NPCProperty.dropItem(stack: () -> ItemStack) = next {
    val entity = this@dropItem()
    val p = entity.position()
    val entityStack = ItemEntity(entity.level, p.x, p.y + entity.eyeHeight, p.z, stack())
    entityStack.setDefaultPickUpDelay()
    val f8 = Mth.sin(entity.xRot * Mth.PI / 180f)
    val f3 = Mth.sin(entity.yHeadRot * Mth.PI / 180f)
    val f4 = Mth.cos(entity.yHeadRot * Mth.PI / 180f)
    entityStack.setDeltaMovement(
        -f3 * 0.3, -f8 * 0.3 + 0.1, f4 * 0.3
    )
    entity.level.addFreshEntity(entityStack)
}

infix fun NPCProperty.requestItems(block: GiveItemList.() -> Unit) {
    builder.apply {
        +NpcItemListNode(block, this@requestItems)
    }
}

fun NPCProperty.waitInteract() {
    builder.apply {
        +NpcInteractNode(this@waitInteract)
    }
}

infix fun NPCProperty.tpTo(target: TeleportContainer.() -> Unit) = next {
    val tp = TeleportContainer().apply(target)
    val teleport = tp.pos
    this@tpTo.invoke().teleportTo(teleport.x, teleport.y, teleport.z)
}