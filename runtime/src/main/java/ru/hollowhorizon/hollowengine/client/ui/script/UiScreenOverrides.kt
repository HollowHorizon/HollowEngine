package ru.hollowhorizon.hollowengine.client.ui.script

import androidx.compose.runtime.compositionLocalOf
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.client.ui.screen.HollowComposeUiScreen
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.ScreenEvent
import ru.hollowhorizon.hollowengine.common.ui.UiData
import ru.hollowhorizon.hollowengine.common.ui.UiDefinitionRegistry

/**
 * The screen a scripted screen was opened in place of, or null when it was opened by id.
 */
val LocalReplacedScreen = compositionLocalOf<Screen?> { null }

/**
 * Swaps a vanilla or modded screen for the scripted screen that declared `override(...)` against it.
 */
object UiScreenOverrides {
    /** The screen to show instead of [screen], or null to leave it alone. */
    fun replacement(screen: Screen): UiScriptScreen? {
        if (!UiDefinitionRegistry.hasScreenOverrides) return null
        if (screen is HollowComposeUiScreen) return null
        val definition = UiDefinitionRegistry.screenOverride(screen.javaClass) ?: return null
        return UiScriptScreen(definition, UiData(), sessionId = null, replaced = screen)
    }

    /**
     * Rebuilds the open override after `.ui.kts` scripts are recompiled: its content belongs to a
     * class that no longer exists.
     */
    fun reload() {
        val current = mc.screen as? UiScriptScreen ?: return
        val replaced = current.replaced ?: return
        val definition = UiDefinitionRegistry.screenOverride(replaced.javaClass) ?: run {
            mc.setScreen(null)
            return
        }
        mc.setScreen(UiScriptScreen(definition, UiData(), sessionId = null, replaced = replaced))
    }
}

@SubscribeEvent
fun onScreenOpenApplyOverride(event: ScreenEvent.Open) {
    val replacement = UiScreenOverrides.replacement(event.screen) ?: return
    event.screen = replacement
}
