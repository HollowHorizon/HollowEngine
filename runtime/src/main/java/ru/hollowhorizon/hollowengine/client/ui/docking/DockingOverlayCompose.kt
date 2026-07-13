package ru.hollowhorizon.hollowengine.client.ui.docking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ru.hollowhorizon.hollowengine.client.ui.*

@Composable
internal fun FloatingResizeHandle(window: FloatingDockWindow, state: DockingState) {
    DockResizeEdge.entries.forEach { edge ->
        val dragStart = remember(window.id, edge) { arrayOf(window) }
        Box(
            id = "${window.id}-resize-${edge.name.lowercase()}",
            tags = listOf(DockTags.ResizeHandle),
            modifier = Modifier.resizeHandleModifier(edge)
                .input(hoverable = true, draggable = true)
                .cursor(edge.cursorShape)
                .onPress { dragStart[0] = window }
                .onDrag { event ->
                    state.resizeFloatingFrom(
                        window.id,
                        edge,
                        dragStart[0],
                        event.dragTotalX,
                        event.dragTotalY,
                    )
                    event.consume()
                }
        )
    }
}

@Composable
internal fun DockDropOverlay(state: DockingState) {
    Box(
        id = "dock-drop-overlay",
        tags = listOf(DockTags.DropOverlay),
        modifier = Modifier.size(100.percent, 100.percent)
            .layer(10_000)
    ) {
        val root = state.root
        if (root == null) {
            DockPlusDropZones(state, DockTarget.Root, DockRect(0f, 0f, 100f, 100f), 1)
            return@Box
        }
        DockLayoutCalculator.layout(root, DockRect(0f, 0f, 100f, 100f))
            .filter { it.stack }
            .forEach { layout ->
                DockPlusDropZones(state, DockTarget(anchorId = layout.nodeId), layout.rect, 1)
            }
        DockRootDropZones(state)
    }
}

@Composable
private fun DockRootDropZones(state: DockingState) {
    DockDropZone(state, DockTarget(placement = DockPlacement.LEFT), width = 25.px, height = 125.px, layer = 2)
    DockDropZone(state, DockTarget(placement = DockPlacement.RIGHT), width = 25.px, height = 125.px, layer = 2)
    DockDropZone(state, DockTarget(placement = DockPlacement.TOP), width = 125.px, height = 25.px, layer = 2)
    DockDropZone(state, DockTarget(placement = DockPlacement.BOTTOM), width = 125.px, height = 25.px, layer = 2)
}

@Composable
private fun DockPlusDropZones(state: DockingState, baseTarget: DockTarget, rect: DockRect, layer: Int) {
    val cell = minOf(rect.width, rect.height) * 0.6f
    val gap = cell * 0.18f
    val centerX = (rect.x + rect.width * 0.5f).percent - (cell * 0.5f).px
    val centerY = (rect.y + rect.height * 0.5f).percent - (cell * 0.5f).px

    DockDropZone(
        state,
        baseTarget.copy(placement = DockPlacement.CENTER),
        centerX,
        centerY,
        cell.px,
        cell.px,
        layer,
    )
    DockDropZone(
        state,
        baseTarget.copy(placement = DockPlacement.LEFT),
        centerX - cell.px - gap.px,
        centerY,
        cell.px,
        cell.px,
        layer,
    )
    DockDropZone(
        state,
        baseTarget.copy(placement = DockPlacement.RIGHT),
        centerX + cell.px + gap.px,
        centerY,
        cell.px,
        cell.px,
        layer,
    )
    DockDropZone(
        state,
        baseTarget.copy(placement = DockPlacement.TOP),
        centerX,
        centerY - cell.px - gap.px,
        cell.px,
        cell.px,
        layer,
    )
    DockDropZone(
        state,
        baseTarget.copy(placement = DockPlacement.BOTTOM),
        centerX,
        centerY + cell.px + gap.px,
        cell.px,
        cell.px,
        layer,
    )
}

