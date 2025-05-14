@file:OptIn(ExperimentalContracts::class)

package ru.hollowhorizon.hollowengine.client.gui.kool

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.KoolContext
import de.fabmax.kool.addScene
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.Layout.Companion.LAYOUT_EPS
import de.fabmax.kool.util.Time
import kotlin.contracts.ExperimentalContracts
import kotlin.math.max
import kotlin.math.round
import kotlin.random.Random

fun main() =
    KoolApplication(KoolConfigJvm(renderBackend = KoolConfigJvm.Backend.OPEN_GL, windowSize = Vec2i(512, 512))) {
        val widgets = (0..100).map { "*" to (Random.nextInt(0, 1000) to Random.nextInt(0, 1000)) }



        addScene {
            setupUiScene()

            addPanelSurface {
                val state = rememberScrollState()
                val scaleTarget = remember(1f)
                val layout = remember(ScrollableCellLayout())

                ScrollArea(
                    isScrollableHorizontal = false,
                    isScrollableVertical = false,
                    withVerticalScrollbar = false,
                    withHorizontalScrollbar = false,
                    containerModifier = {
                        it.onDrag {
                            val delta = it.pointer.delta
                            if (delta.x != 0f) {
                                state.xScrollDpDesired.set(state.xScrollDpDesired.value + Dp.fromPx(-delta.x).value)
                            }
                            if (delta.y != 0f) {
                                state.yScrollDpDesired.set(state.yScrollDpDesired.value + Dp.fromPx(-delta.y).value)
                            }
                        }.onWheelY {
                            val pointerPos = it.pointer.pos
                            val scrollCenter = pointerPos - it.position
                            val centerBeforeZoom = scrollCenter / layout.use().scale.value

                            val delta = it.pointer.scroll.y * 0.1f
                            scaleTarget.set((scaleTarget.value * (1f + delta)).coerceIn(0.2f, 5f))

                            val centerAfterZoom = scrollCenter / scaleTarget.value
                            val offset = (centerAfterZoom - centerBeforeZoom) * layout.use().scale.value

                            state.xScrollDpDesired.set(state.xScrollDpDesired.value + Dp.fromPx(offset.x).value)
                            state.yScrollDpDesired.set(state.yScrollDpDesired.value + Dp.fromPx(offset.y).value)
                        }
                    },
                    state = state
                ) {
                    val layout = remember(ScrollableCellLayout())

                    modifier.layout(layout.use())

                    onUpdate {
                        val s = layout.use().scale.value
                        val t = scaleTarget.value
                        val lerped = s + (t - s) * 0.15f
                        layout.use().scale.set(lerped)
                    }

                    modifier.allowOverScroll(true, true)

                    widgets.forEach { (widget, pos) ->
                        val (x, y) = pos

                        Box {
                            modifier.size(100.dp, 100.dp)
                                .margin(start = x.dp, top = y.dp)

                            Text(widget) {}
                        }
                    }
                }
            }
        }
    }

class ScrollableCellLayout : Layout {
    var scale = mutableStateOf(1f)
    private var oldScale = 1f

    override fun measureContentSize(uiNode: UiNode, ctx: KoolContext) = uiNode.run {
        oldScale = UiScale.uiScale.value
        UiScale.uiScale.set(oldScale * scale.use())
        UiScale.updateScale(uiNode.surface)

        val modWidth = modifier.width
        val modHeight = modifier.height

        var measuredWidth = 0f
        var measuredHeight = 0f
        var isDynamicWidth = true
        var isDynamicHeight = true

        if (modWidth is Dp) {
            measuredWidth = modWidth.px
            isDynamicWidth = false
        }
        if (modHeight is Dp) {
            measuredHeight = modHeight.px
            isDynamicHeight = false
        }

        if (isDynamicWidth || isDynamicHeight) {
            for (child in children) {
                if (isDynamicWidth) {
                    val pStart = max(paddingStartPx, child.marginStartPx)
                    val pEnd = max(paddingEndPx, child.marginEndPx)
                    measuredWidth = max(measuredWidth, child.contentWidthPx + pStart + pEnd)
                }
                if (isDynamicHeight) {
                    val pTop = max(paddingTopPx, child.marginTopPx)
                    val pBottom = max(paddingBottomPx, child.marginBottomPx)
                    measuredHeight = max(measuredHeight, child.contentHeightPx + pTop + pBottom)
                }
            }

            if (modWidth is Grow) measuredWidth = modWidth.clampPx(measuredWidth, measuredWidth)
            if (modHeight is Grow) measuredHeight = modHeight.clampPx(measuredHeight, measuredHeight)
        }

        setContentSize(measuredWidth, measuredHeight)

        UiScale.uiScale.set(oldScale)
        UiScale.updateScale(uiNode.surface)
    }

    override fun layoutChildren(uiNode: UiNode, ctx: KoolContext) {
        oldScale = UiScale.uiScale.value
        UiScale.uiScale.set(oldScale * scale.use(uiNode.surface))
        UiScale.updateScale(uiNode.surface)

        uiNode.children.forEach { child ->
            val growSpaceW = uiNode.widthPx - max(uiNode.paddingStartPx, child.marginStartPx) - max(uiNode.paddingEndPx, child.marginEndPx)
            val growSpaceH = uiNode.heightPx - max(uiNode.paddingTopPx, child.marginTopPx) - max(uiNode.paddingBottomPx, child.marginBottomPx)

            val childWidth = round(child.computeWidthFromDimension(growSpaceW) + LAYOUT_EPS)
            val childHeight = round(child.computeHeightFromDimension(growSpaceH) + LAYOUT_EPS)
            val childX = round(uiNode.computeChildLocationX(child, childWidth) + LAYOUT_EPS)
            val childY = round(uiNode.computeChildLocationY(child, childHeight) + LAYOUT_EPS)

            child.setBounds(childX, childY, childX + childWidth, childY + childHeight)
        }

        UiScale.uiScale.set(oldScale)
        UiScale.updateScale(uiNode.surface)
    }
}