package ru.hollowhorizon.hollowengine.client.gui.npcs.trading

import de.fabmax.kool.scene.Scene
import ru.hollowhorizon.hc.common.utils.get
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hollowengine.client.gui.KoolGui
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability


class TradeMenuGui(val npc: NPCEntity, val editMode: Boolean = false) : KoolGui {
    private val GUI = "hollowengine:textures/gui/trades/trade_menu.png".rl
    private val TRADE_ICON = "hollowengine:textures/gui/trades/trade_icon.png".rl
    private var scale = 1f
    var page = (npc[NPCCapability::class].currentTrade / 9).coerceAtLeast(0)
    private var pageCount = 0
    var selectedTrade = npc[NPCCapability::class].currentTrade % 9

    override fun Scene.setup() {
        TODO("Not yet implemented")
    }


}