@Composable
private fun DockDropZone(
    state: DockingState,
    target: DockTarget,
    x: UiLength? = null,
    y: UiLength? = null,
    width: UiLength,
    height: UiLength,
    layer: Int,
) {
    val active = state.previewTarget == target
    Box(
        id = "dock-drop-${target.anchorId ?: "root"}-${target.placement.name.lowercase()}",
        tags = listOf(DockTags.DropZone),
        modifier = Modifier.size(width, height)
            .layer(layer)
            .background(if (active) DockColors.DropZoneActive else DockColors.DropZone)
            .border(1.px, if (active) DockColors.DropZoneBorderActive else DockColors.DropZoneBorder)
            .input(hoverable = true, clickable = true)
            .cursor(UiCursorShape.HAND)
            .onHover {
                state.previewDock(target)
            }
            .onExit {
                if (state.previewTarget == target) state.previewDock(null)
            }
            .onRelease { event ->
                state.dockDraggedWindow(target)
                event.consume()
            }.then(
                if (x == null || y == null) {
                    if (target.placement == DockPlacement.LEFT || target.placement == DockPlacement.RIGHT) {
                        Modifier.align(
                            horizontal = if (target.placement == DockPlacement.RIGHT) UiAlign.END else UiAlign.START,
                            vertical = UiAlign.CENTER,
                        )
                    } else {
                        Modifier.align(
                            horizontal = UiAlign.CENTER,
                            vertical = if (target.placement == DockPlacement.BOTTOM) UiAlign.END else UiAlign.START,
                        )
                    }
                } else Modifier.position(x, y)
            )
    )
}

private fun Modifier.resizeHandleModifier(edge: DockResizeEdge): Modifier {
    val thickness = 2.px
    val corner = 6.px
    val layer = if (edge.isCorner) layer(1) else layer(0)
    return layer.then(
        when (edge) {
            DockResizeEdge.LEFT -> Modifier.position(0.px, 0.px).size(thickness, 100.percent)
            DockResizeEdge.RIGHT -> Modifier.align(UiAlign.END, UiAlign.START).size(thickness, 100.percent)
            DockResizeEdge.TOP -> Modifier.position(0.px, 0.px).size(100.percent, thickness)
            DockResizeEdge.BOTTOM -> Modifier.align(UiAlign.START, UiAlign.END).size(100.percent, thickness)
            DockResizeEdge.TOP_LEFT -> Modifier.position(0.px, 0.px).size(corner, corner)
            DockResizeEdge.TOP_RIGHT -> Modifier.align(UiAlign.END, UiAlign.START).size(corner, corner)
            DockResizeEdge.BOTTOM_LEFT -> Modifier.align(UiAlign.START, UiAlign.END).size(corner, corner)
            DockResizeEdge.BOTTOM_RIGHT -> Modifier.align(UiAlign.END, UiAlign.END).size(corner, corner)
        }
    )
}

private val DockResizeEdge.isCorner: Boolean
    get() = this == DockResizeEdge.TOP_LEFT ||
            this == DockResizeEdge.TOP_RIGHT ||
            this == DockResizeEdge.BOTTOM_LEFT ||
            this == DockResizeEdge.BOTTOM_RIGHT

private val DockResizeEdge.cursorShape: UiCursorShape
    get() = when (this) {
        DockResizeEdge.LEFT,
        DockResizeEdge.RIGHT,
            -> UiCursorShape.RESIZE_HORIZONTAL

        DockResizeEdge.TOP,
        DockResizeEdge.BOTTOM,
            -> UiCursorShape.RESIZE_VERTICAL

        DockResizeEdge.TOP_LEFT,
        DockResizeEdge.BOTTOM_RIGHT,
            -> UiCursorShape.RESIZE_NWSE

        DockResizeEdge.TOP_RIGHT,
        DockResizeEdge.BOTTOM_LEFT,
            -> UiCursorShape.RESIZE_NESW
    }
