package ru.hollowhorizon.hollowengine.common.addons

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.HollowEngine

internal object HollowAddonNotifications {
    fun restartRequired(descriptor: HollowAddonDescriptor) {
        val message = "Addon '${descriptor.name}' will be enabled after restarting the game."
        HollowEngine.LOGGER.warn(message)
        if (!HollowAddonRuntimeEnvironment.isClient) return
        Client.notify(message)
    }

    private object Client {
        fun notify(message: String) {
            Minecraft.getInstance().execute {
                Minecraft.getInstance().player?.sendSystemMessage(Component.literal("HollowEngine: $message"))
            }
        }
    }
}
