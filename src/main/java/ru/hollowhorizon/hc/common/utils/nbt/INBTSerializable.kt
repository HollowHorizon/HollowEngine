package ru.hollowhorizon.hc.common.utils.nbt

import net.minecraft.nbt.Tag

interface INBTSerializable {
    fun serialize(): Tag
    fun deserialize(tag: Tag)
}