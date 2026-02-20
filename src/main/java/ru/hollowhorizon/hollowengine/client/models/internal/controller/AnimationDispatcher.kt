package ru.hollowhorizon.hollowengine.client.models.internal.controller

import kotlinx.coroutines.*
import ru.hollowhorizon.hollowengine.HollowEngine
import java.util.PriorityQueue
import kotlin.Any
import kotlin.Boolean
import kotlin.Comparable
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.OptIn
import kotlin.String
import kotlin.Throwable
import kotlin.Unit
import kotlin.collections.ArrayDeque
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.synchronized
import kotlin.with

@OptIn(InternalCoroutinesApi::class)
class AnimationDispatcher(private val name: String) : CoroutineDispatcher(), Delay {
    private val lock = Any()
    private val queue = ArrayDeque<Runnable>()
    private val nextFrameQueue = ArrayDeque<Runnable>()
    private val delayedQueue = PriorityQueue<ScheduledTask>()

    private var currentTimeMs = 0L

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(lock) {
            if (context[FrameYieldMarker.Key] != null) {
                nextFrameQueue.addLast(block)
            } else {
                queue.addLast(block)
            }
        }
    }

    suspend fun awaitNextFrame() {
        suspendCancellableCoroutine { cont ->
            dispatch(FrameYieldMarker) { cont.resume(Unit) }
        }
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        synchronized(lock) {
            val targetTime = currentTimeMs + timeMillis
            val task = Runnable {
                with(continuation) { resumeUndispatched(Unit) }
            }
            delayedQueue.add(ScheduledTask(targetTime, task))

            continuation.invokeOnCancellation {
                synchronized(lock) {
                    delayedQueue.removeIf { it.task == task }
                }
            }
        }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val scheduledTask: ScheduledTask
        synchronized(lock) {
            val targetTime = currentTimeMs + timeMillis
            scheduledTask = ScheduledTask(targetTime, block)
            delayedQueue.add(scheduledTask)
        }

        return DisposableHandle {
            synchronized(lock) {
                delayedQueue.remove(scheduledTask)
            }
        }
    }

    fun update(deltaTime: Float) {
        val dtMs = (deltaTime * 1000).toLong()

        synchronized(lock) {
            currentTimeMs += dtMs

            while (nextFrameQueue.isNotEmpty()) {
                queue.addLast(nextFrameQueue.removeFirst())
            }

            while (delayedQueue.isNotEmpty() && delayedQueue.peek().targetTimeMs <= currentTimeMs) {
                queue.addLast(delayedQueue.poll().task)
            }
        }

        while (true) {
            val task = synchronized(lock) { queue.removeFirstOrNull() } ?: break
            try {
                task.run()
            } catch (e: Throwable) {
                HollowEngine.LOGGER.error("Animation Coroutine Error in $name", e)
            }
        }
    }

    override fun toString(): String = name

    private data class ScheduledTask(val targetTimeMs: Long, val task: Runnable) : Comparable<ScheduledTask> {
        override fun compareTo(other: ScheduledTask): Int = this.targetTimeMs.compareTo(other.targetTimeMs)
    }

    private object FrameYieldMarker : CoroutineContext.Element {
        override val key = Key
        object Key : CoroutineContext.Key<FrameYieldMarker>
    }
}