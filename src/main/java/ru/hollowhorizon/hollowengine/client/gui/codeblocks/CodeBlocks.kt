package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import de.fabmax.kool.util.PolyUtil
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import kotlin.math.pow


fun UiScope.BlockLabel(text: String) {
    Text(text) {
        modifier
            .textColor(Color.WHITE)
            .font(sizes.normalText.derive(14f))
            .alignY(AlignmentY.Center)
    }
}

fun UiScope.BlockInput(
    value: String,
    onValueChange: (String) -> Unit,
    body: UiModifier.() -> Unit = {}
) {
    TextField(value) {
        body(modifier)
        this.modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .width(Grow(1f, min=30.dp, max=FitContent))
            .background(RoundRectBackground(Color.WHITE, sizes.gap))
            .alignY(AlignmentY.Center)
            .textAlignX(AlignmentX.Center)
            .onChange { onValueChange(it) }
    }
}

fun UiScope.ScratchBlock(
    color: Color,
    hasTopNotch: Boolean = true,
    hasBottomNotch: Boolean = true,
    blockContent: UiScope.() -> Unit
) {
    Panel {
        val isHovered by modifier.hoverable()
        val color by animateColorAsState(if(isHovered) color else color.mulRgb(0.8f), tween(0.2f, Easing.quadRev))
        modifier.onHover {
            PointerInput.cursorShape = CursorShape.HAND
        }

        modifier
            .layout(RowLayout)
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + if (hasBottomNotch) 4.dp else 0.dp)
            .background(ScratchBlockBackground(color, hasTopNotch, hasBottomNotch))

        blockContent()
    }
}

class CodeBlocksPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.codeblocks", dock) {
    override val icon: String
        get() = "hollowengine:textures/gui/icons/console.svg"

    override fun UiScope.compose() {
        val steps = remember { mutableStateOf("10") }
        val degrees = remember { mutableStateOf("15") }

        Panel {
            modifier
                .width(FitContent)
                .background(RectBackground(Color("1F2229"))) // Темный фон
                .layout(ColumnLayout)
                .padding(20.dp)

            ScratchBlock(
                color = MdColor.AMBER,
                hasTopNotch = false,
                hasBottomNotch = true
            ) {
                BlockLabel("when")
                BlockLabel("clicked")
            }

            Panel { modifier.height(4.dp) }

            ScratchBlock(color = MdColor.BLUE) {
                BlockLabel("move")
                BlockInput(steps.use(), { steps.set(it) })
                BlockLabel("steps")
            }

            Panel { modifier.height(4.dp) }

            ScratchBlock(color = MdColor.BLUE) {
                BlockLabel("turn right")
                BlockInput(degrees.use(), { degrees.set(it) })
                BlockLabel("degrees")
            }

            Panel { modifier.height(4.dp) }

            ScratchBlock(
                color = MdColor.PURPLE,
                hasBottomNotch = false
            ) {
                BlockLabel("play sound")
                BlockLabel("Meow")
            }
        }
    }

}

class ScratchBlockBackground(
    val color: Color,
    val hasTopNotch: Boolean,
    val hasBottomNotch: Boolean
) : UiRenderer<UiNode> {

    private val notchWidth = 40f
    private val notchHeight = 10f
    private val notchStartOffset = 30f
    private val cornerRadius = 10f

    override fun renderUi(node: UiNode) {
        with(node) {
            val w = node.widthPx
            val h = node.heightPx
            val x = 0f //node.leftPx
            val y = 0f //node.topPx

            val points = mutableListOf<Vec3f>()

            addBezier(points, x, y + cornerRadius, x, y, x + cornerRadius, y)

            if (hasTopNotch) {
                points.add(Vec3f(x + notchStartOffset, y, 0f))
                points.add(Vec3f(x + notchStartOffset + 5f, y + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset + notchWidth - 5f, y + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset + notchWidth, y, 0f))
            }

            addBezier(points, x + w - cornerRadius, y, x + w, y, x + w, y + cornerRadius)

            addBezier(points, x + w, y + h - cornerRadius, x + w, y + h, x + w - cornerRadius, y + h)

            if (hasBottomNotch) {
                points.add(Vec3f(x + notchStartOffset + notchWidth, y + h, 0f))
                points.add(Vec3f(x + notchStartOffset + notchWidth - 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset + 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + notchStartOffset, y + h, 0f))
            }

            addBezier(points, x + cornerRadius, y + h, x, y + h, x, y + h - cornerRadius)


            getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(color, clipped = false) {
                fillPolygon(PolyUtil.fillPolygon(points))
            }

            val strokeColor = color.mix(Color.BLACK, 0.2f)

            getPlainBuilder(UiSurface.LAYER_BACKGROUND).configured(strokeColor, clipped = false) {
                for (i in 0 until points.size) {
                    val p1 = points[i]
                    val p2 = points[(i + 1) % points.size]
                    line(p1.xy, p2.xy, 2f)
                }
            }
        }
    }

    private fun addBezier(points: MutableList<Vec3f>, x0: Float, y0: Float, xc: Float, yc: Float, x1: Float, y1: Float) {
        val segments = 8
        for (i in 0..segments) {
            val t = i / segments.toFloat()
            val u = 1 - t
            val px = u.pow(2) * x0 + 2 * u * t * xc + t.pow(2) * x1
            val py = u.pow(2) * y0 + 2 * u * t * yc + t.pow(2) * y1

            if (points.isEmpty() || i > 0) {
                points.add(Vec3f(px, py, 0f))
            }
        }
    }
}