package ru.hollowhorizon.hc.client.kool.support

import de.fabmax.kool.pipeline.Attribute
import de.fabmax.kool.pipeline.GpuType

val MC_POSITION = Attribute("Position", GpuType.Float3)
val MC_COLOR = Attribute("Color", GpuType.Float4)
val MC_UV_0 = Attribute("UV0", GpuType.Float2)
val MC_UV_1 = Attribute("UV1", GpuType.Int2)
val MC_UV_2 = Attribute("UV2", GpuType.Int2)
val MC_NORMAL = Attribute("Normal", GpuType.Float3)
