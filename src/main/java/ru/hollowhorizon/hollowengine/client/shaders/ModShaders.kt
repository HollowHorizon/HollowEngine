package ru.hollowhorizon.hollowengine.client.shaders

import com.mojang.blaze3d.shaders.Uniform
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraftforge.client.event.RegisterShadersEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.registries.ForgeRegistries
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID
import java.util.function.Consumer

object ModShaders {
    lateinit var PARTICLE: HollowShaderInstance

    @SubscribeEvent
    fun onShaderRegistry(event: RegisterShadersEvent) {
        val holder = ShaderHolder(HashMap(), arrayOf("LumiTransparency"))
        PARTICLE = object : HollowShaderInstance(event.resourceManager, "$MODID:particles/particle".rl, DefaultVertexFormat.PARTICLE) {
            override fun holder() = holder
        }

        event.registerShader(PARTICLE) {}
    }
}