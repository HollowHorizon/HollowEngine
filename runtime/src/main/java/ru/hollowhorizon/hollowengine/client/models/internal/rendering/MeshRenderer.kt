package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import ru.hollowhorizon.hollowengine.client.models.internal.v2.PrimitiveInstance

interface MeshRenderer {
    fun init()
    fun setupPipeline(
        pipeline: RenderPipeline,
        instance: PrimitiveInstance,
    )
    fun destroy()
}
