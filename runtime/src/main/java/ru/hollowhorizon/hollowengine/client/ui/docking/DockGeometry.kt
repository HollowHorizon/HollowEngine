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

object DockDropResolver {
    private const val EdgeRatio = 0.12f

    fun resolve(layouts: List<DockNodeLayout>, x: Float, y: Float): DockTarget? {
        val stack = layouts.asReversed().firstOrNull { it.stack && it.rect.contains(x, y) }
        if (stack != null) return DockTarget(stack.nodeId, placementFor(stack.rect, x, y))

        val node = layouts.asReversed().firstOrNull { it.rect.contains(x, y) } ?: return null
        return DockTarget(node.nodeId, placementFor(node.rect, x, y))
    }

    private fun placementFor(rect: DockRect, x: Float, y: Float): DockPlacement {
        val localX = ((x - rect.x) / rect.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
        val localY = ((y - rect.y) / rect.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
        return when {
            localX < EdgeRatio -> DockPlacement.LEFT
            localX > 1f - EdgeRatio -> DockPlacement.RIGHT
            localY < EdgeRatio -> DockPlacement.TOP
            localY > 1f - EdgeRatio -> DockPlacement.BOTTOM
            else -> DockPlacement.CENTER
        }
    }
}
