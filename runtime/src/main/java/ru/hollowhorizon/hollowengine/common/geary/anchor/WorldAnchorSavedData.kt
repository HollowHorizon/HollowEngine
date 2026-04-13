package ru.hollowhorizon.hollowengine.common.geary.anchor

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.saveddata.SavedData
import ru.hollowhorizon.hollowengine.common.geary.snapshot.EntitySerialization
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WorldAnchorSavedData private constructor() : SavedData() {
    private val recordsByChunk = ConcurrentHashMap<Long, LinkedHashMap<UUID, CompoundTag>>()
    private val chunkByStableKey = ConcurrentHashMap<UUID, Long>()

    fun recordsForChunk(chunkKey: Long): List<DormantRecord> =
        recordsByChunk[chunkKey]
            ?.values
            ?.map { tag -> EntitySerialization.deserializeFromNbt(tag) }
            ?.map { snapshot -> DormantRecord(snapshot.requireStableKey(), snapshot) }
            .orEmpty()

    fun forEachRecordInChunk(chunkKey: Long, action: (DormantRecord) -> Unit) {
        recordsByChunk[chunkKey]
            ?.values
            ?.forEach { tag ->
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

    fun allRecords(): List<DormantRecord> = recordsByChunk.values
        .flatMap { records -> records.values }
        .map { tag -> EntitySerialization.deserializeFromNbt(tag) }
        .map { snapshot -> DormantRecord(snapshot.requireStableKey(), snapshot) }

    fun put(record: DormantRecord) {
        val worldAnchor = record.anchor as? WorldAnchor
            ?: error("WorldAnchorSavedData can store only world-anchored records.")
        val chunkKey = ChunkKey.pack(worldAnchor.chunkX, worldAnchor.chunkZ)
        chunkByStableKey.put(record.stableKey, chunkKey)?.takeIf { it != chunkKey }?.let { previousChunk ->
            recordsByChunk[previousChunk]?.remove(record.stableKey)
        }
        recordsByChunk.computeIfAbsent(chunkKey) { linkedMapOf() }[record.stableKey] =
            EntitySerialization.serializeToNbt(record.snapshot) as CompoundTag
        setDirty()
    }

    fun remove(stableKey: UUID): DormantRecord? {
        val chunkKey = chunkByStableKey.remove(stableKey) ?: return null
        val records = recordsByChunk[chunkKey] ?: return null
        val removed = records.remove(stableKey) ?: return null
        if (records.isEmpty()) {
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
        return removed.values
            .map { tag -> EntitySerialization.deserializeFromNbt(tag) }
            .map { snapshot -> DormantRecord(snapshot.requireStableKey(), snapshot) }
    }

    override fun save(tag: CompoundTag): CompoundTag {
        val chunksTag = ListTag()
        recordsByChunk.forEach { (chunkKey, records) ->
            val chunkTag = CompoundTag()
            chunkTag.putLong("chunk", chunkKey)
            val entriesTag = ListTag()
            records.values.forEach(entriesTag::add)
            chunkTag.put("records", entriesTag)
            chunksTag.add(chunkTag)
        }
        tag.put("chunks", chunksTag)
        return tag
    }

    companion object {
        private const val DATA_NAME = "hollowengine_world_anchors"

        fun get(level: ServerLevel): WorldAnchorSavedData {
            return level.dataStorage.computeIfAbsent(
                { tag ->
                    WorldAnchorSavedData().apply {
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
                },
                ::WorldAnchorSavedData,
                DATA_NAME,
            )
        }
    }
}

object ChunkKey {
    fun pack(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() and 0xffffffffL) shl 32 or (chunkZ.toLong() and 0xffffffffL)
}
