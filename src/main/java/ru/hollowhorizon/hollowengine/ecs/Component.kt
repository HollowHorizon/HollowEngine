package ru.hollowhorizon.hollowengine.ecs

import net.minecraft.nbt.CompoundTag

interface Component {
    fun save(tag: CompoundTag) {}
    fun load(tag: CompoundTag) {}
}