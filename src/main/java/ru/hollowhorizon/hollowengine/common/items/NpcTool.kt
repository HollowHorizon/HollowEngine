/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.items

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
import ru.hollowhorizon.hc.client.models.gltf.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.client.gui.NPCCreatorGui
import ru.hollowhorizon.hollowengine.client.gui.NPCToolGui
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.tabs.HOLLOWENGINE_TAB


class NpcTool : Item(Properties().tab(HOLLOWENGINE_TAB).stacksTo(1)) {
    override fun interactLivingEntity(
        pStack: ItemStack,
        pPlayer: Player,
        pInteractionTarget: LivingEntity,
        pUsedHand: InteractionHand,
    ): InteractionResult {
        if (pUsedHand == InteractionHand.MAIN_HAND && pPlayer.level.isClientSide &&
            pInteractionTarget is NPCEntity && pPlayer.hasPermissions(2)
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
                pPlayer.level, pPlayer,
                start, start.add(addition),
                pPlayer.boundingBox.expandTowards(addition).inflate(1000000.0)
            ) { true }

            if (result is EntityHitResult) return super.use(pLevel, pPlayer, pUsedHand)

            val pos = pPlayer.pick(25.0, 0f, false).location

            val npc = NPCEntity(pLevel)
            npc[AnimatedEntityCapability::class].model = "hollowengine:models/entity/player_model.gltf"
            npc.setPos(pos)
            pLevel.addFreshEntity(npc)

            OpenEditorScreen(npc.id).send(pPlayer as ServerPlayer)
        }

        return super.use(pLevel, pPlayer, pUsedHand)
    }
}

@HollowPacketV2
@Serializable
class OpenEditorScreen(private val npcId: Int) : HollowPacketV3<OpenEditorScreen> {
    override fun handle(player: Player, data: OpenEditorScreen) {
        val level = Minecraft.getInstance().level ?: return
        val npc = level.getEntity(npcId) as? NPCEntity ?: NPCEntity(level)
        Minecraft.getInstance().setScreen(NPCCreatorGui(npc, npcId))
    }

}