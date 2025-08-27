package ru.hollowhorizon.hc.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable
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
        while (true) {
            val task = synchronized(lock) {
                check(shutdownDelegate == null) { "Dispatcher has been shut down" }
                queue.removeFirstOrNull()
            } ?: break
            task.run()
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