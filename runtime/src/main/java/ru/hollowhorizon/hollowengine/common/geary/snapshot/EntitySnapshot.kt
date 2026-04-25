package ru.hollowhorizon.hollowengine.common.geary.snapshot

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.common.geary.api.Component
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentDescriptorRegistry
import ru.hollowhorizon.hollowengine.common.geary.components.ComponentPersistencePolicy
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.*

@Serializable
data class EntityNodeSnapshot(
    val id: @Serializable(ForUuid::class) UUID = UUID.randomUUID(),
    val parentId: @Serializable(ForUuid::class) UUID? = null,
    val components: List<@Polymorphic Component> = emptyList(),
)

@Serializable
data class EntitySnapshot(
    val version: Int = CURRENT_VERSION,
    val stableKey: @Serializable(ForUuid::class) UUID = UUID.randomUUID(),
    val hostUuid: @Serializable(ForUuid::class) UUID? = null,
    val primary: Boolean = false,
    val worldChunkX: Int? = null,
    val worldChunkZ: Int? = null,
    val worldLocalId: @Serializable(ForUuid::class) UUID? = null,
    val rootNodeId: @Serializable(ForUuid::class) UUID? = null,
    val nodes: List<EntityNodeSnapshot> = emptyList(),
    val components: List<@Polymorphic Component> = emptyList(),
) {
    fun nodeList(): List<EntityNodeSnapshot> =
        if (nodes.isNotEmpty()) {
            nodes
        } else {
            listOf(EntityNodeSnapshot(id = LEGACY_ROOT_NODE_ID, parentId = null, components = components))
        }

    fun rootNode(): EntityNodeSnapshot {
        val available = nodeList()
        val explicit = rootNodeId
        if (explicit != null) {
            available.firstOrNull { it.id == explicit }?.let { return it }
        }
        return available.firstOrNull { it.parentId == null } ?: available.first()
    }

    fun withNodes(updatedNodes: List<EntityNodeSnapshot>, explicitRootNodeId: UUID? = rootNodeId): EntitySnapshot {
        val root = explicitRootNodeId
            ?: updatedNodes.firstOrNull { it.parentId == null }?.id
            ?: updatedNodes.firstOrNull()?.id
            ?: LEGACY_ROOT_NODE_ID
        val rootNode = updatedNodes.firstOrNull { it.id == root } ?: EntityNodeSnapshot(id = root, components = emptyList())
        return copy(
            rootNodeId = root,
            nodes = updatedNodes,
            components = rootNode.components,
        )
    }

    fun componentById(): LinkedHashMap<ResourceLocation, Component> =
        LinkedHashMap<ResourceLocation, Component>().apply {
            rootNode().components.forEach { component ->
                val id = ComponentDescriptorRegistry.idFor(component::class)
                    ?: error("Component descriptor not found for ${component::class.qualifiedName}")
                put(id, component)
            }
        }

    fun dropLooseOnDeathComponents(): EntitySnapshot = copy(
        components = components.filterNot { component ->
            ComponentDescriptorRegistry.descriptorOrNull(component::class)?.persistencePolicy == ComponentPersistencePolicy.LOOSE_ON_DEATH
        },
        nodes = nodeList().map { node ->
            node.copy(
                components = node.components.filterNot { component ->
                    ComponentDescriptorRegistry.descriptorOrNull(component::class)?.persistencePolicy == ComponentPersistencePolicy.LOOSE_ON_DEATH
                }
            )
        })

    companion object {
        const val CURRENT_VERSION: Int = 4
        val LEGACY_ROOT_NODE_ID: UUID = UUID(0L, 1L)
    }
}

inline fun <reified T : Component> EntitySnapshot.withComponent(component: T): EntitySnapshot =
    withComponent(component, rootNode().id)

fun EntitySnapshot.withComponent(component: Component, nodeId: UUID): EntitySnapshot {
    val updated = nodeList().map { node ->
        if (node.id != nodeId) node
        else node.copy(components = node.components.filter { it::class != component::class } + component)
    }
    return withNodes(updated)
}
