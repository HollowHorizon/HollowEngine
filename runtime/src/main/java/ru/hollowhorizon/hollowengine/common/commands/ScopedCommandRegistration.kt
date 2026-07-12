package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.tree.CommandNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList

internal class ScopedCommandRegistration<S>(
    scope: CoroutineScope,
    private val executeMutation: (() -> Unit) -> Unit,
) {
    private val contributions = CopyOnWriteArrayList<CommandContribution<S>>()

    init {
        requireNotNull(scope.coroutineContext[Job]).invokeOnCompletion {
            executeMutation(::removeContributions)
        }
    }

    fun register(dispatcher: CommandDispatcher<S>, registration: () -> Unit) {
        val before = snapshot(dispatcher.root)
        try {
            registration()
        } finally {
            contributions += findContributions(before)
        }
    }

    private fun snapshot(
        root: CommandNode<S>,
    ): List<CommandNodeSnapshot<S>> {
        val snapshots = ArrayList<CommandNodeSnapshot<S>>()
        val visited = IdentityHashMap<CommandNode<S>, Boolean>()
        val queue = ArrayDeque<CommandNode<S>>()
        queue += root

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (visited.put(node, true) != null) continue
            val children = node.children.associateBy(CommandNode<S>::getName)

            snapshots += CommandNodeSnapshot(
                parent = node,
                children = children,
            )

            children.values.forEach(queue::addLast)
        }
        return snapshots
    }

    private fun findContributions(
        snapshots: List<CommandNodeSnapshot<S>>,
    ): List<CommandContribution<S>> =
        snapshots.flatMap { snapshot ->
            snapshot.parent.children
                .associateBy(CommandNode<S>::getName)
                .filterKeys { name -> name !in snapshot.children }
                .map { (name, node) ->
                    CommandContribution(
                        parent = WeakReference(snapshot.parent),
                        name = name,
                        node = WeakReference(node),
                    )
                }
        }

    private fun removeContributions() {
        contributions.asReversed().forEach { contribution ->
            val parent = contribution.parent.get()
                ?: return@forEach
            val node = contribution.node.get()
                ?: return@forEach
            CommandNodeInternals.removeChild(parent, contribution.name, node)
        }

        contributions.clear()
    }

    private data class CommandNodeSnapshot<S>(
        val parent: CommandNode<S>,
        val children: Map<String, CommandNode<S>>,
    )

    private data class CommandContribution<S>(
        val parent: WeakReference<CommandNode<S>>,
        val name: String,
        val node: WeakReference<CommandNode<S>>,
    )
}
