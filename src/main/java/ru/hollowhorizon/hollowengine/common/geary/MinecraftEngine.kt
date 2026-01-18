package ru.hollowhorizon.hollowengine.common.geary

import co.touchlab.kermit.Logger
import com.mineinabyss.geary.engine.Pipeline
import com.mineinabyss.geary.engine.archetypes.ArchetypeEngine
import com.mineinabyss.geary.helpers.fastForEach
import net.minecraft.server.MinecraftServer
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

class MinecraftEngine(
    val logger: Logger,
    val pipeline: Pipeline,
    val server: MinecraftServer,
    coroutineContext: () -> CoroutineContext,
) : ArchetypeEngine(pipeline, logger, tickDuration = 50.milliseconds, coroutineContext) {
    private var currentTick = 0L

    override fun tick() {
        pipeline.getRepeatingInExecutionOrder()
            .filter {
                it.system.interval?.let { interval ->
                    (currentTick % (interval / tickDuration).toLong().coerceAtLeast(1))
                } == 0L
            }
            .also { logger.v { "Ticking engine with systems $it" } }
            .fastForEach { system ->
                runCatching {
                    system.tick()
                }.onFailure {
                    logger.e { "Error while running system ${system.system.name}" }
                    it.printStackTrace()
                }
            }
        currentTick++
    }
}