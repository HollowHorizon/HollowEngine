package ru.hollowhorizon.hollowengine.client.handlers

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.utils.isLogicalClient
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.tick.TickEvent

object TickHandler {
    private var clientTicks = 0
    private var serverTicks = 0

    val currentTicks get() = if (isLogicalClient) clientTicks else serverTicks
    val partialTick
        get() =
            //? if >=1.21 {
            /*Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(false)
            *///?} else {
            Minecraft.getInstance().frameTime
            //?}
    val deltaFrameTime
        get() =
            //? if >=1.21 {
            /*Minecraft.getInstance().timer.realtimeDeltaTicks
            *///?} else {
            Minecraft.getInstance().deltaFrameTime
            //?}
    val time get() = currentTicks + partialTick

    @SubscribeEvent
    fun onClientTick(event: TickEvent.Client) {
        clientTicks++
    }

    @SubscribeEvent
    fun onServerTick(event: TickEvent.Server) {
        serverTicks++
    }
}
