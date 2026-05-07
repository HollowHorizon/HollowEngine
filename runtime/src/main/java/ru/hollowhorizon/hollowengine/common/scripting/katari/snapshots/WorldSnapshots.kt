package ru.hollowhorizon.hollowengine.common.scripting.katari.snapshots

import com.sunnychung.lib.multiplatform.kotlite.katari.ValueRestoreContext
import com.sunnychung.lib.multiplatform.kotlite.katari.ValueSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.scripting.katari.KatariRestoreContext
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshot
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptSnapshotFactory
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptType
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation

@Serializable
@SerialName("hollowengine:katari/server")
@ScriptType("Server")
class ServerSnapshot() : ValueSnapshot(), ScriptSnapshot<MinecraftServer> {
    override suspend fun restore(context: ValueRestoreContext): MinecraftServer {
        return (context as? KatariRestoreContext)?.server
            ?: error("Server can only be restored with KatariRestoreContext")
    }

    companion object : ScriptSnapshotFactory<MinecraftServer, ServerSnapshot> {
        override fun capture(value: MinecraftServer): ServerSnapshot = ServerSnapshot()
    }
}

@Serializable
@SerialName("hollowengine:katari/level")
@ScriptType("Level")
data class LevelSnapshot(
    val dimension: @Serializable(ForResourceLocation::class) ResourceLocation,
) : ValueSnapshot(), ScriptSnapshot<ServerLevel> {
    override suspend fun restore(context: ValueRestoreContext): ServerLevel {
        val server = (context as? KatariRestoreContext)?.server
            ?: error("Level can only be restored with KatariRestoreContext")
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension))
            ?: error("Dimension `$dimension` is not loaded")
    }

    companion object : ScriptSnapshotFactory<ServerLevel, LevelSnapshot> {
        override fun capture(value: ServerLevel): LevelSnapshot {
            return LevelSnapshot(value.dimension().location())
        }
    }
}
