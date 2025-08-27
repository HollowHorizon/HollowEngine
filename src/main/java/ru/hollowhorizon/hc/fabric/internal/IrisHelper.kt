package ru.hollowhorizon.hc.fabric.internal

import ru.hollowhorizon.hc.common.utils.ModList

import net.irisshaders.iris.Iris
import net.irisshaders.iris.api.v0.IrisApi
import net.irisshaders.iris.pipeline.ShaderRenderingPipeline

object IrisHelper {
    @JvmStatic
    fun shouldOverrideShaders() =
        (Iris.getPipelineManager().pipelineNullable as? ShaderRenderingPipeline)?.shouldOverrideShaders() == true

    val hasIris = ModList.isLoaded("iris") || ModList.isLoaded("oculus")

    fun isShadowRendering() = (hasIris && IrisApi.getInstance().isRenderingShadowPass)
}