package ru.hollowhorizon.hc.client.kool.addons

import de.fabmax.kool.modules.ui2.*
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack

class ContainerModifier(surface: UiSurface) : UiModifier(surface) {
    var slotSize: Dimension by property(FitContent)
}

open class ContainerProvider(val container: Container, parent: UiNode?, surface: UiSurface) : UiNode(parent, surface) {
    override val modifier = ContainerModifier(surface)

    init {
        val rows = mutableListOf(mutableListOf<ItemStack>())
        for (i in 0..<container.containerSize) {
            rows.last().add(container.getItem(i))
            if (i % 9 == 0) rows.add(mutableListOf())
        }

        rows.forEachIndexed { i, list ->
            Row {
                list.forEachIndexed { j, itemStack ->
                    slot(i*9+j, itemStack)
                }
            }
        }
        surface.onUpdate {
            update()
        }
    }

    fun update() {
        previousContainer = container
    }

    fun slot(i: Int, item: ItemStack) {
        //Graphics.slot(i, item, slotSize, container = container)
    }

    fun splitItems() {
        return // TODO: Make it work on server

//        if (slots.size > 1 && ImGui.isMouseDown(ImGuiMouseButton.Left)) {
//            val item = slots.map { container.getItem(it) }.firstOrNull { !it.isEmpty }
//            if (item != null) {
//                val count = slots.sumOf { container.getItem(it).count } + (ClientContainerManager.PLAYERS_HOLD_STACKS[Minecraft.getInstance().player!!.uuid]?.count ?: 0)
//                val eachCount = count / slots.size
//                val holdCount = count % slots.size
//
//                slots.forEach {
//                    container.setItem(
//                        it,
//                        item.copy().apply { this.count = eachCount })
//                }
//                item.count = holdCount
//                ClientContainerManager.PLAYERS_HOLD_STACKS[Minecraft.getInstance().player!!.uuid] = item
//            }
//        }
    }

    companion object {
        var previousContainer: Container? = null
    }
}