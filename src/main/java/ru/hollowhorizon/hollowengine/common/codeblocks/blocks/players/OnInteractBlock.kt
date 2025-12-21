package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson

@Serializable
@SerialName("hollowengine:events/interact_block/entity")
class PlayerInteractWithEntity : StatementBlock() {
    val player by input<Player>("player")
    val entity by input<LivingEntity>("entity")

    override suspend fun execute() {
        while (true) {
            val event = await<PlayerInteractEvent.EntityInteract>()
            if (event.player != player()) continue
            if (event.target != entity()) continue
            return
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Игрок") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("взаимодействует с") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = 5.dp).bold()
        }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:events/interact_block/block")
class PlayerInteractWithBlock : StatementBlock() {
    val player by input<Player>("player")
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun execute() {
        while (true) {
            val event = await<PlayerInteractEvent.BlockInteract>()
            if (event.player != player()) continue
            val state = event.player.level().getBlockState(event.state.blockPos)
            // TODO: better block comparison
            return
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Игрок") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("взаимодействует с блоком") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap).bold()
        }
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

@Serializable
@SerialName("hollowengine:events/interact_block/item")
class PlayerInteractWithItem : StatementBlock(), StartBlock {
    val player by input<Player>("player")
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun execute() {
        while (true) {
            val event = await<PlayerInteractEvent.ItemInteract>()
            if (event.player != player()) continue
            if (ItemStack.isSameItemSameTags(event.itemStack, item)) {
                return
            }
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Игрок") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("использует") {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = sizes.smallGap).bold()
        }
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