package ru.hollowhorizon.hollowengine.client.scripting

import ru.hollowhorizon.hollowengine.client.ui.notification.HollowNotifications
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.events.EventListener
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent

/**
 * Tells player that startup scripts failed. They run before the game window exists, so the
 * notification waits for the first client tick.
 */
internal object StartupScriptNotice {
    fun show(scripts: List<String>) {
        TickEvent.Client.register(object : EventListener<TickEvent.Client> {
            override fun invoke(event: TickEvent.Client) {
                TickEvent.Client.unregister(this)
                HollowNotifications.error(
                    "hollowengine.gui.notification.startup_failed".lang.format(scripts.joinToString()),
                )
            }
        })
    }
}
