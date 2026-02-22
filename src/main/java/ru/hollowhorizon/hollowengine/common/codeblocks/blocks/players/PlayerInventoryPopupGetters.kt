package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson

@Serializable
@SerialName("hollowengine:types/selected_item_popup")
class PlayerSelectedItemPopupBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack(Items.STICK)

    @Transient
    val popup = AutoPopup(true, true)

    @Transient
    override val expressionType: ExpressionType = typeOf<ItemStack>()

    override suspend fun execute(): Any? {
        return item.copy()
    }

    override fun InputSlotScope.composeContent() {
        Box(Dimensions.PaddingHuge.scaled() * 1.5f, Dimensions.PaddingHuge.scaled() * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .margin(horizontal = Dimensions.PaddingSmall.scaled())
                .border(RoundRectBorder(Color.WHITE, Dimensions.PaddingSmall.scaled(), Dimensions.PaddingSmall.scaled()))

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(Dimensions.PaddingHuge.scaled() * size, Dimensions.PaddingHuge.scaled() * size)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.select {
                                item = it
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

@Serializable
@SerialName("hollowengine:types/selected_block_popup")
class PlayerSelectedBlockPopupBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ITEMS

    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack(Items.DIRT)

    @Transient
    val popup = AutoPopup(true, true)

    @Transient
    override val expressionType: ExpressionType = typeOf<BlockState>()

    override suspend fun execute(): Any? {
        val stack = item
        if (stack.isEmpty) return null
        val block = Block.byItem(stack.item) ?: return null
        return block.defaultBlockState()
    }

    override fun InputSlotScope.composeContent() {
        Box(Dimensions.PaddingHuge.scaled() * 1.5f, Dimensions.PaddingHuge.scaled() * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .margin(horizontal = Dimensions.PaddingSmall.scaled())
                .border(RoundRectBorder(Color.WHITE, Dimensions.PaddingSmall.scaled(), Dimensions.PaddingSmall.scaled()))

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(Dimensions.PaddingHuge.scaled() * size, Dimensions.PaddingHuge.scaled() * size)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.select {
                                item = it
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
