package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.scripting.nodes.NodeManager

class ServerRuntimeContext(
    private val server: MinecraftServer,
) {
    val scope = ServerOwnerScope(
        server.dispatcher + SupervisorJob(server.coroutineScope.coroutineContext[Job]),
        ::markDirty,
    )
    val nodes = NodeManager(server)

    private var dirty = false

    fun serialize(tag: CompoundTag) {
        tag.put("scope", CompoundTag().also(scope::serialize))
        tag.put("nodes", CompoundTag().also(nodes::serialize))
    }

    fun deserialize(tag: CompoundTag) {
        scope.deserialize(tag.getCompound("scope"))
        nodes.deserialize(tag.getCompound(if (tag.contains("nodes")) "nodes" else "components"))
        dirty = false
    }

    fun dispose() {
        nodes.dispose()
        scope.cancelAll()
    }

    fun isDirty(): Boolean = dirty

    fun clearDirty() {
        dirty = false
    }

    fun markDirty() {
        dirty = true
    }
}

class ServerOwnerScope(
    override val coroutineContext: kotlin.coroutines.CoroutineContext,
    onDirty: (() -> Unit)? = null,
) : OwnerScope(coroutineContext, onDirty)

val MinecraftServer.runtimeContext get() = ServerRuntimeState.context(this)
