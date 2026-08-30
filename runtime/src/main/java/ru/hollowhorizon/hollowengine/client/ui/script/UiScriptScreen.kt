package ru.hollowhorizon.hollowengine.client.ui.script

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.client.slots.ClientSlots
import ru.hollowhorizon.hollowengine.client.slots.SlotTooltips
import ru.hollowhorizon.hollowengine.client.ui.screen.HollowComposeUiScreen
import ru.hollowhorizon.hollowengine.client.ui.style.CompiledHss
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.ui.UiData
import ru.hollowhorizon.hollowengine.common.ui.UiGuiScale
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
    val replaced: Screen? = null,
) : HollowComposeUiScreen(definition.title, EmptyStyles), UiScope {

    override fun send(payload: CompoundTag) {
        sessionId?.let { UiScriptClient.send(it, payload) }
    }

    override fun close() {
        mc.setScreen(null)
    }

    @Composable
    override fun Content() {
        CompositionLocalProvider(LocalReplacedScreen provides replaced) {
            definition.content(this@UiScriptScreen)
        }
    }

    override fun shouldCloseOnEsc(): Boolean = definition.closeOnEscape

    override fun onClose() {
        if (dismiss()) return
        super.onClose()
    }

    /**
     * Slots override a script's `pausesGame`. A paused singleplayer world stops ticking its server, and the
     * server is what owns the slots: pausing would freeze the very side that answers every click.
     */
    override fun isPauseScreen(): Boolean = definition.pausesGame && !hasSlots()

    override fun rebuildEveryFrame(): Boolean = definition.rebuildEveryFrame

    override fun guiScale(): UiGuiScale = definition.guiScale

    override fun renderAfterUi(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (hasSlots()) SlotTooltips.render(graphics, mouseX, mouseY)
        if (dismissedAt != 0L && System.currentTimeMillis() - dismissedAt >= definition.exitDuration) {
            mc.setScreen(null)
        }
    }

    /**
     * The server has dropped the session; the screen stays up for [UiScreenDefinition.exitDuration]
     * so its closing frames can play, then takes itself away. Returns false when it has no exit
     * animation to wait for and the caller should close it outright.
     */
    fun dismiss(): Boolean {
        if (definition.exitDuration <= 0L) return false
        if (dismissedAt == 0L) dismissedAt = System.currentTimeMillis()
        return true
    }

    /** When the server dismissed this screen, or 0 while it is still live. */
    private var dismissedAt by mutableStateOf(0L)

    override val isClosing: Boolean get() = dismissedAt != 0L

    private fun hasSlots(): Boolean = sessionId?.let { ClientSlots[it] } != null

    override fun removed() {
        super.removed()
        val session = sessionId ?: return
        if (UiAdaptiveSurfaces.isSwapping(session)) return
        UiScriptClient.notifyClosed(session)
    }

    private companion object {
        val EmptyStyles = CompiledHss(emptyList())
    }
}
