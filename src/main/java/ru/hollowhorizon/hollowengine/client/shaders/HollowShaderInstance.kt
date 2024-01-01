package ru.hollowhorizon.hollowengine.client.shaders

import com.google.gson.JsonElement
import com.mojang.blaze3d.shaders.Uniform
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceProvider
import net.minecraft.util.GsonHelper
import java.nio.FloatBuffer
import java.util.function.Consumer


abstract class HollowShaderInstance(
    provider: ResourceProvider,
    location: ResourceLocation,
    format: VertexFormat
) : ShaderInstance(provider, location, format) {
    abstract fun holder(): ShaderHolder

    fun setUniformDefaults() {
        for ((key, value) in holder().defaultUniformData.entries) {
            val t = getUniform(key) ?: continue
            value.accept(t)
        }
    }

    override fun parseUniformNode(pJson: JsonElement) {
        super.parseUniformNode(pJson)

        val uniformName = GsonHelper.getAsString(pJson.asJsonObject, "name")
        if (uniformName in holder().uniformNames) {
            val uniform = uniforms[uniforms.size - 1]

            val consumer = if (uniform.type <= 3) {
                val buffer = uniform.intBuffer
                buffer.position(0)
                val array = IntArray(uniform.count)
                for (i in 0 until uniform.count) array[i] = buffer[i]
                Consumer { _: Uniform ->
                    buffer.position(0)
                    buffer.put(array)
                }
            } else {
                val buffer: FloatBuffer = uniform.floatBuffer
                buffer.position(0)
                val array = FloatArray(uniform.count)
                for (i in 0 until uniform.count) array[i] = buffer[i]
                Consumer { _: Uniform ->
                    buffer.position(0)
                    buffer.put(array)
                }
            }

            holder().defaultUniformData[uniformName] = consumer
        }
    }
}

class ShaderHolder(
    val defaultUniformData: MutableMap<String, Consumer<Uniform>>,
    val uniformNames: Array<String>
)