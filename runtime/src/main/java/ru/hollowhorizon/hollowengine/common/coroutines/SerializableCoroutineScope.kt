package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.CoroutineScope
import net.minecraft.nbt.CompoundTag
import kotlin.coroutines.CoroutineContext

interface SerializableCoroutineScope : CoroutineScope {
    fun serialize(tag: CompoundTag)
    fun deserialize(tag: CompoundTag)
}

interface SerializableCoroutineContextElement : CoroutineContext.Element {
    fun save(tag: CompoundTag)
    fun load(tag: CompoundTag) {}
}
