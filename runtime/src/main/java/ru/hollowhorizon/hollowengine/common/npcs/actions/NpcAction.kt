package ru.hollowhorizon.hollowengine.common.npcs.actions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

@JvmInline
value class NpcActionKey(val id: String) {
    init {
        require(id.isNotBlank()) { "NPC action key cannot be blank" }
    }
}

object NpcActionKeys {
    val MOVEMENT = NpcActionKey("hollowengine:movement")
    val LOOK = NpcActionKey("hollowengine:look")
    val HANDS = NpcActionKey("hollowengine:hands")
    val ANIMATION = NpcActionKey("hollowengine:animation")
}

enum class NpcActionStatus {
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED,
}

interface NpcAction<out R> {
    val status: NpcActionStatus
    val isActive: Boolean

    fun cancel()
    suspend fun await(): R
}

class NpcActionController(private val scope: CoroutineScope) {
    private val lock = Any()
    private val actions = mutableMapOf<NpcActionKey, NpcActionImpl<*>>()

    fun <R> start(key: NpcActionKey, block: suspend CoroutineScope.() -> R): NpcAction<R> {
        val deferred = scope.async(start = CoroutineStart.LAZY, block = block)
        val action = NpcActionImpl(deferred)
        synchronized(lock) { actions.put(key, action) }?.cancel()
        deferred.invokeOnCompletion {
            synchronized(lock) {
                if (actions[key] === action) actions.remove(key)
            }
        }
        deferred.start()
        return action
    }

    fun cancel(key: NpcActionKey) {
        synchronized(lock) { actions.remove(key) }?.cancel()
    }

    fun cancelAll() {
        val running = synchronized(lock) {
            actions.values.toList().also { actions.clear() }
        }
        running.forEach(NpcActionImpl<*>::cancel)
    }
}

private class NpcActionImpl<R>(private val deferred: Deferred<R>) : NpcAction<R> {
    @Volatile
    override var status: NpcActionStatus = NpcActionStatus.RUNNING
        private set

    init {
        deferred.invokeOnCompletion { cause ->
            status = when (cause) {
                null -> NpcActionStatus.COMPLETED
                is CancellationException -> NpcActionStatus.CANCELLED
                else -> NpcActionStatus.FAILED
            }
        }
    }

    override val isActive: Boolean
        get() = deferred.isActive

    override fun cancel() {
        deferred.cancel()
    }

    override suspend fun await(): R = try {
        deferred.await()
    } catch (exception: CancellationException) {
        deferred.cancel(exception)
        throw exception
    }
}
