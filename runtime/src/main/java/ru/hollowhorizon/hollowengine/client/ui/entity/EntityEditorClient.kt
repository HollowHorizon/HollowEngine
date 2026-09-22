package ru.hollowhorizon.hollowengine.client.ui.entity

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.client.editor.WorldInspector
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.client.utils.open
import ru.hollowhorizon.hollowengine.common.attachments.editor.EntityEditorSnapshot
import ru.hollowhorizon.hollowengine.common.attachments.editor.RequestEntityEditorPacket

/**
 * Client-side entity editor: asks the server for an entity's state and shows it on the reply. The
 * request has to go through the server, because the editor shows components that are not
 * `@Syncable` and so never reach client.
 */
object EntityEditorClient {
    /** Where the answer should end up. The request says so, because the reply cannot. */
    enum class Destination { SCREEN, INSPECTOR }

    /** What each in-flight request asked for, keyed by entity. Only ever touched on the client. */
    private val destinations = HashMap<Int, Destination>()

    init {
        BuiltinComponentEditors.register()
    }

    /** Asks to edit [entity]; the answer arrives where [destination] says. */
    fun request(entity: Entity, destination: Destination = Destination.SCREEN) {
        destinations[entity.id] = destination
        RequestEntityEditorPacket(entity.id).send()
    }

    /** The server opened the slot session the item dialog binds to. */
    internal fun acceptSlots(entityId: Int, sessionId: Int) {
        mc.execute {
            val session = (mc.screen as? EntityEditorScreen)?.session ?: WorldInspector.inspectorSession
            if (session?.entityId == entityId) session.acceptSlots(sessionId)
        }
    }

    internal fun accept(state: EntityEditorSnapshot) {
        mc.execute {
            val open = mc.screen as? EntityEditorScreen
            if (open != null && open.session.entityId == state.entityId) {
                open.session.accept(state)
                return@execute
            }
            val destination = when {
                WorldInspector.holds(state.entityId) -> Destination.INSPECTOR
                else -> destinations.remove(state.entityId) ?: Destination.SCREEN
            }
            when (destination) {
                Destination.INSPECTOR -> WorldInspector.accept(state)
                Destination.SCREEN -> EntityEditorScreen(EntityEditorSession(state)).open()
            }
        }
    }

    internal fun forget(entityId: Int) {
        destinations.remove(entityId)
    }
}
