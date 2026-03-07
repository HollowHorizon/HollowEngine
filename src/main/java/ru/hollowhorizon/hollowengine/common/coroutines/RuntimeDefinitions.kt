package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import net.minecraft.nbt.CompoundTag
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@JvmInline
value class RuntimeDefinitionId(val value: String) {
    override fun toString(): String = value
}

fun interface RuntimeExecutionContextFactory {
    fun create(): RuntimeExecutionState
}

interface RuntimeExecutionState {
    fun save(tag: CompoundTag)
    fun load(tag: CompoundTag) {}
    fun buildCoroutineContext(): CoroutineContext = EmptyCoroutineContext
    fun installCancel(callback: () -> Unit) {}
}

data class RuntimeDefinition(
    val id: RuntimeDefinitionId,
    val contextFactory: RuntimeExecutionContextFactory,
    val context: CoroutineContext = EmptyCoroutineContext,
    val start: CoroutineStart = CoroutineStart.DEFAULT,
    val block: suspend CoroutineScope.(RuntimeExecutionState) -> Unit,
)

object RuntimeDefinitionRegistry {
    private val definitions = ConcurrentHashMap<RuntimeDefinitionId, RuntimeDefinition>()

    fun register(definition: RuntimeDefinition) {
        definitions[definition.id] = definition
    }

    fun registerAll(definitions: Iterable<RuntimeDefinition>) {
        definitions.forEach(::register)
    }

    fun unregister(id: RuntimeDefinitionId) {
        definitions.remove(id)
    }

    fun unregisterByPrefix(prefix: String) {
        definitions.keys.removeIf { it.value.startsWith(prefix) }
    }

    fun resolve(id: RuntimeDefinitionId): RuntimeDefinition? = definitions[id]

    fun contains(id: RuntimeDefinitionId): Boolean = definitions.containsKey(id)
}
