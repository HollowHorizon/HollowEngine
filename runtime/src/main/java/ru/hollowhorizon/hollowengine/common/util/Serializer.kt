package ru.hollowhorizon.hollowengine.common.util

import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag

fun <T> Collection<T>.serialize(serializer: (T) -> Tag): ListTag {
    val tag = ListTag()
    for (item in this) tag.add(serializer(item))
    return tag
}

inline fun <T> MutableCollection<T>.deserialize(list: ListTag, serializer: (Tag) -> T) {
    for(item in list) this.add(serializer(item))
}