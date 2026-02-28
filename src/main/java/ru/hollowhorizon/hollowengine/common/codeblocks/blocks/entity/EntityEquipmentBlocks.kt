package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:entity/get_equipment")
class EntityGetEquipmentBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")

    var slotInt = 0

    @Transient
    override val expressionType: ExpressionType = typeOf<ItemStack>()

    override suspend fun execute(): ItemStack {
        val e = entity()
        return when (slotInt) {
            0 -> e.getItemInHand(InteractionHand.MAIN_HAND)
            1 -> e.getItemInHand(InteractionHand.OFF_HAND)
            2 -> e.getItemBySlot(EquipmentSlot.HEAD)
            3 -> e.getItemBySlot(EquipmentSlot.CHEST)
            4 -> e.getItemBySlot(EquipmentSlot.LEGS)
            5 -> e.getItemBySlot(EquipmentSlot.FEET)
            else -> ItemStack.EMPTY
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_equipment".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        // Keep simple selection as ComboBox is fine here
        ComboBox {
            modifier.width(FitContent).items(listOf(
                "hollowengine.gui.codeblocks.label.equipment_main_hand".lang,
                "hollowengine.gui.codeblocks.label.equipment_off_hand".lang,
                "hollowengine.gui.codeblocks.label.equipment_head".lang,
                "hollowengine.gui.codeblocks.label.equipment_chest".lang,
                "hollowengine.gui.codeblocks.label.equipment_legs".lang,
                "hollowengine.gui.codeblocks.label.equipment_feet".lang
            ))
                .font(font)
                .background(de.fabmax.kool.modules.ui2.RoundRectBackground(Color.BLACK.withAlpha(0.15f), Dimensions.PaddingSmall.scaled()))
                .zLayer(modifier.zLayer + 10)
                .margin(Dimensions.PaddingSmall.scaled()).padding(Dimensions.PaddingSmall.scaled())
                .alignY(AlignmentY.Center)
            modifier.selectedIndex(slotInt)
            modifier.onItemSelected { slotInt = it; notifyChanged() }
        }
    }
}

@Serializable
@SerialName("hollowengine:player/get_inventory_item")
class PlayerGetInventoryItemBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>("player")

    var slotInt = 0

    @Transient
    val popup = AutoPopup(true, true)

    @Transient
    override val expressionType: ExpressionType = typeOf<ItemStack>()

    override suspend fun execute(): ItemStack {
        val p = player()
        val idx = slotInt.coerceIn(0, 40)
        return p.inventory.getItem(idx)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_inventory".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)

        val current = runCatching { Minecraft.getInstance().player!!.inventory.getItem(slotInt.coerceIn(0, 40)) }.getOrDefault(ItemStack.EMPTY)
        Box(Dimensions.PaddingHuge.scaled() * 1.5f, Dimensions.PaddingHuge.scaled() * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .margin(horizontal = Dimensions.PaddingSmall.scaled())
                .border(de.fabmax.kool.modules.ui2.RoundRectBorder(Color.WHITE, Dimensions.PaddingSmall.scaled(), Dimensions.PaddingSmall.scaled()))

            Item(current) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(Dimensions.PaddingHuge.scaled() * size, Dimensions.PaddingHuge.scaled() * size)
                    .align(de.fabmax.kool.modules.ui2.AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.selectSlot { index, _ ->
                                slotInt = index
                                popup.hide()
                                notifyChanged()
                            }
                        }
                        popup.show(Vec2f(uiNode.rightPx + Dimensions.PaddingSmall.scaled().px, uiNode.topPx))
                    }
            }
        }
        popup()
    }
}
