package ru.hollowhorizon.hollowengine.client.models.bedrock

import ru.hollowhorizon.hollowengine.common.utils.math.MutableQuatF
import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.utils.math.deg

/** Bedrock positions reflect across X; rotations use Z-Y-X order with negated X/Y angles. */
internal object BedrockCoordinates {
    fun position(value: Vec3f): Vec3f = Vec3f(-value.x, value.y, value.z)

    fun rotation(value: Vec3f): QuatF = MutableQuatF()
        .rotate(value.z.deg, Vec3f.Z_AXIS)
        .rotate(-value.y.deg, Vec3f.Y_AXIS)
        .rotate(-value.x.deg, Vec3f.X_AXIS)
}
