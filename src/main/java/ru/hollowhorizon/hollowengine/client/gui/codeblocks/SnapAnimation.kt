package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.PI_F
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.AnimatedFloat
import net.minecraft.util.Mth.cos
import net.minecraft.util.Mth.sin

class SnapAnimation(val x: Float, val y: Float) {
    val animator = AnimatedFloat(0.4f)

    init {
        animator.start()
    }

    val isFinished: Boolean
        get() = animator.value >= 1f
}

object RingGeometry {
    val vertices = mutableListOf<Vec3f>()
    val indices = mutableListOf<Int>()

    init {
        val segments = 64
        val rInner = 0.7f
        val rOuter = 1.0f

        for (i in 0..segments) {
            val a = (i.toFloat() / segments) * 2f * PI_F
            val c = cos(a)
            val s = sin(a)

            vertices.add(Vec3f(c * rInner, s * rInner, 0f))
            vertices.add(Vec3f(c * rOuter, s * rOuter, 0f))

            if (i < segments) {
                val start = i * 2
                // Первый треугольник
                indices.add(start)
                indices.add(start + 1)
                indices.add(start + 2)

                // Второй треугольник
                indices.add(start + 2)
                indices.add(start + 1)
                indices.add(start + 3)
            }
        }
    }
}