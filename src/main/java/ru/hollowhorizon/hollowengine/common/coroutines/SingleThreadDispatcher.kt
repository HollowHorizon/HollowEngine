package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.*
import ru.hollowhorizon.hollowengine.HollowEngine
import java.util.PriorityQueue
import kotlin.Any
import kotlin.Boolean
import kotlin.Comparable
import kotlin.Int
import kotlin.Long
import kotlin.OptIn
import kotlin.String
import kotlin.Throwable
import kotlin.Unit
import kotlin.collections.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlin.synchronized
import kotlin.with

@OptIn(InternalCoroutinesApi::class)
class SingleThreadDispatcher(private val name: String, private val thread: Thread) : CoroutineDispatcher(), Delay {
    private val lock = Any()
    private val queue = ArrayDeque<Runnable>()
    private val delayedQueue = PriorityQueue<ScheduledTask>()
    private var currentTick = 0L
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

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        if (shutdownDelegate != null) return true

        return Thread.currentThread() != thread
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>,
    ) {
        val ticks = (timeMillis + 49) / 50
        synchronized(lock) {
            val targetTick = currentTick + ticks
            val task = Runnable {
                with(continuation) { resumeUndispatched(Unit) }
            }
            delayedQueue.add(ScheduledTask(targetTick, task))
            continuation.invokeOnCancellation {
                synchronized(lock) {
                    delayedQueue.removeIf { it.task == task }
                }
            }
        }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val ticks = (timeMillis + 49) / 50
        val scheduledTask: ScheduledTask

        synchronized(lock) {
            val targetTick = currentTick + ticks
            scheduledTask = ScheduledTask(targetTick, block)
            delayedQueue.add(scheduledTask)
        }

        return DisposableHandle {
            synchronized(lock) {
                delayedQueue.remove(scheduledTask)
            }
        }
    }

    fun runTasks() {
        synchronized(lock) {
            currentTick++

            while (delayedQueue.isNotEmpty()) {
                val head = delayedQueue.peek()
                if (head.targetTick <= currentTick) {
                    delayedQueue.poll()
                    queue.addLast(head.task)
                } else {
                    break
                }
            }
        }

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

    private data class ScheduledTask(val targetTick: Long, val task: Runnable) : Comparable<ScheduledTask> {
        override fun compareTo(other: ScheduledTask): Int = this.targetTick.compareTo(other.targetTick)
    }
}