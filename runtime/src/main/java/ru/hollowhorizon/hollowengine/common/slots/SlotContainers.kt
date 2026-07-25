package ru.hollowhorizon.hollowengine.common.slots

import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.slots.net.SlotLayoutPacket
import ru.hollowhorizon.hollowengine.common.ui.net.UiSession
import ru.hollowhorizon.hollowengine.common.ui.net.UiSessionManager
import java.util.concurrent.ConcurrentHashMap

/**
 * The open slot UIs on this server, keyed by the UI session that owns them.
 *
 * A separate registry rather than a field on the session manager: slots are one thing a UI may have, and
 * the UI layer has no reason to grow a dependency on them. The session's own lifecycle still drives
 * everything: closing it unregisters the container and hands the cursor stack back.
 */
object SlotContainers {
    private val containers = ConcurrentHashMap<Int, SlotContainer>()

    operator fun get(sessionId: Int): SlotContainer? = containers[sessionId]

    internal fun register(container: SlotContainer) {
        containers[container.session.id] = container
    }

    /** Routes an intent, refusing sessions that belong to somebody else. */
    internal fun onIntent(player: Player, sessionId: Int, revision: Int, intent: SlotIntent) {
        val container = containers[sessionId] ?: return
        if (container.player.uuid != player.uuid) return
        container.handle(revision, intent)
    }

    private fun release(sessionId: Int) {
        containers.remove(sessionId)?.releaseCarried()
    }

    internal fun tick() {
        if (containers.isEmpty()) return
        containers.values.forEach { container ->
            if (!container.session.isOpen) {
                release(container.session.id)
                return@forEach
            }
            if (!container.isValid()) {
                UiSessionManager.close(container.session)
                return@forEach
            }
            container.syncExternalChanges()
        }
    }

    internal fun onSessionClosed(sessionId: Int) = release(sessionId)
}

/**
 * Declares the slots of the UI this session drives.
 *
 * ```kotlin
 * player.openUi("mypack:npc_gear") {
 *     slots {
 *         zone("gear", EquipmentSource(npc, EquipmentSource.All)) { role = SlotZoneRole.TARGET }
 *         zone("npc", NpcInventorySource(npc.inventory))
 *         zone("player", ContainerSource(player.inventory))
 *         quickMove("gear" to "player")
 *     }
 * }
 * ```
 *
 * The declaration lives here, on the server, because it is what authorises every later click: a client
 * addresses slots by zone and index and can only ever reach storage this block bound. The `.ui.kts`
 * screen refers to the same zone names and decides purely how they are laid out.
 */
fun UiSession.slots(block: SlotZonesBuilder.() -> Unit): SlotContainer {
    val builder = SlotZonesBuilder().apply(block)
    val container = SlotContainer(this, builder.buildLayout(), builder.buildBindings(), builder.validity)
    slotContainer = container
    SlotContainers.register(container)
    onCloseInternal { SlotContainers.onSessionClosed(id) }

    // Sent from inside the session body, so it lands before the packet that opens the screen: the client
    // needs the layout to exist by the time the screen's first composition asks about a zone.
    SlotLayoutPacket(id, container.layout, container.state.contents(), container.state.carried, container.revision)
        .send(player)
    return container
}

@SubscribeEvent
fun onServerTickSyncSlots(event: TickEvent.Server) = SlotContainers.tick()
