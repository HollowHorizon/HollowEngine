package ru.hollowhorizon.hc.common.events.registry

import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.common.events.Event

class RegisterShadersEvent: Event {
    val shaders = hashMapOf<ResourceLocation, Pair<VertexFormat, (ShaderInstance) -> Unit>>()

    fun register(location: ResourceLocation, format: VertexFormat, consumer: (ShaderInstance) -> Unit) {
        shaders += location to Pair(format, consumer)
    }
}