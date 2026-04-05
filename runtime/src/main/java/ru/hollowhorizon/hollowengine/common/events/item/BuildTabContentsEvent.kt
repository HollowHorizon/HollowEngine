package ru.hollowhorizon.hollowengine.common.events.item

import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTab.TabVisibility
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike
//? if forge
/*import net.minecraftforge.common.util.MutableHashedLinkedMap*/
import ru.hollowhorizon.hollowengine.common.events.Event

class BuildTabContentsEvent(
    val tab: CreativeModeTab,
    val tabKey: ResourceKey<CreativeModeTab>,
    val parameters: CreativeModeTab.ItemDisplayParameters,
    private val acceptor: (ItemStack, TabVisibility) -> Unit
): Event, CreativeModeTab.Output {
    val flags = this.parameters.enabledFeatures

    val hasPermissions = this.parameters.hasPermissions

    override fun accept(stack: ItemStack, tabVisibility: TabVisibility) {
        acceptor(stack, tabVisibility)
    }

    fun accept(item: () -> ItemLike, visibility: TabVisibility) {
        this.accept(item(), visibility)
    }

    fun accept(item: () -> ItemLike) {
        this.accept(item())
    }

    fun acceptFor(tab: CreativeModeTab, stack: ItemStack, visibility: TabVisibility) {
        if (this.tab != tab) return
        this.accept(stack, visibility)
    }

    fun acceptFor(tab: CreativeModeTab, item: () -> ItemLike, visibility: TabVisibility) {
        if (this.tab != tab) return
        this.accept(item, visibility)
    }

    fun acceptFor(tab: CreativeModeTab, stack: ItemStack) {
        if (this.tab != tab) return
        this.accept(stack)
    }

    fun acceptFor(tab: CreativeModeTab, item: () -> ItemLike) {
        if (this.tab != tab) return
        this.accept(item)
    }

    fun acceptForAll(tab: CreativeModeTab, stacks: Collection<ItemStack>, visibility: TabVisibility) {
        if (this.tab != tab) return
        this.acceptAll(stacks, visibility)
    }

    fun acceptForAll(tab: CreativeModeTab, stacks: Collection<ItemStack>) {
        if (this.tab != tab) return
        this.acceptAll(stacks)
    }
}