import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.hss.compileStyleModifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiShapeTests {
    @Test
    fun `svg path parser supports lines curves smooth commands arcs and close`() {
        val path = SvgPathParser.parse(
            "M 10 10 l 20 0 H 40 v 20 C 45 35 50 40 55 45 " +
                    "s 10 10 15 0 Q 80 30 90 40 t 20 10 A 12 8 30 0 1 130 65 z"
        )
        val commands = path.commands

        assertTrue(commands[0] is UiPathCommand.MoveTo)
        assertEquals(UiPathPoint(30f, 10f), (commands[1] as UiPathCommand.LineTo).target)
        assertEquals(UiPathPoint(40f, 10f), (commands[2] as UiPathCommand.LineTo).target)
        assertEquals(UiPathPoint(40f, 30f), (commands[3] as UiPathCommand.LineTo).target)
        assertTrue(commands.any { it is UiPathCommand.CubicTo })
        assertTrue(commands.any { it is UiPathCommand.QuadraticTo })
        assertTrue(commands.any { it is UiPathCommand.ArcTo })
        assertTrue(commands.last() is UiPathCommand.Close)
    }

    @Test
    fun `elliptical arc is flattened to drawable contour`() {
        val path = SvgPathParser.parse("M 0 0 A 30 20 0 0 1 60 0")

        val contour = path.flatten().contours.single()

        assertTrue(contour.points.size > 2)
        assertEquals(UiPathPoint(60f, 0f), contour.points.last())
    }

    @Test
    fun `hss compiles path clip shape and radial gradient fill`() {
        val style = MutableUiStyle()

        compileStyleModifier("clip", "path(\"M 0 0 L 100 0 L 100 100 L 0 100 Z\", 100 100)")!!.applyTo(style)
        compileStyleModifier("shape", "path(\"M 0 0 L 100 50 L 0 100 Z\", 100 100)")!!.applyTo(style)
        compileStyleModifier("shape-fill", "radial-gradient(70% at 25% 25%, #000000, #FFFFFF)")!!.applyTo(style)
        compileStyleModifier("shape-stroke-width", "3px")!!.applyTo(style)

        assertEquals(true, style.clip)
        assertNotNull(style.clipShape)
        assertNotNull(style.shape)
        assertTrue(style.shapeFill is UiPaint.RadialGradient)
        assertEquals(UiLength.Px(3f), style.shapeStrokeWidth)
    }

    @Test
    fun `shape and shape clip emit render commands`() {
        val shape = svgPath("M 0 0 L 100 0 L 100 100 L 0 100 Z", UiRect(0f, 0f, 100f, 100f))

        HollowUiSurface().use { runtime ->
            val frame = runtime.frame(
                content = {
                    Box(
                        id = "shape",
                        modifier = Modifier.then(
                            Modifier.size(100.px, 100.px),
                            Modifier.shape(shape, UiPaint.Color(UiColor.White)),
                        ),
                    )
                    Box(
                        id = "clipper",
                        modifier = Modifier.then(
                            Modifier.size(100.px, 100.px),
                            Modifier.clip(shape),
                            Modifier.background(UiColor(1f, 0f, 0f, 1f)),
                        ),
                    )
                },
                width = 140f,
                height = 120f,
            )

            val shapeNode = frame.resolved.styles.keys.single { it.id == "shape" }
            val clipNode = frame.resolved.styles.keys.single { it.id == "clipper" }
            val drawShape = frame.commands.filterIsInstance<DrawShapeCommand>().single { it.node == shapeNode }
            val layer = frame.commands.filterIsInstance<BeginLayerCommand>().single { it.node == clipNode }

            assertEquals(UiRenderPhase.BACKGROUND, drawShape.phase)
            assertNotNull(layer.clipShape)
        }
    }
}
