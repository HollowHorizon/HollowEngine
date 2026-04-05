package ru.hollowhorizon.hollowengine.common.handlers

import net.minecraft.client.gui.screens.Screen
import net.minecraft.locale.Language
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.ItemTooltipEvent
import ru.hollowhorizon.hollowengine.common.utils.mcTranslate

object HollowEventHandler {

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        val desc = event.itemStack.item.descriptionId + ".hc_desc"
        val shiftDesc = event.itemStack.item.descriptionId + ".hc_shift_desc"
        val lang = Language.getInstance()

        if (lang.has(desc)) event.toolTip.add(desc.mcTranslate)

        if (Screen.hasShiftDown() && lang.has(shiftDesc)) event.toolTip.add(desc.mcTranslate)
    }
}
