package ru.hollowhorizon.hollowengine.common.items

//? if <=1.19.2
/*import ru.hollowhorizon.hc.client.utils.math.level*/
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.literal
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.objects.items.CreativeTab
import ru.hollowhorizon.hollowengine.client.gui.NPCToolGui
import ru.hollowhorizon.hollowengine.client.gui.scripting.sendToast
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.registry.ModTabs


class NpcTool : Item(Properties().stacksTo(1)), CreativeTab {
    override fun interactLivingEntity(
        pStack: ItemStack,
        pPlayer: Player,
        pInteractionTarget: LivingEntity,
        pUsedHand: InteractionHand,
    ): InteractionResult {
        if (pUsedHand == InteractionHand.MAIN_HAND && pPlayer.level().isClientSide &&
            pInteractionTarget is NpcEntity && pPlayer.hasPermissions(2)
        ) {
            NPCToolGui(pInteractionTarget).open()
            return InteractionResult.SUCCESS
        }

        return super.interactLivingEntity(pStack, pPlayer, pInteractionTarget, pUsedHand)
    }

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (!pLevel.isClientSide && pUsedHand == InteractionHand.MAIN_HAND) {
            val start: Vec3 = pPlayer.eyePosition
            val addition: Vec3 = pPlayer.lookAngle.multiply(Vec3(25.0, 25.0, 25.0))
            val result = ProjectileUtil.getEntityHitResult(
                pPlayer.level(), pPlayer,
                start, start.add(addition),
                pPlayer.boundingBox.expandTowards(addition).inflate(1000000.0)
            ) { true }

            if (result is EntityHitResult) return super.use(pLevel, pPlayer, pUsedHand)

            val pos = pPlayer.pick(25.0, 0f, false).location

            val npc = NpcEntity(pLevel)
            npc.setPos(pos)
            pLevel.addFreshEntity(npc)
        }

        return super.use(pLevel, pPlayer, pUsedHand)
    }

    override fun tab() = ModTabs.HOLLOW_ENGINE
}