package ru.hollowhorizon.hollowengine.client.slots

import net.minecraft.client.gui.GuiGraphics
import ru.hollowhorizon.hollowengine.client.utils.mc

/**
 * Draws the vanilla item tooltip for the slot under the cursor.
 *
 * Deliberately not a HollowUi tooltip: item tooltips are the one part of an inventory other mods routinely
 * extend, and going through `GuiGraphics.renderTooltip` keeps every such addition working untouched.
 *
 * Drawn from the screen's own render pass rather than an engine event, because our screens override
 * `Screen.render` and the event is posted from a mixin inside it. Slots hosted in a HUD overlay therefore
 * show no tooltip.
 */
object SlotTooltips {
    private var active: SlotInteraction? = null

    internal fun bind(interaction: SlotInteraction) {
        active = interaction
    }

    internal fun unbind(interaction: SlotInteraction) {
        if (active === interaction) active = null
    }

    /** Called by the hosting screen after its UI frame is on screen. */
    fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val interaction = active ?: return
        // Vanilla hides the tooltip while something is on the cursor, and so do we.
        if (!interaction.carriedStack.isEmpty) return
        val stack = interaction.hoveredStack
        if (stack.isEmpty) return
        graphics.renderTooltip(mc.font, stack, mouseX, mouseY)
    }
}
