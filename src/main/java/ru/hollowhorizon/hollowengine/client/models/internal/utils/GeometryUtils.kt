package ru.hollowhorizon.hollowengine.client.models.internal.utils

import de.fabmax.kool.math.*
import kotlin.math.abs

object GeometryUtils {
    fun recalculateNormals(indices: IntArray?, positions: Array<Vec3f>): Array<Vec3f> {
        val count = positions.size
        val accumNormals = Array(count) { MutableVec3f() }
        val getIndex = { i: Int -> indices?.get(i) ?: i }
        val loops = indices?.size ?: count

        val p1 = MutableVec3f(); val p2 = MutableVec3f(); val p3 = MutableVec3f()
        val e1 = MutableVec3f(); val e2 = MutableVec3f(); val nrm = MutableVec3f()

        for (i in 0 until loops step 3) {
            val i1 = getIndex(i); val i2 = getIndex(i + 1); val i3 = getIndex(i + 2)
            if (i1 >= count || i2 >= count || i3 >= count) continue

            p1.set(positions[i1]); p2.set(positions[i2]); p3.set(positions[i3])
            p2.subtract(p1, e1).norm()
            p3.subtract(p1, e2).norm()
            e1.cross(e2, nrm).norm()

            // Area weighted normal
            p2.subtract(p1, e1); p3.subtract(p1, e2)
            val area = 0.5f * e1.cross(e2, MutableVec3f()).length()
            nrm.mul(area)

            if (!nrm.x.isNaN() && !nrm.y.isNaN() && !nrm.z.isNaN()) {
                accumNormals[i1].add(nrm); accumNormals[i2].add(nrm); accumNormals[i3].add(nrm)
            }
        }

        return Array(count) { i ->
            val n = accumNormals[i]
            if (n.sqrLength() > 1e-6f) n.norm() else n.set(0f, 1f, 0f)
            Vec3f(n.x, n.y, n.z)
        }
    }

    fun recalculateTangents(indices: IntArray?, positions: Array<Vec3f>, uvs: Array<Vec2f>, normals: Array<Vec3f>): Array<Vec4f> {
        val count = positions.size
        val accumTangents = Array(count) { MutableVec3f() }
        val getIndex = { i: Int -> indices?.get(i) ?: i }
        val loops = indices?.size ?: count

        val p1 = MutableVec3f(); val p2 = MutableVec3f(); val p3 = MutableVec3f()
        val uv1 = MutableVec2f(); val uv2 = MutableVec2f(); val uv3 = MutableVec2f()
        val e1 = MutableVec3f(); val e2 = MutableVec3f(); val tan = MutableVec3f()

        for (i in 0 until loops step 3) {
            val i1 = getIndex(i); val i2 = getIndex(i + 1); val i3 = getIndex(i + 2)
            if (i1 >= count || i2 >= count || i3 >= count) continue

            p1.set(positions[i1]); p2.set(positions[i2]); p3.set(positions[i3])
            uv1.set(uvs[i1]); uv2.set(uvs[i2]); uv3.set(uvs[i3])

            p2.subtract(p1, e1).norm()
            p3.subtract(p1, e2).norm()

            val du1 = uv2.x - uv1.x; val dv1 = uv2.y - uv1.y
            val du2 = uv3.x - uv1.x; val dv2 = uv3.y - uv1.y
            val det = du1 * dv2 - du2 * dv1

            if (abs(det) < 1e-6f) continue
            val f = 1f / det

            if (!f.isNaN()) {
                tan.x = f * (dv2 * e1.x - dv1 * e2.x)
                tan.y = f * (dv2 * e1.y - dv1 * e2.y)
                tan.z = f * (dv2 * e1.z - dv1 * e2.z)
                accumTangents[i1].add(tan); accumTangents[i2].add(tan); accumTangents[i3].add(tan)
            }
        }

        return Array(count) { i ->
            val t = accumTangents[i]
            if (t.sqrLength() > 1e-6f) t.norm() else t.set(1f, 0f, 0f)
            Vec4f(t.x, t.y, t.z, 1f)
        }
    }

    fun recalculateMidCoords(indices: IntArray?, uvs: Array<Vec2f>): Array<Vec2f> {
        val count = uvs.size
        val accumMid = Array(count) { MutableVec2f() }
        val contributions = IntArray(count)
        val getIndex = { i: Int -> indices?.get(i) ?: i }
        val loops = indices?.size ?: count

        for (i in 0 until loops step 3) {
            val i1 = getIndex(i)
            val i2 = getIndex(i + 1)
            val i3 = getIndex(i + 2)
            if (i1 >= count || i2 >= count || i3 >= count) continue

            val midU = (uvs[i1].x + uvs[i2].x + uvs[i3].x) / 3f
            val midV = (uvs[i1].y + uvs[i2].y + uvs[i3].y) / 3f

            accumMid[i1].x += midU
            accumMid[i1].y += midV
            accumMid[i2].x += midU
            accumMid[i2].y += midV
            accumMid[i3].x += midU
            accumMid[i3].y += midV
            contributions[i1]++
            contributions[i2]++
            contributions[i3]++
        }

        return Array(count) { i ->
            val mid = accumMid[i]
            val factor = contributions[i].takeIf { it > 0 }?.toFloat() ?: 1f
            Vec2f(mid.x / factor, mid.y / factor)
        }
    }
}
