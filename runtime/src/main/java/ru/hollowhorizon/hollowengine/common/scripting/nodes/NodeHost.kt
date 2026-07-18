package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.CoroutineScope
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity

/**
 * Describes what a node script is attached to. Every host exposes the [server] it belongs to so
 * lifecycle handlers ([onSave]/[onLoad]) can build a [SerializationContext].
 */
sealed interface NodeHost {
    val server: MinecraftServer

    class Server(override val server: MinecraftServer) : NodeHost

    class OfEntity(val entity: Entity) : NodeHost {
        override val server: MinecraftServer
            get() = entity.server ?: error("Entity ${entity.uuid} is not attached to a server")
    }
}

class NodeBinding(val host: NodeHost, val scope: CoroutineScope)

@JvmInline
value class Ticks(val count: Int)

val Int.ticks: Ticks get() = Ticks(this)
