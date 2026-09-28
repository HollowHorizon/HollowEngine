package ru.hollowhorizon.hollowengine.client.ui.docking

enum class DockOrientation {
    HORIZONTAL,
    VERTICAL
}

enum class DockPlacement {
    LEFT,
    RIGHT,
    TOP,
    BOTTOM,
    CENTER
}

data class DockRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(px: Float, py: Float): Boolean {
        return px >= x && py >= y && px <= right && py <= bottom
    }
}

data class DockTarget(
    val anchorId: String? = null,
    val placement: DockPlacement = DockPlacement.CENTER,
    val tabIndex: Int? = null,
) {
    companion object {
        val Root = DockTarget()
    }
}

data class DockNodeLayout(
    val nodeId: String,
    val rect: DockRect,
    val stack: Boolean,
)

object DockLayoutCalculator {
    fun layout(root: DockNode?, bounds: DockRect): List<DockNodeLayout> {
        if (root == null || bounds.width <= 0f || bounds.height <= 0f) return emptyList()
        val layouts = mutableListOf<DockNodeLayout>()
        collect(root, bounds, layouts)
        return layouts
    }

    private fun collect(node: DockNode, rect: DockRect, layouts: MutableList<DockNodeLayout>) {
        when (node) {
            is DockNode.Stack -> layouts += DockNodeLayout(node.id, rect, stack = true)
            is DockNode.Split -> {
                layouts += DockNodeLayout(node.id, rect, stack = false)
                val firstRect: DockRect
                val secondRect: DockRect
                if (node.orientation == DockOrientation.HORIZONTAL) {
                    val firstWidth = rect.width * node.fraction
                    firstRect = rect.copy(width = firstWidth)
                    secondRect = rect.copy(x = rect.x + firstWidth, width = rect.width - firstWidth)
                } else {
                    val firstHeight = rect.height * node.fraction
                    firstRect = rect.copy(height = firstHeight)
                    secondRect = rect.copy(y = rect.y + firstHeight, height = rect.height - firstHeight)
                }
                collect(node.first, firstRect, layouts)
                collect(node.second, secondRect, layouts)
            }
        }
    }
}

fun dockPreviewRect(target: DockTarget, layouts: List<DockNodeLayout>, bounds: DockRect): DockRect? {
    val base = if (target.anchorId == null) bounds else layouts.firstOrNull { it.nodeId == target.anchorId }?.rect
    return base?.part(target.placement)
}

private fun DockRect.part(placement: DockPlacement): DockRect = when (placement) {
    DockPlacement.CENTER -> this
    DockPlacement.LEFT -> copy(width = width * 0.5f)
    DockPlacement.RIGHT -> copy(x = x + width * 0.5f, width = width * 0.5f)
    DockPlacement.TOP -> copy(height = height * 0.5f)
    DockPlacement.BOTTOM -> copy(y = y + height * 0.5f, height = height * 0.5f)
}