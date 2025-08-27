package ru.hollowhorizon.hollowengine.common.fsm

import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.utils.currentServer

class TransitionRequest(val path: String) : CancellationException()

open class StateNode(
    val name: String,
    val parent: StateNode? = null,
    var initializer: suspend StateNode.() -> Unit,
) {
    val children = mutableMapOf<String, StateNode>()
    var currentState = "main"
    var tag = CompoundTag()

    fun pathToRoot(): List<String> =
        parent?.pathToRoot().orEmpty() + name

    private fun resolvePath(relativePath: String): StateNode {
        val parts = relativePath.split("/")
        val base = if (relativePath.startsWith("/")) {
            root()
        } else this
        return parts.fold(base) { node, part ->
            when (part) {
                ".", "" -> node
                ".." -> node.parent ?: error("Parent not found!")
                else -> node.children[part] ?: error("Child with name $part not found!")
            }
        }
    }

    private fun root(): StateNode = parent?.root() ?: this

    suspend fun start() {
        var current: StateNode = this

        while (true) {
            try {
                withContext(StateStorage(current.tag)) {
                    current.children.clear()
                    current.apply { initializer() }
                }

                // Если в состоянии явно задан переход по тегу
                val next = current.tag.get("__state__")?.asString?.let {
                    current.resolvePath(it)
                } ?: current.children["main"]

                if (next == null) break
                current = next
            } catch (e: TransitionRequest) {
                current.parent?.tag?.putString("__state__", e.path)
                val target = current.parent?.resolvePath(e.path)
                    ?: error("State not found: ${e.path}")
                current = target
            }
        }
    }

    fun transition(path: String): Nothing {
        throw TransitionRequest(path)
    }

    fun state(path: String, initializer: suspend StateNode.() -> Unit): StateNode {
        assert(!path.contains("/")) { "State can't contain '/'!" }

        val child = StateNode(path, this, initializer)
        if (!tag.contains(path)) tag.put(path, CompoundTag())
        child.tag = tag.getCompound(path)
        children[path] = child
        return child
    }

    fun <T> async(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> T,
    ): Deferred<T> = currentServer.coroutineScope.async(start = start, block = block)
}