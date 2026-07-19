package ru.hollowhorizon.hollowengine.common.scripting.state

import kotlinx.coroutines.delay
import net.minecraft.nbt.CompoundTag
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import java.lang.Math.addExact
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Waits for loaded server ticks and resumes from the same deadline after a save and restart.
 * Must be called from an `@State` function.
 */
suspend fun waitPersistent(name: String, ticks: Int) {
    waitPersistent(
        tag = stateTag(),
        name = name,
        ticks = ticks,
        currentTick = { currentServer.overworld().gameTime },
        pause = { delay(TICK_DURATION) },
    )
}

/**
 * Waits for wall-clock time, including time for which the game was not running.
 * Must be called from an `@State` function.
 */
suspend fun waitRealtime(name: String, duration: Duration) {
    require(duration.isFinite() && duration >= Duration.ZERO) {
        "Realtime wait duration must be finite and non-negative"
    }
    val now = System.currentTimeMillis()
    waitUntil(name, Instant.ofEpochMilli(addExact(now, duration.inWholeMilliseconds)))
}

/**
 * Waits until an absolute date while preserving the first stored deadline across restarts.
 * Must be called from an `@State` function.
 */
suspend fun waitUntil(name: String, instant: Instant) {
    waitUntil(
        tag = stateTag(),
        name = name,
        requestedDeadline = instant.toEpochMilli(),
        nowMillis = System::currentTimeMillis,
        pause = { delay(it.milliseconds) },
    )
}

internal suspend fun waitPersistent(
    tag: CompoundTag,
    name: String,
    ticks: Int,
    currentTick: () -> Long,
    pause: suspend () -> Unit,
) {
    require(ticks >= 0) { "Persistent wait duration cannot be negative" }
    val key = waitKey(GAME_TIME_KIND, name)
    val deadline = if (tag.contains(key)) {
        tag.getLong(key)
    } else {
        addExact(currentTick(), ticks.toLong()).also { tag.putLong(key, it) }
    }

    while (currentTick() < deadline) pause()
    tag.remove(key)
}

internal suspend fun waitUntil(
    tag: CompoundTag,
    name: String,
    requestedDeadline: Long,
    nowMillis: () -> Long,
    pause: suspend (Long) -> Unit,
) {
    val key = waitKey(REAL_TIME_KIND, name)
    val deadline = if (tag.contains(key)) tag.getLong(key) else requestedDeadline.also { tag.putLong(key, it) }

    while (true) {
        val remaining = deadline - nowMillis()
        if (remaining <= 0L) break
        pause(minOf(remaining, MAX_REALTIME_SLEEP_MILLIS))
    }
    tag.remove(key)
}

private fun waitKey(kind: String, name: String): String {
    require(name.isNotBlank()) { "Persistent wait name cannot be blank" }
    return "$WAIT_PREFIX.$kind.$name"
}

private val TICK_DURATION = 50.milliseconds
private const val WAIT_PREFIX = "wait$"
private const val GAME_TIME_KIND = "game"
private const val REAL_TIME_KIND = "real"
private const val MAX_REALTIME_SLEEP_MILLIS = 60_000L
