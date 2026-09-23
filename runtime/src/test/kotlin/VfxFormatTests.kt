import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.common.utils.math.Vec3f
import ru.hollowhorizon.hollowengine.common.vfx.VfxAppearance
import ru.hollowhorizon.hollowengine.common.vfx.VfxBlend
import ru.hollowhorizon.hollowengine.common.vfx.VfxColorValue
import ru.hollowhorizon.hollowengine.common.vfx.VfxCurve
import ru.hollowhorizon.hollowengine.common.vfx.VfxEffect
import ru.hollowhorizon.hollowengine.common.vfx.VfxEmission
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxForceKind
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxForceSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxFormat
import ru.hollowhorizon.hollowengine.common.vfx.VfxGradient
import ru.hollowhorizon.hollowengine.common.vfx.VfxGradientStop
import ru.hollowhorizon.hollowengine.common.vfx.VfxKey
import ru.hollowhorizon.hollowengine.common.vfx.VfxMaterialSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxMeshEmitterSpec
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxNoiseSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxQuadEmitterSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxRgba
import ru.hollowhorizon.hollowengine.common.vfx.VfxShape
import ru.hollowhorizon.hollowengine.common.vfx.VfxShapeKind
import ru.hollowhorizon.hollowengine.common.vfx.VfxSpawn
import ru.hollowhorizon.hollowengine.common.vfx.VfxTimelineSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxTrack
import ru.hollowhorizon.hollowengine.common.vfx.VfxTrackCurve
import ru.hollowhorizon.hollowengine.common.vfx.VfxTransform
import ru.hollowhorizon.hollowengine.common.vfx.modules.VfxUvAnimationSpec
import ru.hollowhorizon.hollowengine.common.vfx.VfxUvRect
import ru.hollowhorizon.hollowengine.common.vfx.VfxValue
import ru.hollowhorizon.hollowengine.common.vfx.VfxVec3Value
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The `.vfx` format has to survive a round trip.
 *
 * Effects are written by an editor that rewrites the whole file, so anything the reader drops is
 * work silently thrown away. That makes the round trip the one test worth keeping here: it covers
 * the polymorphic node and module registries, the sealed value shapes and the timeline at once.
 */
class VfxFormatTests {
    @Test
    fun `an effect survives a round trip`() {
        val effect = sample()

        val text = VfxFormat.write(effect)
        val read = VfxFormat.read(text)

        assertEquals(effect, read)
    }

    @Test
    fun `a node keeps its modules and their kinds`() {
        val read = VfxFormat.read(VfxFormat.write(sample()))
        val emitter = assertIs<VfxQuadEmitterSpec>(read.nodes.first())

        assertEquals(3, emitter.modules.size)
        val swirl = assertIs<VfxForceSpec>(emitter.modules[0])
        assertIs<VfxForceSpec>(emitter.modules[1])
        assertIs<VfxUvAnimationSpec>(emitter.modules[2])
        assertEquals(VfxForceKind.VORTEX, swirl.kind)
    }

    @Test
    fun `a child node survives its parent`() {
        val read = VfxFormat.read(VfxFormat.write(sample()))
        val child = read.nodes.first().children.single()

        assertIs<VfxMeshEmitterSpec>(child)
        assertEquals("sparks", child.id)
    }

    @Test
    fun `an empty file reads as an empty effect`() {
        assertEquals(VfxEffect.EMPTY, VfxFormat.read(""))
    }

    @Test
    fun `the shipped example reads`() {
        val text = javaClass.classLoader.getResource(EXAMPLE_PATH)?.readText()
            ?: error("The example effect is missing from the resources: $EXAMPLE_PATH")

        val effect = VfxFormat.read(text)

        assertTrue(effect.nodes.isNotEmpty(), "The example effect has no nodes")
        assertTrue(effect.expressions().isEmpty() || effect.expressions().all { it.isNotBlank() })
        assertEquals(text.trim(), VfxFormat.write(effect).trim(), "The example is not in canonical form")
    }

    private fun sample() = VfxEffect(
        nodes = listOf(
            VfxQuadEmitterSpec(
                id = "flame",
                name = "Flame",
                transform = VfxTransform(position = Vec3f(0f, 0.25f, 0f)),
                emission = VfxEmission(rate = VfxValue.Const(40f), burst = 3, duration = 0f),
                shape = VfxShape(
                    kind = VfxShapeKind.CONE,
                    radius = VfxValue.Expr("0.1 + e.age * 0.05"),
                    angle = VfxValue.Const(12f),
                ),
                spawn = VfxSpawn(
                    lifetime = VfxValue.Range(0.4f, 0.9f),
                    speed = VfxValue.Range(0.5f, 1.2f),
                ),
                appearance = VfxAppearance(
                    size = VfxVec3Value(
                        VfxValue.OverTime(VfxCurve(listOf(VfxKey(0f, 0.2f), VfxKey(1f, 0f)))),
                        VfxValue.ONE,
                        VfxValue.ONE,
                    ),
                    color = VfxColorValue.Gradient(
                        VfxGradient(
                            listOf(
                                VfxGradientStop(0f, VfxRgba(1f, 1f, 1f, 1f)),
                                VfxGradientStop(1f, VfxRgba(1f, 0.2f, 0f, 0f)),
                            )
                        )
                    ),
                ),
                material = VfxMaterialSpec(blend = VfxBlend.ADDITIVE, uv = VfxUvRect(0f, 0f, 0.5f, 0.5f)),
                modules = listOf(
                    VfxForceSpec(id = "swirl", kind = VfxForceKind.VORTEX, strength = VfxValue.Expr("p.progress * 2")),
                    VfxForceSpec(id = "wind", direction = Vec3f(1f, 0f, 0f)),
                    VfxUvAnimationSpec(columns = 2, rows = 2),
                ),
                children = listOf(VfxMeshEmitterSpec(id = "sparks", name = "Sparks")),
            ),
            VfxQuadEmitterSpec(
                id = "smoke",
                name = "Smoke",
                modules = listOf(VfxNoiseSpec(strength = VfxValue.Const(0.5f))),
            ),
        ),
        timeline = VfxTimelineSpec(
            duration = 3f,
            tracks = listOf(
                VfxTrack(
                    node = "smoke",
                    property = "enabled",
                    curves = listOf(VfxTrackCurve(keys = listOf(VfxKey(0f, 0f), VfxKey(1f, 1f)))),
                ),
                VfxTrack(
                    node = "flame",
                    property = "modules.swirl.strength",
                    curves = listOf(VfxTrackCurve(keys = listOf(VfxKey(0f, 0f), VfxKey(2f, 3f)))),
                ),
            ),
        ),
    )

    private companion object {
        const val EXAMPLE_PATH = "assets/hollowengine/vfx/example.vfx"
    }
}
