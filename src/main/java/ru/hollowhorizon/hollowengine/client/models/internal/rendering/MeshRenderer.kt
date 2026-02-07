package ru.hollowhorizon.hollowengine.client.models.internal.rendering

import ru.hollowhorizon.hollowengine.client.models.internal.MatrixGetter
import ru.hollowhorizon.hollowengine.client.models.internal.SkinGetter
import ru.hollowhorizon.hollowengine.client.models.internal.VisibilityGetter

interface MeshRenderer {
    fun init()
    fun setupPipeline(
        pipeline: RenderPipeline,
        skinGetter: SkinGetter,
        matrixGetter: MatrixGetter,
        visibilityGetter: VisibilityGetter
    )
    fun destroy()
}