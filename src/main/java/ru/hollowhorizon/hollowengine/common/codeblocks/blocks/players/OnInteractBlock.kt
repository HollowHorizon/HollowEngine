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
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.EventDrivenStartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentScriptEvent
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.skipScriptEventExecution
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson

@Serializable
@SerialName("hollowengine:events/interact_block/entity")
class PlayerInteractWithEntity : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS

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
        Text("hollowengine.gui.codeblocks.label.player".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("hollowengine.gui.codeblocks.label.player_interacts_with".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(start = 5.dp).bold()
        }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:events/interact_block/block")
class PlayerInteractWithBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS

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
        Text("hollowengine.gui.codeblocks.label.player".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("hollowengine.gui.codeblocks.label.player_interacts_with_block".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = Dimensions.PaddingSmall.scaled()).bold()
        }
        Box(Dimensions.PaddingLarge.scaled(), Dimensions.PaddingLarge.scaled()) {
            modifier.alignY(AlignmentY.Center)
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

@Serializable
@SerialName("hollowengine:events/interact_block/item")
class PlayerInteractWithItem : StartBlock(), EventDrivenStartBlock<PlayerInteractEvent.ItemInteract> {
    override val color: Color get() = CodeBlocksColors.EVENTS

    val player by input<Player>("player")
    var item: @Serializable(ForItemStackJson::class) ItemStack = ItemStack.EMPTY

    @Transient
    val popup = AutoPopup(true, true)

    override suspend fun trigger() {
        currentScriptEvent<PlayerInteractEvent.ItemInteract>()?.let { event ->
            if (!matches(event)) skipScriptEventExecution()
            return
        }

        while (true) {
            val event = await<PlayerInteractEvent.ItemInteract>()
            if (matches(event)) {
                return
            }
        }
    }

    override val eventType: Class<PlayerInteractEvent.ItemInteract> get() = PlayerInteractEvent.ItemInteract::class.java

    override fun resolveScopeEntity(event: PlayerInteractEvent.ItemInteract) = event.player

    private suspend fun matches(event: PlayerInteractEvent.ItemInteract): Boolean {
        if (event.player != player()) return false
        //? if > 1.20.1 {
        /*return ItemStack.isSameItemSameComponents(event.itemStack, item)
        *///?} else {
        return ItemStack.isSameItemSameTags(event.itemStack, item)
        //?}
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("hollowengine.gui.codeblocks.label.player_uses".lang) {
            modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).margin(horizontal = Dimensions.PaddingSmall.scaled()).bold()
        }
        Box(Dimensions.PaddingLarge.scaled(), Dimensions.PaddingLarge.scaled()) {
            modifier.alignY(AlignmentY.Center)
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
