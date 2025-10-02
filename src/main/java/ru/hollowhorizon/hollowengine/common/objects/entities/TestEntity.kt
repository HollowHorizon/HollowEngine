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

package ru.hollowhorizon.hollowengine.common.objects.entities

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
//? if >= 1.21 {
/*import net.minecraft.world.level.pathfinder.PathType as BlockPathTypes
*///?} else {
import net.minecraft.world.level.pathfinder.BlockPathTypes
//?}
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.utils.open

class TestEntity(type: EntityType<TestEntity>, world: Level) : PathfinderMob(type, world) {

    init {

        setPathfindingMalus(BlockPathTypes.WATER, -1.0f)
        this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.NETHERITE_HOE))
        this.setItemInHand(InteractionHand.OFF_HAND, ItemStack(Items.TNT))

        this.setItemSlot(EquipmentSlot.FEET, ItemStack(Items.IRON_BOOTS))
        this.setItemSlot(EquipmentSlot.LEGS, ItemStack(Items.IRON_LEGGINGS))
        this.setItemSlot(EquipmentSlot.CHEST, ItemStack(Items.IRON_CHESTPLATE))
        this.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.IRON_HELMET))
    }

    override fun registerGoals() {
        super.registerGoals()
        //this.goalSelector.addGoal(0, RandomLookAroundGoal(this))
        //this.goalSelector.addGoal(1, RandomStrollGoal(this, 1.0, 10))
    }

    override fun tick() {
        super.tick()

        if (!level().isClientSide) {
            server?.playerList?.players?.firstOrNull()?.let {
                lookControl.setLookAt(it)
            }
        }
    }

    override fun interactAt(player: Player, vec: Vec3, hand: InteractionHand): InteractionResult {
        if (level().isClientSide) {
            object : Screen(Component.empty()) {
                override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
                }
            }.open()
        }
        return super.interactAt(player, vec, hand)
    }
}