package ru.hollowhorizon.hollowengine.client.particles

import ru.hollowhorizon.hollowengine.common.utils.math.MutableVec3f
import ru.hollowhorizon.hollowengine.common.utils.math.QuatF
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f


interface Transform {
    val parent: Transform?
    val isValid: Boolean
    val position: Vec3f
    val rotation: QuatF
    val velocity: Vec3f

    object Zero : Transform {
        override val parent: Transform? get() = null
        override val isValid: Boolean get() = true
        override val position: Vec3f get() = MutableVec3f()
        override val rotation: QuatF get() = QuatF.IDENTITY
        override val velocity: Vec3f get() = MutableVec3f()
    }

    companion object {
        fun create(pos: Vec3f = Vec3f.ZERO, rotation: QuatF = QuatF.IDENTITY): Transform =
            object : Transform {
                override val parent: Transform? get() = null
                override val isValid: Boolean get() = true
                override val position: Vec3f get() = pos
                override val rotation: QuatF get() = rotation
                override val velocity: Vec3f get() = Vec3f.ZERO
            }
    }
}