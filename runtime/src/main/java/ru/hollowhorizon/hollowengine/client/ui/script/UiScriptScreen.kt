package ru.hollowhorizon.hollowengine.client.ui.script

import androidx.compose.runtime.Composable
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.slots.ClientSlots
import ru.hollowhorizon.hollowengine.client.slots.SlotTooltips
import ru.hollowhorizon.hollowengine.client.ui.screen.HollowComposeUiScreen
import ru.hollowhorizon.hollowengine.client.ui.style.CompiledHss
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.ui.UiData
import ru.hollowhorizon.hollowengine.common.ui.UiScope
import ru.hollowhorizon.hollowengine.common.ui.UiScreenDefinition

/**
 * Hosts a screen declared by a `.ui.kts` script. Styling comes from the script's own
 * `Modifier.style(...)` calls, so no stylesheet is bound at the surface level.
 */
class UiScriptScreen(
    private val definition: UiScreenDefinition,
    override val data: UiData,
    override val sessionId: Int?,
) : HollowComposeUiScreen(definition.title, EmptyStyles), UiScope {

    override fun send(payload: CompoundTag) {
        sessionId?.let { UiScriptClient.send(it, payload) }
    }

    override fun close() {
        mc.setScreen(null)
    }

    @Composable
    override fun Content() {
        definition.content(this)
    }

    override fun shouldCloseOnEsc(): Boolean = definition.closeOnEscape

    /**
     * Slots override a script's `pausesGame`. A paused singleplayer world stops ticking its server, and the
     * server is what owns the slots: pausing would freeze the very side that answers every click.
     */
    override fun isPauseScreen(): Boolean = definition.pausesGame && !hasSlots()

    override fun rebuildEveryFrame(): Boolean = definition.rebuildEveryFrame

    override fun renderAfterUi(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (hasSlots()) SlotTooltips.render(graphics, mouseX, mouseY)
    }

    private fun hasSlots(): Boolean = sessionId?.let { ClientSlots[it] } != null

    override fun removed() {
        super.removed()
        sessionId?.let(UiScriptClient::notifyClosed)
    }

    private companion object {
        val EmptyStyles = CompiledHss(emptyList())
    }
}
