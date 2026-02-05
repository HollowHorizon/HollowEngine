package ru.hollowhorizon.hollowengine.common.items

//? if <=1.19.2
/*import ru.hollowhorizon.hollowengine.client.utils.math.level*/
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ProjectileUtil
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.utils.open
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.objects.items.CreativeTab
import ru.hollowhorizon.hollowengine.common.registry.ModTabs


class NpcTool : Item(Properties().stacksTo(1)), CreativeTab {

    override fun use(pLevel: Level, pPlayer: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (pLevel.isClientSide && pUsedHand == InteractionHand.MAIN_HAND && pPlayer.isShiftKeyDown) {
            ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.EntityEditorScreen(pPlayer).open()
            return InteractionResultHolder.success(pPlayer.getItemInHand(pUsedHand))
        }
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

@SubscribeEvent
fun PlayerInteractEvent.EntityInteract.interactHandler() {
    if (hand == InteractionHand.MAIN_HAND && player.level().isClientSide && player.hasPermissions(2)) {
        ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.EntityEditorScreen(target).open()
        isCanceled = true
    }
}