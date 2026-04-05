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
            for (i in 0..<3) {
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

    context(scope: UiScope)
    fun selectSlot(onChoice: (index: Int, stack: ItemStack) -> Unit) = with(scope) {
        modifier.background(RoundRectBackground(IdeTheme.colors.background, sizes.smallGap))
        modifier.border(RoundRectBorder(IdeTheme.colors.primary, sizes.smallGap, sizes.borderWidth))
            .padding(sizes.smallGap)

        val player = Minecraft.getInstance().player ?: return@with
        Column {
            Text("Инвентарь") {
                modifier.font(MsdfFont(MONOCRAFT, 12f))
                    .margin(bottom = sizes.smallGap)
                    .textColor(Color.WHITE)
            }

            // Main inventory (3 rows)
            for (row in 0..<3) {
                Row {
                    for (col in 0..<9) {
                        val idx = 9 + row * 9 + col
                        val stack = player.inventory.items[idx]
                        slot(stack) {
                            modifier.onClick { onChoice(idx, stack) }
                        }
                    }
                }
            }

            divider(Color.WHITE)

            // Hotbar
            Row {
                for (i in 0..<9) {
                    val idx = i
                    val stack = player.inventory.items[idx]
                    slot(stack) {
                        modifier.onClick { onChoice(idx, stack) }
                    }
                }
            }

            divider(Color.WHITE)

            // Armor + Offhand (best-effort indices for PlayerInventory)
            Row {
                val labels = listOf("Ботинки", "Поножи", "Нагрудник", "Шлем", "Левая рука")
                val indices = listOf(36, 37, 38, 39, 40)
                for (i in indices.indices) {
                    val idx = indices[i]
                    val stack = player.inventory.getItem(idx)
                    Box {
                        slot(stack) {
                            modifier.onClick { onChoice(idx, stack) }
                        }
                        Text(labels[i]) {
                            modifier.font(MsdfFont(MONOCRAFT, 8f))
                                .align(AlignmentX.Center, AlignmentY.Bottom)
                                .margin(bottom = sizes.smallGap * 0.25f)
                                .textColor(Color.WHITE)
                                .zLayer(3000)
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

            if (item.count > 1) Text(item.count.toString()) {
                modifier.font(MsdfFont(MONOCRAFT, 10f))
                    .align(AlignmentX.End, AlignmentY.Bottom)
                    .margin(sizes.smallGap * 0.5f)
                    .zLayer(2000)
            }
        }
    }
}