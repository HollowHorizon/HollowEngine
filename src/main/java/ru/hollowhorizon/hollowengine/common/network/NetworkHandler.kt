package ru.hollowhorizon.hollowengine.common.network

import net.minecraft.resources.ResourceLocation
import net.minecraftforge.network.NetworkRegistry
import net.minecraftforge.network.simple.SimpleChannel

object NetworkHandler {
    val HOLLOW_ENGINE_CHANNEL = ResourceLocation("hollowengine", "hollow_engine_channel")
    lateinit var HollowEngineChannel: SimpleChannel

    fun register() {
        HollowEngineChannel = NetworkRegistry.newSimpleChannel(HOLLOW_ENGINE_CHANNEL,
            { "1.0" },
            { it == "1.0" },
            { it == "1.0" }
        )

        PacketRegisterer.register()
    }
}