package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import net.minecraft.world.item.trading.MerchantOffer
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.SimpleNode

infix fun NPCProperty.addTrade(offer: () -> MerchantOffer) = next {
    this@addTrade().npcTrader.npcOffers.add(offer())
}

fun NPCProperty.clearTrades() = next {
    this@clearTrades().npcTrader.npcOffers.clear()
}

fun NPCProperty.clearTradeUses() = next {
    this@clearTradeUses().npcTrader.npcOffers.forEach { it.resetUses() }
}