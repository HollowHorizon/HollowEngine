package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.scripting.components.ComponentSystem

class ServerRuntimeContext(
    private val server: MinecraftServer,
) {
    val scope = ServerOwnerScope(
        server.dispatcher + SupervisorJob(server.coroutineScope.coroutineContext[Job]),
        ::markDirty,
    )
    val components = ComponentSystem(server)

    private var dirty = false

    fun serialize(tag: CompoundTag) {
        tag.put("scope", CompoundTag().also(scope::serialize))
        tag.put("components", CompoundTag().also(components::serialize))
    }

    fun deserialize(tag: CompoundTag) {
        scope.deserialize(tag.getCompound("scope"))
        components.deserialize(tag.getCompound("components"))
        dirty = false
    }

    fun dispose() {
        components.dispose()
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
