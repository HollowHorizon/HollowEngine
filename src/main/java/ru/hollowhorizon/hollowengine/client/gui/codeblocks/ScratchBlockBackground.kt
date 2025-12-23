package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.*

class ScratchBlockBackground(
    val color: Color,
    val isExpression: Boolean,
    val hasNext: Boolean,
    val hasPrev: Boolean = true,
    val isContainerHeader: Boolean = false,
    val drawInnerShadow: Boolean = false,
) : UiRenderer<UiNode> {

    override fun renderUi(node: UiNode) = with(node) {
        val notchWidth = sizes.gap * 1.5f
        val notchHeight = sizes.smallGap * 0.75f
        val notchX = sizes.gap
        val r = sizes.smallGap

        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        if (isExpression) {
            PuzzleShapes.addBezier(points, x, y + r.px, x, y, x + r.px, y)
            points.add(Vec3f(x + w - r.px, y, 0f))
            PuzzleShapes.addBezier(points, x + w - r.px, y, x + w, y, x + w, y + r.px)
            PuzzleShapes.addBezier(points, x + w, y + h - r.px, x + w, y + h, x + w - r.px, y + h)
            points.add(Vec3f(x + r.px, y + h, 0f))
            PuzzleShapes.addBezier(points, x + r.px, y + h, x, y + h, x, y + h - r.px)

            val width = Dp(4f).px
            val height = Dp(12f).px

            val tyStart = (h - height) / 2f
            points.add(Vec3f(x, tyStart + height, 0f))
            points.add(Vec3f(x - width, tyStart + height - notchHeight.px, 0f))
            points.add(Vec3f(x - width, tyStart + notchHeight.px, 0f))
            points.add(Vec3f(x, tyStart, 0f))
        } else {
            PuzzleShapes.addBezier(points, x, y + r.px, x, y, x + r.px, y)

            if (hasPrev) {
                points.add(Vec3f(x + notchX.px, y, 0f))
                points.add(Vec3f(x + notchX.px + notchHeight.px, y + notchHeight.px, 0f))
                points.add(Vec3f(x + notchX.px + notchWidth.px - notchHeight.px, y + notchHeight.px, 0f))
                points.add(Vec3f(x + notchX.px + notchWidth.px, y, 0f))
            }

            PuzzleShapes.addBezier(points, x + w - r.px, y, x + w, y, x + w, y + r.px)
            PuzzleShapes.addBezier(points, x + w, y + h - r.px, x + w, y + h, x + w - r.px, y + h)

            if (isContainerHeader) {
                val innerNotchX = BlockEditor.C_BLOCK_SPINE_WIDTH + notchX.px
                points.add(Vec3f(x + innerNotchX + notchWidth.px, y + h, 0f))
                points.add(Vec3f(x + innerNotchX + notchWidth.px - notchHeight.px, y + h + notchHeight.px, 0f))
                points.add(Vec3f(x + innerNotchX + notchHeight.px, y + h + notchHeight.px, 0f))
                points.add(Vec3f(x + innerNotchX, y + h, 0f))
                points.add(Vec3f(x, y + h, 0f))
            } else {
                if (hasNext) {
                    points.add(Vec3f(x + notchX.px + notchWidth.px, y + h, 0f))
                    points.add(Vec3f(x + notchX.px + notchWidth.px - notchHeight.px, y + h + notchHeight.px, 0f))
                    points.add(Vec3f(x + notchX.px + notchHeight.px, y + h + notchHeight.px, 0f))
                    points.add(Vec3f(x + notchX.px, y + h, 0f))
                }
                PuzzleShapes.addBezier(points, x + r.px, y + h, x, y + h, x, y + h - r.px)
            }
        }

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(null) {
            if(!drawInnerShadow) PuzzleShapes.drawShadow(points)

            color = this@ScratchBlockBackground.color
            fillPolygon(PolyUtil.fillPolygon(points))

        }
        node.getPlainBuilder(UiSurface.LAYER_FLOATING).configure(null) {
            if (drawInnerShadow) {
                PuzzleShapes.drawInnerShadow(
                    points,
                    width = 1f.dp.px,
                    color = Color.BLACK.withAlpha(0.5f)
                )
            }
        }
    }
}

