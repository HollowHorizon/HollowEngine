package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson

@Serializable
@SerialName("hollowengine:player/give_item")
class PlayerGiveItemBlock : StatementBlock() {
    val player by input<Player>()
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun execute() {
        player().inventory.add(item.copy())
    }

    override fun InputSlotScope.composeContent() {
        Text("Выдать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)

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
                            }
                        }
                        popup.show(Vec2f(uiNode.rightPx + Dimensions.PaddingSmall.scaled().px, uiNode.topPx))
                    }
            }
        }
        popup()
    }
}