package ru.hollowhorizon.hollowengine.client.ui.script

import androidx.compose.runtime.Composable
import net.minecraft.nbt.CompoundTag
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
    private val sessionId: Int?,
) : HollowComposeUiScreen(definition.title, EmptyStyles), UiScope {

    override val isServerBound: Boolean get() = sessionId != null

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

    override fun isPauseScreen(): Boolean = definition.pausesGame

    override fun rebuildEveryFrame(): Boolean = definition.rebuildEveryFrame

    override fun removed() {
        super.removed()
        sessionId?.let(UiScriptClient::notifyClosed)
    }

    private companion object {
        val EmptyStyles = CompiledHss(emptyList())
    }
}
