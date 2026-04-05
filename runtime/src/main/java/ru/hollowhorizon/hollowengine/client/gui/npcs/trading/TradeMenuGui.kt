package ru.hollowhorizon.hollowengine.client.gui.npcs.trading

import de.fabmax.kool.scene.Scene
import ru.hollowhorizon.hollowengine.common.utils.rl
import ru.hollowhorizon.hollowengine.client.gui.KoolGui
import ru.hollowhorizon.hollowengine.common.entities.NpcEntity


class TradeMenuGui(val npc: NpcEntity, val editMode: Boolean = false) : KoolGui {
    private val GUI = "hollowengine:textures/gui/trades/trade_menu.png".rl
    private val TRADE_ICON = "hollowengine:textures/gui/trades/trade_icon.png".rl
    private var scale = 1f
    private var pageCount = 0

    override fun Scene.setup() {
        TODO("Not yet implemented")
    }


}