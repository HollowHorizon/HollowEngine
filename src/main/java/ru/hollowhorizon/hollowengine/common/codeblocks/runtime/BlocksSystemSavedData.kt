package ru.hollowhorizon.hollowengine.common.codeblocks.runtime

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent

class BlocksSystemSavedData private constructor(
    private val server: MinecraftServer,
) : SavedData() {
    val system: BlocksSystem = BlocksSystem(server).also { it.dirtyListener = ::markDirty }

    fun markDirty() {
        setDirty()
    }

    override fun save(tag: CompoundTag): CompoundTag {
        system.serialize(tag)
        return tag
    }

    companion object {
        private const val DATA_NAME = "hollowengine_blocks_system"

        fun get(server: MinecraftServer): BlocksSystem {
            val storage = server.overworld().dataStorage
            val data = storage.computeIfAbsent(
                { tag ->
                    BlocksSystemSavedData(server).apply {
                        system.deserialize(tag)
                    }
                },
                {
                    BlocksSystemSavedData(server).apply {
                        system.reloadScripts()
                    }
                },
                DATA_NAME,
            )
            return data.system
        }
    }
}

@SubscribeEvent
fun onServerStart(event: LevelEvent.Load) {
    if (event.level.dimension() != Level.OVERWORLD) return
    event.level.server?.let(BlocksSystemSavedData::get)
}
