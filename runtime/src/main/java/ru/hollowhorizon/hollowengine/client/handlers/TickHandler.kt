package ru.hollowhorizon.hollowengine.client.handlers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient

object TickHandler {
    var serverTick = 0
        private set

    val currentFrame get() = if (isLogicalClient) clientFrame else serverTick
    val partialTick
        get() = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)

    val deltaFrameTime
        get() = Minecraft.getInstance().timer.realtimeDeltaTicks / 20f
    val gameTime get() = currentFrame + partialTick

    var clientFrame: Int = 0
        internal set(value) {
            field = value
            _frameFlow.update { value }
        }
    private val _frameFlow = MutableStateFlow(0)
    val frameFlow: StateFlow<Int> = _frameFlow.asStateFlow()


    @SubscribeEvent
    @ClientOnly
    fun onClientTick(event: TickEvent.Client) {
        clientFrame++
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.Server) {
        serverTick++
    }
}
