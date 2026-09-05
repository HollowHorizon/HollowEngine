package ru.hollowhorizon.hollowengine.common.scripting.nodes

import kotlinx.coroutines.CoroutineScope
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import kotlin.coroutines.CoroutineContext

/**
 * Base class of every `*.node.kts` script. The script body runs as the constructor, so by the time it
 * executes [binding] is already set and the registration handlers ([onStart], [onStop], [onSave],
 * [onLoad], [onUpdate], entity handlers) can attach themselves to this node's [CoroutineScope].
 */
abstract class NodeScript(
    val path: String,
    internal val binding: NodeBinding,
) : CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = binding.scope.coroutineContext

    val host: NodeHost get() = binding.host
    val server: MinecraftServer get() = binding.host.server

    internal val onSaveHandlers = mutableListOf<(SerializationContext) -> Unit>()
    internal val onLoadHandlers = mutableListOf<(SerializationContext) -> Unit>()
    internal val onStartHandlers = mutableListOf<suspend CoroutineScope.() -> Unit>()

    val editor: NodeEditor = NodeEditor(this)
}

data class SerializationContext(
    val server: MinecraftServer,
    val tag: CompoundTag,
)
