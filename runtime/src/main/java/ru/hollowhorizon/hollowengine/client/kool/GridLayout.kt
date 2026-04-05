package ru.hollowhorizon.hollowengine.client.kool

import de.fabmax.kool.KoolContext
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.Layout.Companion.LAYOUT_EPS
import net.minecraft.util.Mth
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

object GridLayout : Layout {
    override fun measureContentSize(uiNode: UiNode, ctx: KoolContext) {
        val size = uiNode.children.size
        if(size == 1) {
            return CellLayout.measureContentSize(uiNode, ctx)
        }
        val columns =
            Mth.ceil(size / sqrt(size * uiNode.childrenRatio).coerceAtLeast(1f))
        Grid.measure(uiNode, columns)
    }

    override fun layoutChildren(uiNode: UiNode, ctx: KoolContext) {
        val size = uiNode.children.size
        if(size == 1) {
            return CellLayout.layoutChildren(uiNode, ctx)
        }
        val columns =
            Mth.ceil(size / sqrt(size * uiNode.childrenRatio).coerceAtLeast(1f))
        Grid.layout(uiNode, columns)
    }
}

fun GridLayout(columns: Int) = object : Layout {
    override fun layoutChildren(uiNode: UiNode, ctx: KoolContext) = Grid.layout(uiNode, columns)
    override fun measureContentSize(uiNode: UiNode, ctx: KoolContext) = Grid.measure(uiNode, columns)
}

fun UiScope.Grid(columns: Int = 0, block: UiScope.() -> Unit) {
    Column {
        modifier.layout(if (columns > 0) GridLayout(columns) else GridLayout)

        block()
    }
}

private val UiNode.childrenRatio: Float
    get() {
        var totalAspectRatio = 0f
        for (child in children) {
            val childAspectRatio = child.contentWidthPx / child.contentHeightPx
            totalAspectRatio += childAspectRatio
        }
        return if (children.isNotEmpty()) totalAspectRatio / children.size else 1f
    }

private object Grid {
    fun measure(uiNode: UiNode, columns: Int) = uiNode.run {
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
            val childWidths = FloatArray(columns) { 0f }
            var rowHeight = 0f
            var currentColumn = 0

            for (child in children) {
                if (isDynamicWidth) {
                    val colWidth = child.contentWidthPx + child.marginStartPx + child.marginEndPx
                    childWidths[currentColumn] = max(childWidths[currentColumn], colWidth)
                }
                if (isDynamicHeight) {
                    val heightWithMargins = child.contentHeightPx + child.marginTopPx + child.marginBottomPx
                    rowHeight = max(rowHeight, heightWithMargins)
                }

                currentColumn++
                if (currentColumn == columns) {
                    currentColumn = 0
                    measuredHeight += rowHeight
                    rowHeight = 0f
                }
            }

            if (currentColumn != 0) {
                measuredHeight += rowHeight
            }

            if (isDynamicWidth) {
                measuredWidth = childWidths.sum() + paddingStartPx + paddingEndPx
            }
            if (isDynamicHeight) {
                measuredHeight += paddingTopPx + paddingBottomPx
            }

            if (modWidth is Grow) measuredWidth = modWidth.clampPx(measuredWidth, measuredWidth)
            if (modHeight is Grow) measuredHeight = modHeight.clampPx(measuredHeight, measuredHeight)
        }
        setContentSize(measuredWidth, measuredHeight)
    }

    fun layout(uiNode: UiNode, columns: Int) = uiNode.run {
        val columnWidths = FloatArray(columns) { 0f }
        var currentColumn = 0
        var currentRowY = paddingTopPx
        var rowHeight = 0f

        for (child in children) {
            val colWidth = child.contentWidthPx + child.marginStartPx + child.marginEndPx
            columnWidths[currentColumn] = max(columnWidths[currentColumn], colWidth)

            val heightWithMargins = child.contentHeightPx + child.marginTopPx + child.marginBottomPx
            rowHeight = max(rowHeight, heightWithMargins)

            currentColumn++
            if (currentColumn == columns) {
                currentColumn = 0
                currentRowY += rowHeight
                rowHeight = 0f
            }
        }

        currentColumn = 0
        currentRowY = paddingTopPx
        rowHeight = 0f

        for (child in children) {
            val layoutX = paddingStartPx + columnWidths.take(currentColumn).sum() + child.marginStartPx
            val layoutY = currentRowY + child.marginTopPx
            val layoutW = round(child.contentWidthPx + LAYOUT_EPS)
            val layoutH = round(child.contentHeightPx + LAYOUT_EPS)

            child.setBounds(layoutX, layoutY, layoutX + layoutW, layoutY + layoutH)

            rowHeight = max(rowHeight, layoutH + child.marginTopPx + child.marginBottomPx)
            currentColumn++

            if (currentColumn == columns) {
                currentColumn = 0
                currentRowY += rowHeight
                rowHeight = 0f
            }
        }
    }
}