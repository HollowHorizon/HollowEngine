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
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.prefabs.EntityEditorScreen
import ru.hollowhorizon.hollowengine.client.utils.open
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.geary.sync.setSyncing
import ru.hollowhorizon.hollowengine.common.objects.items.CreativeTab
import ru.hollowhorizon.hollowengine.common.registry.ModItems
import ru.hollowhorizon.hollowengine.common.registry.ModTabs
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.player.send


class NpcTool : Item(Properties().stacksTo(1)), CreativeTab {

    override fun use(pLevel: Level, player: Player, pUsedHand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (pLevel.isClientSide && pUsedHand == InteractionHand.MAIN_HAND && player.isShiftKeyDown) {
            EntityEditorScreen(player).open()
            return InteractionResultHolder.success(player.getItemInHand(pUsedHand))
        }
        if (!pLevel.isClientSide && pUsedHand == InteractionHand.MAIN_HAND) {
            val start: Vec3 = player.eyePosition
            val addition: Vec3 = player.lookAngle.multiply(Vec3(25.0, 25.0, 25.0))
            val result = ProjectileUtil.getEntityHitResult(
                player.level(), player,
                start, start.add(addition),
                player.boundingBox.expandTowards(addition).inflate(1000000.0)
            ) { true }

            if (result is EntityHitResult) return super.use(pLevel, player, pUsedHand)

            val pos = player.pick(25.0, 0f, false).location

            val npc = NpcEntity(pLevel)
            npc.setPos(pos)
            pLevel.addFreshEntity(npc)
            npc.entity.setSyncing(Model())
        }

        return super.use(pLevel, player, pUsedHand)
    }

    override fun tab() = ModTabs.HOLLOW_ENGINE
}

@SubscribeEvent
fun PlayerInteractEvent.EntityInteract.onInteract() {
    if (hand == InteractionHand.MAIN_HAND && player.mainHandItem.item == ModItems.NPC_TOOL && player.level().isClientSide) {
        if (player.hasPermissions(2)) {
            EntityEditorScreen(target).open()
        } else {
            player.send("You don't have permission to use this item.")
        }
        isCanceled = true
    }
}