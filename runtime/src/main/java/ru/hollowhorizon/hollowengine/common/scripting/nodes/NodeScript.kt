package ru.hollowhorizon.hollowengine.common.scripting.nodes

import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer

abstract class NodeScript(val path: String) {
    open fun onSave(context: SerializationContext) {}
    open fun onLoad(context: SerializationContext) {}
    open fun onStart() {}
    open fun onStop() {}
}

data class SerializationContext(
    val server: MinecraftServer,
    val tag: CompoundTag
)