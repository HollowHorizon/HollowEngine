package ru.hollowhorizon.hollowengine.client.ui.entity

import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.client.utils.mc
import ru.hollowhorizon.hollowengine.client.utils.open
import ru.hollowhorizon.hollowengine.common.attachments.editor.EntityEditorSnapshot
import ru.hollowhorizon.hollowengine.common.attachments.editor.RequestEntityEditorPacket

/**
 * Client-side entity editor: asks the server for an entity's state and opens the screen on
 * the reply. Request has to go through the server, because editor shows components that are not
 * `@Syncable` and so never reach client.
 */
object EntityEditorClient {
    init {
        BuiltinComponentEditors.register()
    }

    /** Asks to edit [entity]; the screen opens when the server answers. */
    fun request(entity: Entity) {
        RequestEntityEditorPacket(entity.id).send()
    }

    /** The server opened the slot session the item dialog binds to. */
    internal fun acceptSlots(entityId: Int, sessionId: Int) {
        mc.execute {
            val screen = mc.screen as? EntityEditorScreen ?: return@execute
            if (screen.session.entityId == entityId) screen.session.acceptSlots(sessionId)
        }
    }

    internal fun accept(state: EntityEditorSnapshot) {
        mc.execute {
            val open = mc.screen as? EntityEditorScreen
            if (open != null && open.session.entityId == state.entityId) open.session.accept(state)
            else EntityEditorScreen(EntityEditorSession(state)).open()
        }
    }
}
