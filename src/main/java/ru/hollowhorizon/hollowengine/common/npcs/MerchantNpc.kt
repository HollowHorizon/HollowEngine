package ru.hollowhorizon.hollowengine.common.npcs

import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.trading.Merchant
import net.minecraft.world.item.trading.MerchantOffer
import net.minecraft.world.item.trading.MerchantOffers

class MerchantNpc: Merchant {
    var tradePlayer: Player? = null
    var npcOffers = MerchantOffers()

    override fun setTradingPlayer(pTradingPlayer: Player?) {
        tradePlayer = pTradingPlayer
    }

    override fun getTradingPlayer() = tradePlayer

    override fun getOffers() = npcOffers

    override fun overrideOffers(pOffers: MerchantOffers) {}

    override fun notifyTrade(pOffer: MerchantOffer) {
        pOffer.increaseUses()
    }

    override fun notifyTradeUpdated(pStack: ItemStack) {
    }

    override fun getVillagerXp() = 0

    override fun overrideXp(pXp: Int) {}

    override fun showProgressBar() = true

    override fun getNotifyTradeSound() = SoundEvents.VILLAGER_YES

    override fun isClientSide() = false
}