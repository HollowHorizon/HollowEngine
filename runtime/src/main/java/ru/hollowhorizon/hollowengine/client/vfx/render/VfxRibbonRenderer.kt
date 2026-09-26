package ru.hollowhorizon.hollowengine.client.vfx.render

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import org.lwjgl.opengl.GL33
import ru.hollowhorizon.hollowengine.common.registry.ModShaders
import ru.hollowhorizon.hollowengine.common.vfx.VfxBlend
import kotlin.math.sqrt

/**
 * Draws trails and beams: strips of points turned into quads that face the camera.
 *
 * Ribbons are built on the CPU every frame in the vanilla particle format, drawn by the engine
 * ribbon program or by the shader the material names.
 */
object VfxRibbonRenderer {
    private val side = FloatArray(3)

    /**
     * Draws [draws] as seen from [view]; with [glow], only the glow of the ones that have one, which
     * builds their strips again, since the pass comes after the frame has moved on to other surfaces.
     */
    fun render(draws: List<VfxRibbonDraw>, view: VfxView, glow: Boolean = false) {
        if (draws.isEmpty()) return
        val engine = ModShaders.VFX_RIBBON ?: return

        val ordered = draws.filter { it.material.blend != VfxBlend.BLEND } +
                draws.filter { it.material.blend == VfxBlend.BLEND }.sortedByDescending { depth(it, view) }

        ordered.forEach { draw ->
            val material = draw.material
            if (glow && material.glow <= 0f) return@forEach
            val shader = if (glow) {
                VfxMaterialStates.glowShader(material.shader, engine, DefaultVertexFormat.PARTICLE) ?: return@forEach
            } else {
                material.shader?.let { VfxShaders.get(it, DefaultVertexFormat.PARTICLE) } ?: engine
            }
            val texture = VfxMaterialStates.texture(material.texture)

            val builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE)
            for (strip in 0 until draw.stripCount) {
                quads(builder, draw, draw.strips[strip * 2], draw.strips[strip * 2 + 1], view)
            }
            val mesh = builder.build() ?: return@forEach

            if (draw.repeat) {
                RenderSystem.bindTexture(texture)
                RenderSystem.texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_S, GL33.GL_REPEAT)
                RenderSystem.texParameter(GL33.GL_TEXTURE_2D, GL33.GL_TEXTURE_WRAP_T, GL33.GL_REPEAT)
            }
            val key = VfxBatchKey.of(material, mixed = false, owner = draw)
            if (glow) VfxMaterialStates.applyGlow(key) else VfxMaterialStates.apply(key, premultiplied = false)
            view.drawImmediate(mesh, shader) { bound ->
                VfxMaterialStates.bindCommonSamplers(bound, texture)
                bound.safeGetUniform("BlendMode").set(VfxQuadPacker.blendMode(material.blend))
                bound.safeGetUniform("Softness").set(material.softness)
                bound.safeGetUniform("Glow").set(material.glow)
                bound.safeGetUniform("GlowPass").set(if (glow) 1f else 0f)
                if (bound !== engine) draw.uniforms?.apply(bound)
            }
        }
        VfxMaterialStates.restore()
    }

    private fun quads(builder: BufferBuilder, draw: VfxRibbonDraw, first: Int, count: Int, view: VfxView) {
        val points = draw.points
        val region = draw.material.uv
        val stride = VfxRibbonDraw.STRIDE
        for (index in first until first + count - 1) {
            val at = index * stride
            val next = at + stride
            sideOf(points, index, first, count, view)
            val ax = side[0]
            val ay = side[1]
            val az = side[2]
            sideOf(points, index + 1, first, count, view)

            val v0 = if (draw.repeat) points[at + 8] else region.v0 + points[at + 8] * region.height
            val v1 = if (draw.repeat) points[next + 8] else region.v0 + points[next + 8] * region.height
            vertex(builder, points, at, -ax, -ay, -az, region.u0, v0)
            vertex(builder, points, at, ax, ay, az, region.u1, v0)
            vertex(builder, points, next, side[0], side[1], side[2], region.u1, v1)
            vertex(builder, points, next, -side[0], -side[1], -side[2], region.u0, v1)
        }
    }

    /** The half-width vector at a point: across the ribbon and across the line to the eye. */
    private fun sideOf(points: FloatArray, index: Int, first: Int, count: Int, view: VfxView) {
        val stride = VfxRibbonDraw.STRIDE
        val before = (index - 1).coerceAtLeast(first) * stride
        val after = (index + 1).coerceAtMost(first + count - 1) * stride
        val tx = points[after] - points[before]
        val ty = points[after + 1] - points[before + 1]
        val tz = points[after + 2] - points[before + 2]

        val at = index * stride
        val ex = view.eye.x - points[at]
        val ey = view.eye.y - points[at + 1]
        val ez = view.eye.z - points[at + 2]

        var sx = ty * ez - tz * ey
        var sy = tz * ex - tx * ez
        var sz = tx * ey - ty * ex
        val length = sqrt(sx * sx + sy * sy + sz * sz)
        val half = points[at + 3]
        if (length < 1.0e-6f) {
            sx = view.right.x; sy = view.right.y; sz = view.right.z
        } else {
            sx /= length; sy /= length; sz /= length
        }
        side[0] = sx * half
        side[1] = sy * half
        side[2] = sz * half
    }

    private fun vertex(
        builder: BufferBuilder,
        points: FloatArray,
        at: Int,
        dx: Float,
        dy: Float,
        dz: Float,
        u: Float,
        v: Float,
    ) {
        builder.addVertex(points[at] + dx, points[at + 1] + dy, points[at + 2] + dz)
            .setUv(u, v)
            .setColor(points[at + 4], points[at + 5], points[at + 6], points[at + 7])
            .setLight(points[at + 9].toRawBits())
    }

    private fun depth(draw: VfxRibbonDraw, view: VfxView): Float {
        val points = draw.points
        if (points.isEmpty()) return 0f
        val dx = points[0] - view.eye.x
        val dy = points[1] - view.eye.y
        val dz = points[2] - view.eye.z
        return dx * dx + dy * dy + dz * dz
    }
}
