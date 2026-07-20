package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.math.Vec3f

class FloatPropertyDriver(
    val name: String = "Value",
    val onApply: (Float) -> Unit,
) : PropertyDriver<Float> {

    override fun interpolate(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    override fun apply(value: Float) {
        onApply(value)
    }
}

class Vec2PropertyDriver(
    val onApply: (Vec2f) -> Unit,
) : PropertyDriver<Vec2f> {

    override fun interpolate(start: Vec2f, end: Vec2f, fraction: Float): Vec2f {
        return Vec2f(
            start.x + (end.x - start.x) * fraction,
            start.y + (end.y - start.y) * fraction
        )
    }

    override fun apply(value: Vec2f) {
        onApply(value)
    }
}

class Vec3PropertyDriver(
    val name: String = "Vector",
    val onApply: (Vec3f) -> Unit,
) : PropertyDriver<Vec3f> {

    override fun interpolate(start: Vec3f, end: Vec3f, fraction: Float): Vec3f {
        return start.mix(end, fraction, MutableVec3f())
    }

    override fun apply(value: Vec3f) {
        onApply(value)
    }
}
