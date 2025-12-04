package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.*

class ScratchBlockBackground(
    val color: Color,
    val isExpression: Boolean,
    val hasNext: Boolean,
    val isContainerHeader: Boolean = false
) : UiRenderer<UiNode> {

    private val notchWidth = 30f
    private val notchHeight = 8f
    private val notchX = 20f
    private val r = PuzzleShapes.CORNER_RADIUS

    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        if (isExpression) {
            PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y)
            points.add(Vec3f(x + w - r, y, 0f))
            PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)
            PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)
            points.add(Vec3f(x + r, y + h, 0f))
            PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)
            val tyStart = (h - PuzzleShapes.TAB_HEIGHT) / 2f
            points.add(Vec3f(x, tyStart + PuzzleShapes.TAB_HEIGHT, 0f))
            points.add(Vec3f(x - PuzzleShapes.TAB_WIDTH, tyStart + PuzzleShapes.TAB_HEIGHT - 5f, 0f))
            points.add(Vec3f(x - PuzzleShapes.TAB_WIDTH, tyStart + 5f, 0f))
            points.add(Vec3f(x, tyStart, 0f))
        } else {
            PuzzleShapes.addBezier(points, x, y + r, x, y, x + r, y)
            points.add(Vec3f(x + notchX, y, 0f))
            points.add(Vec3f(x + notchX + 5f, y + notchHeight, 0f))
            points.add(Vec3f(x + notchX + notchWidth - 5f, y + notchHeight, 0f))
            points.add(Vec3f(x + notchX + notchWidth, y, 0f))
            PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)

            PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)

            if (isContainerHeader) {
                val innerNotchX = BlockEditor.C_BLOCK_SPINE_WIDTH + notchX

                points.add(Vec3f(x + innerNotchX + notchWidth, y + h, 0f))
                points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + innerNotchX + 5f, y + h + notchHeight, 0f))
                points.add(Vec3f(x + innerNotchX, y + h, 0f))

                points.add(Vec3f(x, y + h, 0f))
            } else {
                if (hasNext) {
                    points.add(Vec3f(x + notchX + notchWidth, y + h, 0f))
                    points.add(Vec3f(x + notchX + notchWidth - 5f, y + h + notchHeight, 0f))
                    points.add(Vec3f(x + notchX + 5f, y + h + notchHeight, 0f))
                    points.add(Vec3f(x + notchX, y + h, 0f))
                }
                PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)
            }
        }

        drawPath(node, points, color)
    }

    private fun drawPath(node: UiNode, points: List<Vec3f>, color: Color) = with(node) {
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(color) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
        val strokeColor = color.mix(Color.BLACK, 0.2f)
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(strokeColor) {
            for (i in 0 until points.size) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]

                line(p1.xy, p2.xy, 2f)
            }
        }
    }
}

class ContainerFooterBackground(val color: Color) : UiRenderer<UiNode> {
    private val notchWidth = 30f
    private val notchHeight = 8f
    private val notchX = 20f
    private val r = PuzzleShapes.CORNER_RADIUS

    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        val innerNotchX = BlockEditor.C_BLOCK_SPINE_WIDTH + notchX

        points.add(Vec3f(x, y, 0f))
        points.add(Vec3f(x + innerNotchX, y, 0f))
        points.add(Vec3f(x + innerNotchX + 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth, y, 0f))

        PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)
        PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)

        points.add(Vec3f(x + notchX + notchWidth, y + h, 0f))
        points.add(Vec3f(x + notchX + notchWidth - 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + notchX + 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + notchX, y + h, 0f))

        PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(color) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
        val strokeColor = color.mix(Color.BLACK, 0.2f)
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(strokeColor) {
            for (i in 0 until points.size) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]
                line(p1.xy, p2.xy, 2f)
            }
        }
    }
}

class ContainerMiddleBackground(val color: Color) : UiRenderer<UiNode> {
    private val notchWidth = 30f
    private val notchHeight = 8f
    private val notchX = 20f
    private val spineW = BlockEditor.C_BLOCK_SPINE_WIDTH

    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        val innerNotchX = spineW + notchX

        points.add(Vec3f(x, y, 0f))
        points.add(Vec3f(x + innerNotchX, y, 0f))
        points.add(Vec3f(x + innerNotchX + 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth, y, 0f))

        points.add(Vec3f(x + w, y, 0f))
        points.add(Vec3f(x + w, y + h, 0f))

        points.add(Vec3f(x + innerNotchX + notchWidth, y + h, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + 5f, y + h + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX, y + h, 0f))
        points.add(Vec3f(x, y + h, 0f))

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(color) {
            fillPolygon(PolyUtil.fillPolygon(points))
        }
        val strokeColor = color.mix(Color.BLACK, 0.2f)
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(strokeColor) {
            for (i in 0 until points.size) {
                val p1 = points[i]
                val p2 = points[(i + 1) % points.size]
                line(p1.xy, p2.xy, 2f)
            }
        }
    }
}

context(node: UiNode)
inline fun <Layout: Struct> MeshBuilder<Layout>.configure(color: Color? = null, block: MeshBuilder<Layout>.() -> Unit) {
    val panel = node.findParentOfType<ScrollPaneNode>() ?: node
    val setBoundsUiVertex: MutableStructBufferView<UiVertexLayout>.(UiVertexLayout) -> Unit = {
        it.clip.set(panel.clipLeftPx, panel.clipTopPx, panel.clipRightPx, panel.clipBottomPx)
    }
    val setBoundsTextVertex: MutableStructBufferView<UiTextVertexLayout>.(UiTextVertexLayout) -> Unit = {
        it.clip.set(panel.clipLeftPx, panel.clipTopPx, panel.clipRightPx, panel.clipBottomPx)
    }
    val setBoundsCustom: MutableStructBufferView<*>.(Struct) -> Unit = {
        @Suppress("UNCHECKED_CAST")
        this as MutableStructBufferView<Struct>
        it.getFloat4(UiVertexLayout.clip.name)?.set(panel.clipLeftPx, panel.clipTopPx, panel.clipRightPx, panel.clipBottomPx)
    }

    val prevMod = vertexCustomizer
    @Suppress("UNCHECKED_CAST")
    when {
        geometry.layout === UiVertexLayout -> {
            this as MeshBuilder<UiVertexLayout>
            vertexCustomizer = setBoundsUiVertex
        }
        geometry.layout === UiTextVertexLayout -> {
            this as MeshBuilder<UiTextVertexLayout>
            vertexCustomizer = setBoundsTextVertex
        }
        else -> vertexCustomizer = setBoundsCustom
    }
    val prevColor = this.color
    color?.let { this.color = it }
    withTransform {
        translate(node.leftPx, node.topPx, 0f)
        this.block()
    }
    vertexCustomizer = prevMod
    this.color = prevColor
}