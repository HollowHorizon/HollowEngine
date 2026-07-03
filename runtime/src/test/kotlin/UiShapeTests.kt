import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.shape.SvgFileParser
import ru.hollowhorizon.hollowengine.client.ui.shape.SvgPathParser
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathCommand
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathContour
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathGeometry
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathPoint
import ru.hollowhorizon.hollowengine.client.ui.shape.UiPathTriangle
import ru.hollowhorizon.hollowengine.client.ui.shape.UiShapeSize
import ru.hollowhorizon.hollowengine.client.ui.shape.UiSvgStrokeLineCap
import ru.hollowhorizon.hollowengine.client.ui.shape.UiSvgStrokeLineJoin
import ru.hollowhorizon.hollowengine.client.ui.shape.flatten
import ru.hollowhorizon.hollowengine.client.ui.shape.resolveSvgTextFont
import ru.hollowhorizon.hollowengine.client.ui.shape.svgPath
import ru.hollowhorizon.hollowengine.client.ui.shape.svgResource
import ru.hollowhorizon.hollowengine.client.ui.style.*
import java.awt.Font
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
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
        compileStyleModifier("shape", "svg(\"hollowengine:ui/shapes/hexagon.svg\")")!!.applyTo(style)
        compileStyleModifier("shape-fill", "radial-gradient(70% at 25% 25%, #000000, #FFFFFF)")!!.applyTo(style)
        compileStyleModifier("shape-stroke-width", "3px")!!.applyTo(style)

        assertEquals(true, style.clip)
        assertNotNull(style.clipShape)
        assertNotNull(style.shape)
        assertTrue(style.shapeFill is UiPaint.RadialGradient)
        assertEquals(UiLength.Px(3f), style.shapeStrokeWidth)
    }

    @Test
    fun `svg file parser extracts viewBox and path elements`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 -1 10 8" xmlns="http://www.w3.org/2000/svg">
                <g>
                    <path d="M 0 0 L 10 0 L 10 5 Z"/>
                </g>
            </svg>
            """.trimIndent()
        )

        assertEquals(UiRect(0f, -1f, 10f, 8f), document.viewBox)
        assertTrue(document.path.commands.first() is UiPathCommand.MoveTo)
        assertTrue(document.path.commands.last() is UiPathCommand.Close)
    }

    @Test
    fun `svg file parser converts text image and foreignObject to geometry`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 0 120 40" xmlns="http://www.w3.org/2000/svg">
                <text x="4" y="18" font-size="14" fill="#ffffff">NBT</text>
                <image x="50" y="4" width="20" height="12" href="icon.png"/>
                <foreignObject x="80" y="5" width="30" height="20"/>
            </svg>
            """.trimIndent()
        )

        assertTrue(document.path.commands.isNotEmpty())
        assertEquals(UiColor.White, document.elements.first().style.fillColor())
    }

    @Test
    fun `svg text uses serif fallback for unavailable concrete font`() {
        val font = resolveSvgTextFont("'Definitely Missing SVG Font'", 23f)

        assertEquals(Font.SERIF, font.family)
    }

    @Test
    fun `svg text infers monospaced fallback from missing mono font name`() {
        val font = resolveSvgTextFont("'JetBrains Mono'", 23f)

        assertEquals(Font.MONOSPACED, font.family)
    }

    @Test
    fun `svg text font family list uses next available fallback`() {
        val font = resolveSvgTextFont("'Definitely Missing SVG Font', monospace", 23f)

        assertEquals(Font.MONOSPACED, font.family)
    }

    @Test
    fun `svg file parser converts basic primitives to paths`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
                <rect x="2" y="2" width="8" height="6" rx="1"/>
                <circle cx="16" cy="6" r="3"/>
                <line x1="2" y1="14" x2="22" y2="14"/>
                <polygon points="4 18 12 16 20 18 12 22"/>
            </svg>
            """.trimIndent()
        )

        assertTrue(document.path.commands.any { it is UiPathCommand.ArcTo })
        assertTrue(document.path.commands.count { it is UiPathCommand.MoveTo } >= 4)
        assertTrue(document.path.commands.count { it is UiPathCommand.Close } >= 3)
    }

    @Test
    fun `svg file parser applies transform chains to paths primitives and groups`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 0 64 64" xmlns="http://www.w3.org/2000/svg">
                <g transform="translate(10 5) scale(2)">
                    <rect x="1" y="2" width="4" height="3"/>
                    <path transform="translate(4 0)" d="M 0 0 L 2 0"/>
                </g>
            </svg>
            """.trimIndent()
        )

        val bounds = document.path.bounds()

        assertNotNull(bounds)
        assertEquals(12f, bounds.x)
        assertEquals(5f, bounds.y)
        assertTrue(bounds.width >= 10f)
    }

    @Test
    fun `svg file parser resolves css colors and use symbols`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
                <defs>
                    <style>
                        .accent, #direct { fill: rgb(255, 128, 0); stroke-linecap: round; stroke-linejoin: round; }
                    </style>
                    <symbol id="mark" viewBox="0 0 10 10">
                        <path class="accent" d="M 1 1 L 9 1 L 9 9 Z"/>
                    </symbol>
                </defs>
                <use href="#mark" width="20" height="20"/>
            </svg>
            """.trimIndent()
        )

        val style = document.elements.single().style

        assertEquals(UiColor(1f, 128f / 255f, 0f, 1f), style.fillColor())
        assertEquals(UiSvgStrokeLineCap.ROUND, style.strokeLineCap)
        assertEquals(UiSvgStrokeLineJoin.ROUND, style.strokeLineJoin)
        assertTrue(document.path.bounds()!!.width > 15f)
    }

    @Test
    fun `svg file parser turns styled strokes into geometry`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 0 12 10" xmlns="http://www.w3.org/2000/svg">
                <path d="M 2 5 L 10 5" fill="none" stroke="#fff" stroke-width="4"
                      stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
            """.trimIndent()
        )

        val bounds = document.path.bounds()

        assertNotNull(bounds)
        assertTrue(bounds.x < 1f)
        assertTrue(bounds.width > 11f)
        assertEquals(UiSvgStrokeLineCap.ROUND, document.elements.single().style.strokeLineCap)
    }

    @Test
    fun `svg file parser keeps root and path paint colors`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 0 20 10" fill="#123456" xmlns="http://www.w3.org/2000/svg">
                <path d="M 0 0 L 10 0 L 10 10 L 0 10 Z"/>
                <path fill="#ff0000" d="M 10 0 L 20 0 L 20 10 L 10 10 Z"/>
            </svg>
            """.trimIndent()
        )

        assertEquals(UiColor(0x12 / 255f, 0x34 / 255f, 0x56 / 255f, 1f), document.elements[0].paint)
        assertEquals(UiColor(1f, 0f, 0f, 1f), document.elements[1].paint)
    }

    @Test
    fun `svg file parser keeps fill and stroke as separate painted geometry`() {
        val document = SvgFileParser.parse(
            """
            <svg viewBox="0 0 12 12" xmlns="http://www.w3.org/2000/svg">
                <path fill="#0000ff" stroke="#ff0000" stroke-width="2" d="M 2 2 L 10 2 L 10 10 L 2 10 Z"/>
            </svg>
            """.trimIndent()
        )

        assertEquals(listOf(UiColor(0f, 0f, 1f, 1f), UiColor(1f, 0f, 0f, 1f)), document.elements.map { it.paint })
    }

    @Test
    fun `svg file parser applies clipPath mask and filter geometry`() {
        val clipped = SvgFileParser.parse(
            """
            <svg viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
                <defs>
                    <clipPath id="clip"><rect x="0" y="0" width="8" height="20"/></clipPath>
                    <mask id="mask"><rect x="0" y="0" width="8" height="12"/></mask>
                </defs>
                <rect x="0" y="0" width="20" height="20" clip-path="url(#clip)" mask="url(#mask)"/>
            </svg>
            """.trimIndent()
        )
        val filtered = SvgFileParser.parse(
            """
            <svg viewBox="0 0 20 20" xmlns="http://www.w3.org/2000/svg">
                <defs><filter id="shadow"><feDropShadow dx="4" dy="0" stdDeviation="1"/></filter></defs>
                <rect x="2" y="2" width="4" height="4" filter="url(#shadow)"/>
            </svg>
            """.trimIndent()
        )

        assertEquals(8f, clipped.path.bounds()!!.width)
        assertEquals(12f, clipped.path.bounds()!!.height)
        assertTrue(filtered.path.bounds()!!.width > 8f)
    }

    @Test
    fun `path stroke mesh uses round caps and joins by default`() {
        val triangles = SvgPathParser.parse("M 0 0 L 10 0").flatten().strokeTriangles(4f)
        val xs = triangles.flatMap { listOf(it.first.x, it.second.x, it.third.x) }

        assertTrue(xs.minOrNull()!! < 0f)
        assertTrue(xs.maxOrNull()!! > 10f)
    }

    @Test
    fun `path stroke mesh avoids disk fan geometry for round caps`() {
        val triangles = SvgPathParser.parse("M 0 0 L 10 0").flatten().strokeTriangles(4f)
        val xs = triangles.flatMap { listOf(it.first.x, it.second.x, it.third.x) }

        assertTrue(xs.minOrNull()!! < 0f)
        assertTrue(xs.maxOrNull()!! > 10f)
        assertTrue(triangles.size < 34)
    }

    @Test
    fun `fill triangulation supports concave contours and holes`() {
        val concave = SvgPathParser.parse("M 0 0 L 10 0 L 10 10 L 5 5 L 0 10 Z").flatten().fillTriangles()
        val holed = SvgPathParser.parse(
            "M 0 0 L 10 0 L 10 10 L 0 10 Z M 3 3 L 7 3 L 7 7 L 3 7 Z"
        ).flatten().fillTriangles()

        assertEquals(75f, concave.sumArea(), 0.001f)
        assertEquals(84f, holed.sumArea(), 0.001f)
    }

    @Test
    fun `fill triangulation removes collinear points without hanging`() {
        val geometry = UiPathGeometry(
            listOf(
                UiPathContour(
                    points = listOf(
                        UiPathPoint(0f, 0f),
                        UiPathPoint(5f, 0f),
                        UiPathPoint(10f, 0f),
                        UiPathPoint(10f, 10f),
                        UiPathPoint(0f, 10f),
                        UiPathPoint(0f, 0f),
                    ),
                    closed = true,
                ),
            ),
        )

        val triangles = geometry.fillTriangles()

        assertEquals(100f, triangles.sumArea(), 0.001f)
    }

    @Test
    fun `svg resource shape loads path asset`() {
        val shape = svgResource("hollowengine:ui/shapes/hexagon.svg")
        val path = shape.createPath(UiShapeSize(120f, 80f))

        val bounds = path.bounds()

        assertNotNull(bounds)
        assertTrue(bounds.width > 80f)
        assertTrue(bounds.height > 60f)
    }

    @Test
    fun `existing geometric hollowengine svg assets load as shapes`() {
        val root = Path.of("src/main/resources/assets/hollowengine")
        val locations = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".svg") }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .map { ResourceLocation.parse("hollowengine:$it") }
                .toList()
        }
        val unsupported = mutableMapOf<ResourceLocation, Throwable>()

        assertTrue(locations.any { it.path == "textures/gui/logo/logo.svg" })
        locations.forEach { location ->
            val result = runCatching {
                svgResource(location).createPath(UiShapeSize(64f, 64f))
            }
            val path = result.getOrElse {
                unsupported[location] = it
                return@forEach
            }

            assertTrue(path.commands.isNotEmpty(), "Expected $location to produce path commands")
        }
        assertTrue(unsupported.isEmpty(), unsupported.toString())
    }

    @Test
    fun `existing geometric hollowengine svg assets triangulate without hanging`() {
        val root = Path.of("src/main/resources/assets/hollowengine")
        val locations = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".svg") }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .map { ResourceLocation.parse("hollowengine:$it") }
                .toList()
        }
        val failed = mutableMapOf<ResourceLocation, Throwable>()

        locations.forEach { location ->
            runCatching {
                val triangles = svgResource(location)
                    .createPath(UiShapeSize(64f, 64f))
                    .flatten()
                    .fillTriangles()
                assertTrue(triangles.size < 50_000, "Expected $location to stay below 50000 triangles, got ${triangles.size}")
            }.onFailure { failed[location] = it }
        }

        assertTrue(failed.isEmpty(), failed.toString())
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

private fun List<UiPathTriangle>.sumArea(): Float {
    return sumOf { triangle ->
        abs(
            ((triangle.second.x - triangle.first.x) * (triangle.third.y - triangle.first.y) -
                    (triangle.second.y - triangle.first.y) * (triangle.third.x - triangle.first.x)).toDouble()
        ) * 0.5
    }.toFloat()
}
