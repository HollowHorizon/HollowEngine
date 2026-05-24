package ru.hollowhorizon.hollowengine.common.coroutines

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.utils.nbt.loadAsNBT
import ru.hollowhorizon.hollowengine.common.utils.nbt.save
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.WeakHashMap

private const val HOLLOWENGINE_RUNTIME_FILE = "hollowengine-server-runtime.dat"

private data class ServerRuntimeStateEntry(
    val runtimeContext: ServerRuntimeContext,
    val runtimePath: Path,
    var autosaveTicks: Int = 0,
)

object ServerRuntimeState {
    private val states = Collections.synchronizedMap(WeakHashMap<MinecraftServer, ServerRuntimeStateEntry>())

    fun create(server: MinecraftServer, levelRoot: Path) {
        states.computeIfAbsent(server) {
            ServerRuntimeStateEntry(
                runtimeContext = ServerRuntimeContext(server),
                runtimePath = levelRoot.resolve("data").resolve(HOLLOWENGINE_RUNTIME_FILE),
            )
        }
    }

    fun load(server: MinecraftServer) {
        val state = entry(server)
        if (!Files.exists(state.runtimePath)) {
            state.runtimeContext.startLoaders()
            return
        }

        try {
            Files.newInputStream(state.runtimePath).use { stream ->
                val tag = stream.loadAsNBT()
                if (tag is CompoundTag) {
                    entry(server).runtimeContext.deserialize(tag)
                }
            }
        } catch (exception: Exception) {
            HollowCore.LOGGER.error("Failed to load HollowEngine server runtime from {}", state.runtimePath, exception)
        }
    }

    fun autosave(server: MinecraftServer) {
        val state = entry(server)
        if (!state.runtimeContext.isDirty()) {
            state.autosaveTicks = 0
            return
        }

        state.autosaveTicks++
        if (state.autosaveTicks >= 200) {
            save(server)
        }
    }

    fun save(server: MinecraftServer) {
        val state = entry(server)
        if (!state.runtimeContext.isDirty()) return

        try {
            Files.createDirectories(state.runtimePath.parent)
            val tag = CompoundTag()
            state.runtimeContext.serialize(tag)
            Files.newOutputStream(state.runtimePath).use { stream -> tag.save(stream) }
            state.runtimeContext.clearDirty()
            state.autosaveTicks = 0
        } catch (exception: Exception) {
            HollowCore.LOGGER.error("Failed to save HollowEngine server runtime to {}", state.runtimePath, exception)
        }
    }

    fun remove(server: MinecraftServer) {
        states.remove(server)?.runtimeContext?.dispose()
    }

    fun context(server: MinecraftServer): ServerRuntimeContext = entry(server).runtimeContext

    private fun entry(server: MinecraftServer) =
        states[server] ?: error("Server runtime state is not initialized for $server")
}
