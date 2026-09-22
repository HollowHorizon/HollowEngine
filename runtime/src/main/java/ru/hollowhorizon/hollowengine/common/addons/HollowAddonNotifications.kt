package ru.hollowhorizon.hollowengine.common.addons

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.ui.notification.HollowNotifications
import ru.hollowhorizon.hollowengine.client.utils.lang

internal object HollowAddonNotifications {
    fun restartRequired(descriptor: HollowAddonDescriptor) {
        HollowEngine.LOGGER.warn("Addon '{}' will be enabled after restarting the game.", descriptor.name)
        if (!HollowAddonRuntimeEnvironment.isClient) return
        Client.restartRequired(descriptor.name)
    }

    private object Client {
        fun restartRequired(name: String) {
            HollowNotifications.warning("hollowengine.gui.notification.addon_restart".lang.format(name))
        }
    }
}
