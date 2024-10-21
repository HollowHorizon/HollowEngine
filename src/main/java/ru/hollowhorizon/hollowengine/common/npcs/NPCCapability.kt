@file:UseSerializers(ForItemStack::class)

package ru.hollowhorizon.hollowengine.common.npcs

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.client.utils.nbt.ForItemStack
import ru.hollowhorizon.hc.client.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.capabilities.containers.HollowContainer
import ru.hollowhorizon.hc.common.capabilities.containers.container
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.quests.QuestGraph

@HollowCapabilityV2(NPCEntity::class)
class NPCCapability : CapabilityInstance() {
    var hitboxMode by syncable(HitboxMode.PULLING)
    var icon by syncable(NpcIcon.EMPTY)
    var mouseButton by syncable(HoverIcon.NONE)
    var script by syncable("%empty%")

    val trades by syncableList<TradeOffer>()
    var currentTrade by syncable(-1)
    var tradeContainer by container(TradeContainer(this))
}

class TradeContainer(capability: CapabilityInstance) : HollowContainer(capability, 7, intArrayOf(6)) {
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
        val npcData = capability as NPCCapability
        if (npcData.currentTrade == -1 || slot > 5) return super.canPlaceItem(slot, stack)

        val validItem = npcData.trades[npcData.currentTrade].inputs[slot]

        //? if >=1.21 {
        /*return ItemStack.isSameItemSameComponents(validItem, stack)
        *///?} else {
        return ItemStack.isSameItemSameTags(validItem, stack)
        //?}
    }
}

@Serializable
class TradeOffer(var output: ItemStack, val inputs: Array<ItemStack>) {
    fun matches(tradeContainer: HollowContainer): Boolean {
        for (i in inputs.indices) {
            val input = tradeContainer.getItem(i)
            //? if >=1.21 {
            /*if (ItemStack.isSameItemSameComponents(inputs[i], input) && input.count >= inputs[i].count) continue
            *///?} else {
            if (ItemStack.isSameItemSameTags(inputs[i], input) && input.count >= inputs[i].count) continue
            //?}
            else return false
        }
        return true
    }
}

@Serializable
class NpcIcon private constructor(
    val image: @Serializable(ForResourceLocation::class) ResourceLocation,
    var scale: Float = 1f,
    var offsetY: Float = 0f,
) {
    companion object {
        fun create(image: String, scale: Float = 1f, offsetY: Float = 0f) = NpcIcon(image.rl, scale, offsetY)

        val EMPTY = NpcIcon("hollowengine:textures/gui/icons/empty.png".rl)
        val DIALOGUE = NpcIcon("hollowengine:textures/gui/icons/dialogue.png".rl)
        val QUESTION = NpcIcon("hollowengine:textures/gui/icons/question.png".rl)
        val WARN = NpcIcon("hollowengine:textures/gui/icons/warn.png".rl)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is NpcIcon) return false
        return this.image == other.image
    }

    override fun hashCode(): Int {
        return image.hashCode()
    }
}