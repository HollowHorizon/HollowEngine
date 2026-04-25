package ru.hollowhorizon.hollowengine.common.geary.binding

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class WorldNodeSavedData private constructor() : SavedData() {
    private val recordsByChunk = ConcurrentHashMap<Long, LinkedHashMap<UUID, CompoundTag>>()
    private val chunkByStableKey = ConcurrentHashMap<UUID, Long>()

    fun recordsForChunk(chunkKey: Long): List<DormantRecord> =
        snapshotChunkTags(chunkKey)
            .map { tag -> EntitySerialization.deserializeFromNbt(tag) }
            .map { snapshot -> DormantRecord(snapshot.requireStableKey(), snapshot) }

    fun forEachRecordInChunk(chunkKey: Long, action: (DormantRecord) -> Unit) {
        snapshotChunkTags(chunkKey).forEach { tag ->
            val snapshot = EntitySerialization.deserializeFromNbt(tag)
            action(DormantRecord(snapshot.requireStableKey(), snapshot))
        }
    }

    fun forEachRecordInChunkRange(centerChunkX: Int, centerChunkZ: Int, radius: Int, action: (DormantRecord) -> Unit) {
        for (chunkX in centerChunkX - radius..centerChunkX + radius) {
            for (chunkZ in centerChunkZ - radius..centerChunkZ + radius) {
                forEachRecordInChunk(ChunkKey.pack(chunkX, chunkZ), action)
            }
        }
    }

    fun allRecords(): List<DormantRecord> = snapshotAllTags()
        .map { tag -> EntitySerialization.deserializeFromNbt(tag) }
        .map { snapshot -> DormantRecord(snapshot.requireStableKey(), snapshot) }

    fun put(record: DormantRecord) {
        val chunkKey = ChunkKey.pack(record.worldChunkX, record.worldChunkZ)
        chunkByStableKey.put(record.stableKey, chunkKey)?.takeIf { it != chunkKey }?.let { previousChunk ->
            recordsByChunk[previousChunk]?.let { records ->
                synchronized(records) {
                    records.remove(record.stableKey)
                }
            }
        }
        val serialized = EntitySerialization.serializeToNbt(record.snapshot) as CompoundTag
        val records = recordsByChunk.computeIfAbsent(chunkKey) { linkedMapOf() }
        synchronized(records) {
            records[record.stableKey] = serialized
        }
        setDirty()
    }

    fun remove(stableKey: UUID): DormantRecord? {
        val chunkKey = chunkByStableKey.remove(stableKey) ?: return null
        val records = recordsByChunk[chunkKey] ?: return null
        val removed = synchronized(records) {
            records.remove(stableKey)
        } ?: return null
        if (synchronized(records) { records.isEmpty() }) {
            recordsByChunk.remove(chunkKey, records)
        }
        setDirty()
        val snapshot = EntitySerialization.deserializeFromNbt(removed)
        return DormantRecord(snapshot.requireStableKey(), snapshot)
    }

    fun removeChunk(chunkKey: Long): List<DormantRecord> {
        val removed = recordsByChunk.remove(chunkKey).orEmpty()
        removed.keys.forEach(chunkByStableKey::remove)
        if (removed.isNotEmpty()) setDirty()
        return synchronized(removed) {
            removed.values.toList()
        }
            .map { tag -> EntitySerialization.deserializeFromNbt(tag) }
            .map { snapshot -> DormantRecord(snapshot.requireStableKey(), snapshot) }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val chunksTag = ListTag()
        recordsByChunk.forEach { (chunkKey, records) ->
            val chunkTag = CompoundTag()
            chunkTag.putLong("chunk", chunkKey)
            val entriesTag = ListTag()
            synchronized(records) {
                records.values.toList()
            }.forEach(entriesTag::add)
            chunkTag.put("records", entriesTag)
            chunksTag.add(chunkTag)
        }
        tag.put("chunks", chunksTag)
        return tag
    }

    companion object {
        private const val DATA_NAME = "hollowengine_world_nodes"

        fun get(level: ServerLevel): WorldNodeSavedData {
            return level.dataStorage.computeIfAbsent(
                Factory(::WorldNodeSavedData, { tag, _ ->
                    WorldNodeSavedData().apply {
                        val chunks = tag.getList("chunks", Tag.TAG_COMPOUND.toInt())
                        for (index in 0 until chunks.size) {
                            val chunkTag = chunks.getCompound(index)
                            val chunkKey = chunkTag.getLong("chunk")
                            val records = linkedMapOf<UUID, CompoundTag>()
                            val list = chunkTag.getList("records", Tag.TAG_COMPOUND.toInt())
                            for (entryIndex in 0 until list.size) {
                                val entryTag = list.getCompound(entryIndex)
                                val snapshot = EntitySerialization.deserializeFromNbt(entryTag)
                                val stableKey = snapshot.requireStableKey()
                                records[stableKey] = entryTag
                                chunkByStableKey[stableKey] = chunkKey
                            }
                            if (records.isNotEmpty()) {
                                recordsByChunk[chunkKey] = records
                            }
                        }
                    }
                }, DataFixTypes.LEVEL),
                DATA_NAME,
            )
        }
    }

    private fun snapshotChunkTags(chunkKey: Long): List<CompoundTag> {
        val records = recordsByChunk[chunkKey] ?: return emptyList()
        return synchronized(records) {
            records.values.toList()
        }
    }

    private fun snapshotAllTags(): List<CompoundTag> {
        val all = ArrayList<CompoundTag>()
        recordsByChunk.values.forEach { records ->
            synchronized(records) {
                all.addAll(records.values)
            }
        }
        return all
    }
}

object ChunkKey {
    fun pack(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() and 0xffffffffL) shl 32 or (chunkZ.toLong() and 0xffffffffL)
}
