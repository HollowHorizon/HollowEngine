package ru.hollowhorizon.hollowengine.client.kool.addons

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.KoolManager.MONOCRAFT

object InventoryPicker {
    context(scope: UiScope)
    fun select(onChoice: (ItemStack) -> Unit) = with(scope) {
        modifier.background(RoundRectBackground(IdeTheme.colors.background, sizes.smallGap))
        modifier.border(RoundRectBorder(IdeTheme.colors.primary, sizes.smallGap, sizes.borderWidth))
            .padding(sizes.smallGap)

        val player = Minecraft.getInstance().player ?: return@with
        Column {
            (0..<3).forEach { i ->
                Row {
                    player.inventory.items.subList(9 + i * 9, 18 + i * 9).forEach { item ->
                        slot(item) {
                            modifier.onClick {
                                onChoice(item)
                            }
                        }
                    }
                }
            }
            divider(Color.WHITE)
            Row {
                player.inventory.items.subList(0, 9).forEach { item ->
                    slot(item) {
                        modifier.onClick {
                            onChoice(item)
                        }
                    }
                }
            }
        }
    }

    private fun UiScope.slot(item: ItemStack, body: UiScope.() -> Unit) {
        Box(sizes.largeGap * 1.5f, sizes.largeGap * 1.5f) {
            modifier.alignY(AlignmentY.Center)
                .border(RoundRectBorder(Color.WHITE, sizes.smallGap, sizes.borderWidth))
                .margin(horizontal = sizes.smallGap, vertical = sizes.smallGap * 0.5f)

            Item(item) {
                val isHovered by modifier.hoverable()
                val size by animateFloatAsState(if (isHovered) 1.5f else 1.2f)

                modifier.size(sizes.largeGap * size, sizes.largeGap * size)
                    .align(AlignmentX.Center, AlignmentY.Center)

                body()
            }

            if(item.count > 1) Text(item.count.toString()) {
                modifier.font(MsdfFont(MONOCRAFT, 10f))
                    .align(AlignmentX.End, AlignmentY.Bottom)
                    .margin(sizes.smallGap * 0.5f)
                    .zLayer(2000)
            }
        }
    }
}