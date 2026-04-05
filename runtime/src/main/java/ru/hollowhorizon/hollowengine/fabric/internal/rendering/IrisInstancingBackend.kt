package ru.hollowhorizon.hollowengine.fabric.internal.rendering

import ru.hollowhorizon.hollowengine.client.models.internal.SHADER
import ru.hollowhorizon.hollowengine.client.models.internal.drawWithShader
import ru.hollowhorizon.hollowengine.client.models.internal.opaqueShaderState
import ru.hollowhorizon.hollowengine.client.models.internal.translucentShaderState
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.InstancedShaderLayoutMode
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.ModelInstancingBackend
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.PipelineRenderer
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.SubmittedInstance
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.fallbackToCapturedDraws
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.renderOpaque
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.renderTranslucent
import ru.hollowhorizon.hollowengine.client.models.internal.rendering.withInstancingRenderState
import ru.hollowhorizon.hollowengine.fabric.internal.IrisHelper

object IrisInstancingBackend : ModelInstancingBackend {
    override fun canBatch(): Boolean = IrisHelper.currentPipeline() != null

    override fun flush(batches: Map<PipelineRenderer, List<SubmittedInstance>>) {
        if (batches.isEmpty()) return

        withInstancingRenderState {
            renderOpaque(batches, SHADER) { renderer, instances, _ ->
                renderRenderer(renderer, instances)
            }
            renderTranslucent(batches, SHADER) { renderer, instances, _ ->
                renderRenderer(renderer, instances)
            }
        }
    }

    private fun renderRenderer(renderer: PipelineRenderer, instances: List<SubmittedInstance>) {
        if (!renderer.shouldUseInstancing(instances.size)) {
            drawWithShader(SHADER, if (renderer.isTranslucent) translucentShaderState() else opaqueShaderState()) {
                fallbackToCapturedDraws(renderer, instances)
            }
            return
        }

        val shader = IrisInstancingPrograms.shaderFor(
            translucent = renderer.isTranslucent,
            shadow = IrisHelper.isShadowRendering()
        )

        if (shader != null) {
            drawWithShader(shader, if (renderer.isTranslucent) translucentShaderState() else opaqueShaderState()) {
                renderer.renderInstanced(instances, shader, InstancedShaderLayoutMode.RUNTIME)
            }
        } else {
            drawWithShader(SHADER, if (renderer.isTranslucent) translucentShaderState() else opaqueShaderState()) {
                fallbackToCapturedDraws(renderer, instances)
            }
        }
    }
}
