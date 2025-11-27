package ru.hollowhorizon.hollowengine.client.gui.kool

import de.fabmax.kool.KoolContext
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.geometry.MeshBuilder
import de.fabmax.kool.util.set
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.math.min
import kotlin.math.sqrt

@OptIn(ExperimentalContracts::class)
inline fun UiScope.Triangle(
    rotation: Float = ArrowScope.ROTATION_RIGHT,
    scopeName: String? = null,
    isHoverable: Boolean = true,
    block: ArrowScope.() -> Unit,
): ArrowScope {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val arrow = uiNode.createChild(scopeName, TriangleNode::class, TriangleNode.factory)
    arrow.modifier.rotation(rotation)
    if (isHoverable) {
        arrow.modifier.hoverListener(arrow)
    }
    arrow.block()
    return arrow
}

class TriangleNode(parent: UiNode?, surface: UiSurface) : UiNode(parent, surface), ArrowScope, Hoverable {
    override val modifier = ArrowModifier(surface)
    override val isHovered: Boolean get() = isHoveredState.value

    private var isHoveredState = mutableStateOf(false)
    private val rotationAnimator = AnimatedFloat(0.1f)
    private var isFirst = true
    private var prevRotation = 0f

    override fun measureContentSize(ctx: KoolContext) {
        val modWidth = modifier.width
        val modHeight = modifier.height
        val measuredWidth = if (modWidth is Dp) modWidth.px else sizes.gap.px + paddingStartPx + paddingEndPx
        val measuredHeight = if (modHeight is Dp) modHeight.px else sizes.gap.px + paddingTopPx + paddingBottomPx
        setContentSize(measuredWidth, measuredHeight)

        if (isFirst) {
            prevRotation = modifier.rotation
            isFirst = false
        } else if (prevRotation != modifier.rotation && !rotationAnimator.isActive) {
            rotationAnimator.start()
        }
    }

    override fun render(ctx: KoolContext) {
        super.render(ctx)

        val p = rotationAnimator.progressAndUse()
        val rot = modifier.rotation * p + prevRotation * (1f - p)
        val color = if (isHoveredState.use()) modifier.arrowHoverColor else modifier.arrowColor
        getPlainBuilder().configured(color) {

            triangleDown(widthPx * 0.5f, heightPx * 0.5f, min(innerWidthPx, innerHeightPx), -90 + rot)
        }

        if (!rotationAnimator.isActive) {
            prevRotation = modifier.rotation
        }
    }

    override fun onEnter(ev: PointerEvent) {
        isHoveredState.set(true)
    }

    override fun onExit(ev: PointerEvent) {
        isHoveredState.set(false)
    }

    companion object {
        val factory: (UiNode, UiSurface) -> TriangleNode = { parent, surface -> TriangleNode(parent, surface) }
    }
}

fun MeshBuilder<UiVertexLayout>.triangleDown(centerX: Float, centerY: Float, size: Float, rotation: Float = 0f) {
    val halfSize = size / 2f
    val height = size * sqrt(3f) / 2f

    // Вершины треугольника (до поворота)
    val x1 = 0f
    val y1 = height / 2f

    val x2 = -halfSize
    val y2 = -height / 2f

    val x3 = halfSize
    val y3 = -height / 2f

    // Матрица поворота
    val angle = rotation.deg
    val cos = angle.cos
    val sin = angle.sin

    fun rotateX(x: Float, y: Float) = centerX + cos * x - sin * y
    fun rotateY(x: Float, y: Float) = centerY + sin * x + cos * y


    val i1 = vertex { it.position.set(rotateX(x1, y1), rotateY(x1, y1), 0f) }
    val i2 = vertex { it.position.set(rotateX(x2, y2), rotateY(x2, y2), 0f) }
    val i3 = vertex { it.position.set(rotateX(x3, y3), rotateY(x3, y3), 0f) }

    addTriIndices(i1, i2, i3)
}