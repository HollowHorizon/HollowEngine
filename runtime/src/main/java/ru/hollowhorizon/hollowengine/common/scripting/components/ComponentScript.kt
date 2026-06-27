package ru.hollowhorizon.hollowengine.common.scripting.components

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer

abstract class ComponentScript(val path: String) {
    open fun onSave(context: SerializationContext) {}
    open fun onLoad(context: SerializationContext) {}
    open fun onStart() {}
    open fun onStop() {}
}

data class SerializationContext(
    val server: MinecraftServer,
    val tag: CompoundTag
)