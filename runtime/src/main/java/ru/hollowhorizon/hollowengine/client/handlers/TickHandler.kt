package ru.hollowhorizon.hollowengine.client.handlers

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient

object TickHandler {
    private var clientTicks = 0
    private var serverTicks = 0

    val currentTicks get() = if (isLogicalClient) clientTicks else serverTicks
    val partialTick
        get() = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)

    val deltaFrameTime
        get() = Minecraft.getInstance().timer.realtimeDeltaTicks
    val time get() = currentTicks + partialTick

    @SubscribeEvent
    @ClientOnly
    fun onClientTick(event: TickEvent.Client) {
        clientTicks++
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.Server) {
        serverTicks++
    }
}
