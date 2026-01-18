package ru.hollowhorizon.hollowengine.common.geary

import com.mineinabyss.geary.helpers.async.AsyncCatcher
import kotlinx.coroutines.*
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.common.coroutines.coroutineScope
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class MinecraftAsyncCatcher(val server: MinecraftServer) {
    fun isAsync(): Boolean {
        return !server.isSameThread
    }

    fun catchOp(message: String) {
        if (isAsync()) {
            throw IllegalStateException("Thread " + Thread.currentThread().name + " failed main thread check: " + message)
        }
    }
}

class MinecraftWarningAsyncCatcher(val server: MinecraftServer) : AsyncCatcher {
    private val logger = com.mojang.logging.LogUtils.getLogger()

    override fun isAsync(): Boolean {
        return !server.isSameThread
    }

    override fun throwException(message: String) {
        logger.warn("Thread ${Thread.currentThread().name} failed main thread check: $message", Throwable())
    }
}

inline fun MinecraftServer.launchTimedRepeating(
    period: Duration,
    context: CoroutineContext = Dispatchers.IO,
    crossinline block: suspend CoroutineScope.() -> Unit,
) = this.coroutineScope.launch(context) {
    while (isActive) {
        val start = TimeSource.Monotonic.markNow()
        block()
        val elapsed = start.elapsedNow()
        delay((period - elapsed).coerceAtLeast(0.milliseconds))
    }
}

inline fun MinecraftServer.launchTickRepeating(
    periodTicks: Int,
    crossinline block: suspend CoroutineScope.() -> Unit,
) = this.coroutineScope.launch(this.dispatcher) {
    while (isActive) {
        block()
        delay((periodTicks * 50).milliseconds)
    }
}