class ContainerFooterBackground(
    val color: Color,
    val hasNext: Boolean = true
) : UiRenderer<UiNode> {

    override fun renderUi(node: UiNode) = with(node) {
        val notchWidth = (sizes.gap * 1.5f).px
        val notchHeight = (sizes.smallGap * 0.75f).px
        val notchX = sizes.gap.px
        val r = sizes.smallGap.px

        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        val innerNotchX = BlockEditor.C_BLOCK_SPINE_WIDTH + notchX

        points.add(Vec3f(x, y, 0f))
        points.add(Vec3f(x + innerNotchX, y, 0f))
        points.add(Vec3f(x + innerNotchX + notchHeight, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - notchHeight, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth, y, 0f))

        PuzzleShapes.addBezier(points, x + w - r, y, x + w, y, x + w, y + r)
        PuzzleShapes.addBezier(points, x + w, y + h - r, x + w, y + h, x + w - r, y + h)

        if (hasNext) {
            points.add(Vec3f(x + notchX + notchWidth, y + h, 0f))
            points.add(Vec3f(x + notchX + notchWidth - notchHeight, y + h + notchHeight, 0f))
            points.add(Vec3f(x + notchX + notchHeight, y + h + notchHeight, 0f))
            points.add(Vec3f(x + notchX, y + h, 0f))
        }

        PuzzleShapes.addBezier(points, x + r, y + h, x, y + h, x, y + h - r)

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(color) {
            PuzzleShapes.drawShadow(points)
            fillPolygon(PolyUtil.fillPolygon(points))
        }
    }
}

class ContainerMiddleBackground(val color: Color) : UiRenderer<UiNode> {

    override fun renderUi(node: UiNode) = with(node) {
        val notchWidth = (sizes.gap * 1.5f).px
        val notchHeight = (sizes.smallGap * 0.75f).px
        val notchX = sizes.gap.px
        val spineW = sizes.gap.px

        val w = node.widthPx
        val h = node.heightPx
        val x = 0f
        val y = 0f
        val points = mutableListOf<Vec3f>()

        val innerNotchX = spineW + notchX

        points.add(Vec3f(x, y, 0f))
        points.add(Vec3f(x + innerNotchX, y, 0f))
        points.add(Vec3f(x + innerNotchX + notchHeight, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - notchHeight, y + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth, y, 0f))

        points.add(Vec3f(x + w, y, 0f))
        points.add(Vec3f(x + w, y + h, 0f))

        points.add(Vec3f(x + innerNotchX + notchWidth, y + h, 0f))
        points.add(Vec3f(x + innerNotchX + notchWidth - notchHeight, y + h + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX + notchHeight, y + h + notchHeight, 0f))
        points.add(Vec3f(x + innerNotchX, y + h, 0f))
        points.add(Vec3f(x, y + h, 0f))

        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(color) {
            PuzzleShapes.drawShadow(points)
            fillPolygon(PolyUtil.fillPolygon(points))
        }
    }
}

class SpineBackground(val color: Color) : UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) = with(node) {
        val w = node.widthPx + 10f
        val h = node.heightPx
        node.getPlainBuilder(UiSurface.LAYER_BACKGROUND).configure(color) {

            val p0 = vertex { it.position.set(0f, 0f, 0f); it.color.set(this@SpineBackground.color) }
            val p1 = vertex { it.position.set(w, 0f, 0f); it.color.set(this@SpineBackground.color) }
            val p2 = vertex { it.position.set(w, h, 0f); it.color.set(this@SpineBackground.color) }
            val p3 = vertex { it.position.set(0f, h, 0f); it.color.set(this@SpineBackground.color) }

            addTriIndices(p0, p1, p2)
            addTriIndices(p0, p2, p3)

            val shadowColor = PuzzleShapes.SHADOW_COLOR
            val transparent = shadowColor.withAlpha(0f)
            val radius = PuzzleShapes.SHADOW_RADIUS
            val offsetY = PuzzleShapes.SHADOW_OFFSET_Y

            val si0 = vertex { it.position.set(0f, 0f, 0f); it.color.set(shadowColor) }
            val si1 = vertex { it.position.set(0f, h, 0f); it.color.set(shadowColor) }

            val so0 = vertex { it.position.set(-radius.px, offsetY.px, 0f); it.color.set(transparent) }
            val so1 = vertex { it.position.set(-radius.px, h + offsetY.px, 0f); it.color.set(transparent) }

            addTriIndices(si0, so0, so1)
            addTriIndices(si0, so1, si1)
        }
    }
}

context(node: UiNode)
inline fun <Layout : Struct> MeshBuilder<Layout>.configure(
    color: Color? = null,
    block: MeshBuilder<Layout>.() -> Unit,
) {
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
        it.getFloat4(UiVertexLayout.clip.name)
            ?.set(panel.clipLeftPx, panel.clipTopPx, panel.clipRightPx, panel.clipBottomPx)
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