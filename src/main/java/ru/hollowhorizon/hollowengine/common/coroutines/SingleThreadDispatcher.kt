package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
import ru.hollowhorizon.hollowengine.HollowEngine
import kotlin.coroutines.CoroutineContext

class SingleThreadDispatcher(private val name: String) : CoroutineDispatcher() {
    private val lock = Any()
    private val queue = ArrayDeque<Runnable>()
    private var shutdownDelegate: CoroutineDispatcher? = null

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(lock) {
            val shutdownDelegate = shutdownDelegate
            if (shutdownDelegate == null) {
                queue.addLast(block)
            } else {
                shutdownDelegate.dispatch(context, block)
            }
        }
    }

    fun runTasks() {
        var tasksToRun = synchronized(lock) { queue.size }

        while (tasksToRun > 0) {
            val task = synchronized(lock) { queue.removeFirstOrNull() } ?: break

            try {
                task.run()
            } catch (e: Throwable) {
                HollowEngine.LOGGER.error("Exception while running coroutine!", e)
            }

            tasksToRun--
        }
    }

    fun shutdown() {
        while (true) {
            runTasks()
            synchronized(lock) {
                if (queue.isEmpty()) {
                    shutdownDelegate = Dispatchers.IO.limitedParallelism(1)
                    return
                }
            }
        }
    }

    override fun toString(): String {
        return name
    }
}