package ru.hollowhorizon.hollowengine.common.geary.api

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

/**
 * Reads the pre-[HollowAttachments] entity NBT, where every kind of attached state had its own
 * top-level key.
 */
internal object LegacyEntityNbt {
    private const val COMPONENTS = "EntitySnapshot"
    private const val DATA = "HollowEngineData"
    private const val NODES = "NodeAttachments"

    /** Left over from the resumable coroutine scopes that were removed earlier. */
    private const val SCOPE = "EntityScope"

    private val ALL = listOf(COMPONENTS, DATA, NODES, SCOPE)

    fun isPresent(tag: CompoundTag): Boolean = ALL.any(tag::contains)

    fun componentsOrNull(tag: CompoundTag): CompoundTag? = tag.compoundOrNull(COMPONENTS)

    fun dataOrNull(tag: CompoundTag): CompoundTag? = tag.compoundOrNull(DATA)

    fun nodesOrNull(tag: CompoundTag): CompoundTag? = tag.compoundOrNull(NODES)

    /** Drops the old keys once their content has been read into the new root. */
    fun erase(tag: CompoundTag) {
        ALL.forEach(tag::remove)
    }

    private fun CompoundTag.compoundOrNull(key: String): CompoundTag? =
        takeIf { it.contains(key, Tag.TAG_COMPOUND.toInt()) }?.getCompound(key)
}
