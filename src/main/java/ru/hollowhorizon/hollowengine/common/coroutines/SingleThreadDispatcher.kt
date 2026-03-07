package ru.hollowhorizon.hollowengine.common.coroutines

import kotlinx.coroutines.*
import ru.hollowhorizon.hollowengine.HollowEngine
import java.lang.Runnable
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
    private var isShutdown = false

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(lock) {
            if (isShutdown) return
            queue.addLast(block)
        }
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return Thread.currentThread() != thread
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>,
    ) {
        val ticks = (timeMillis + 49) / 50
        val scheduledTask: ScheduledTask

        synchronized(lock) {
            if (isShutdown) {
                continuation.cancel(CancellationException("Dispatcher $name is shutdown"))
                return
            }

            scheduledTask = ScheduledTask(
                targetTick = currentTick + ticks,
                task = { with(continuation) { resumeUndispatched(Unit) } },
                onShutdown = { continuation.cancel(CancellationException("Dispatcher $name is shutdown")) },
            )
            delayedQueue.add(scheduledTask)
        }

        continuation.invokeOnCancellation {
            synchronized(lock) {
                delayedQueue.remove(scheduledTask)
            }
        }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val ticks = (timeMillis + 49) / 50
        val scheduledTask: ScheduledTask

        synchronized(lock) {
            if (isShutdown) return DisposableHandle {}

            scheduledTask = ScheduledTask(targetTick = currentTick + ticks, task = block)
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
            if (isShutdown) return

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
        val delayedToCancel = synchronized(lock) {
            if (isShutdown) return
            isShutdown = true
            val pending = delayedQueue.toList()
            queue.clear()
            delayedQueue.clear()
            pending
        }

        delayedToCancel.forEach { it.onShutdown?.invoke() }
    }

    override fun toString(): String {
        return name
    }

    private data class ScheduledTask(
        val targetTick: Long,
        val task: Runnable,
        val onShutdown: (() -> Unit)? = null,
    ) : Comparable<ScheduledTask> {
        override fun compareTo(other: ScheduledTask): Int = targetTick.compareTo(other.targetTick)
    }
}
