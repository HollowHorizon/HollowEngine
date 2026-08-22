package ru.hollowhorizon.hollowengine.common.attachments.api

import kotlinx.coroutines.CoroutineScope
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope
import ru.hollowhorizon.hollowengine.common.data.NbtDataStore
import ru.hollowhorizon.hollowengine.common.attachments.sync.ComponentSync
import ru.hollowhorizon.hollowengine.common.attachments.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.scripting.nodes.EntityNodeManager

/**
 * Everything attached to one entity, under one owner.
 *
 * The split between them is deliberate and stays:
 * - [components] are *data* other systems read - the renderer, the AI runtime, the client. They are the
 *   only part that goes over the network, and only when the descriptor says so.
 * - [data] is the script-owned storage, addressed by entity rather than by script on purpose, so one script
 *   can read what another left on an NPC.
 * - [nodes] are script-behavior, server-side only.
 * - [runtime] is whatever should live exactly as long as the entity and never be saved or sent.
 */
class HollowAttachments internal constructor(entity: MCEntity) {
    var entity: MCEntity = entity
        internal set

    val components = ComponentStore()
    val scope: CoroutineScope = EntityScope(entity)

    private var dataStore: NbtDataStore? = null
    private var nodeManager: EntityNodeManager? = null
    private var runtimeState: RuntimeAttachments? = null

    /** The script storage, or null when nothing has ever been written to it. Creates nothing. */
    val dataOrNull: NbtDataStore? get() = dataStore

    val data: NbtDataStore get() = dataStore ?: NbtDataStore().also { dataStore = it }

    /**
     * State that lives exactly as long as this entity: drawn models, their animators, anything else
     * worth keeping while it exists. Never saved, never synced, gone when the entity is removed.
     */
    val runtime: RuntimeAttachments get() = runtimeState ?: RuntimeAttachments().also { runtimeState = it }

    /** The node scripts attached to this entity, or null when none were ever attached. */
    val nodesOrNull: EntityNodeManager? get() = nodeManager

    val nodes: EntityNodeManager
        get() = nodeManager ?: EntityNodeManager(entity).also { nodeManager = it }

    /**
     * Rises with every batch [ComponentSync] sends for this entity on the server, and records the last
     * batch applied on the client.
     */
    var syncVersion: Long = 0L

    /** What the clients tracking this entity were last told, so a batch can carry only the difference. */
    var lastSyncedComponents: Map<ResourceLocation, Component> = emptyMap()

    init {
        components.onChange = { ComponentSync.markDirty(entity) }
    }

    internal fun adoptData(store: NbtDataStore?) {
        dataStore = store
    }

    /**
     * Rebinds this attachment set to a new instance of the same entity. The scope is not carried over:
     * the old one is canceled by the caller, and node scripts are re-attached from their saved state.
     */
    internal fun rebindTo(newEntity: MCEntity): HollowAttachments =
        HollowAttachments(newEntity).also { target ->
            target.components.putAll(components.copyOf())
            target.dataStore = dataStore
            target.syncVersion = syncVersion
            target.lastSyncedComponents = lastSyncedComponents
        }
}
