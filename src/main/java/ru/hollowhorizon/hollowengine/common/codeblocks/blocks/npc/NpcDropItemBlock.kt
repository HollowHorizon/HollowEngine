package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.dropItem
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson

@Serializable
@SerialName("hollowengine:npcs/drop_item")
class NpcDropItemBlock : StatementBlock() {
    val npc by input<NpcEntity>()
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun execute() {
        npc().dropItem(item)
    }

    override fun InputSlotScope.composeContent() {
        Text("НИП") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(npc)
        Box(Grow.Std) { }
        Text("Бросает:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap).bold() }
        Box(sizes.largeGap * 1.5f, sizes.largeGap * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(sizes.largeGap * size, sizes.largeGap * size)
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .onClick {
                        popup.popupContent = {
                            InventoryPicker.select {
                                item = it
                                popup.hide()
                            }
                        }

                        popup.show(Vec2f(uiNode.rightPx + sizes.smallGap.px, uiNode.topPx))
                    }
            }
        }
        popup()
    }
}