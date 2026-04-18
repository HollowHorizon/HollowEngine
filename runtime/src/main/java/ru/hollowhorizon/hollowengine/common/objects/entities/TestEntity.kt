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
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.utils.open
import net.minecraft.world.level.pathfinder.PathType as BlockPathTypes

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