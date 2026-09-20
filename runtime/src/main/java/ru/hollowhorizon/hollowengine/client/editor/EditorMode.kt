package ru.hollowhorizon.hollowengine.client.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.utils.PlayerPermissions

@ClientOnly
object EditorMode {
    private var enabledValue by mutableStateOf(false)
    private var loaded = false

    private val listeners = ArrayList<(Boolean) -> Unit>()

    val isEnabled: Boolean
        get() {
            load()
            return enabledValue
        }

    fun toggle() = setEnabled(!isEnabled)

    fun setEnabled(enabled: Boolean) {
        load()
        if (enabledValue == enabled) return
        enabledValue = enabled
        HollowEngineConfig.editorMode = enabled
        listeners.toList().forEach { it(enabled) }
    }

    fun onChanged(listener: (Boolean) -> Unit) {
        listeners += listener
    }

    fun isAvailable(): Boolean {
        val minecraft = Minecraft.getInstance()
        return minecraft.level != null && minecraft.player?.hasPermissions(PlayerPermissions.GAMEMASTER) == true && (minecraft.screen == null || minecraft.screen is ChatScreen)
    }

    fun isActive(): Boolean = isEnabled && isAvailable()

    private fun load() {
        if (loaded) return
        loaded = true
        enabledValue = HollowEngineConfig.editorMode
    }
}
