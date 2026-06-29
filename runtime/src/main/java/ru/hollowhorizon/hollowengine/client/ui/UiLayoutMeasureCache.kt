package ru.hollowhorizon.hollowengine.client.ui


internal data class MeasureCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val availableWidth: Float,
    val availableHeight: Float,
    val widthOverride: Float?,
    val heightOverride: Float?,
    val deferFlexibleWidth: Boolean,
    val deferFlexibleHeight: Boolean,
    val allowWidthOverflow: Boolean,
    val allowHeightOverflow: Boolean,
    val reserve: UiScrollbarReserve,
)

internal data class FlowChildrenCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val availableWidth: Float,
    val availableHeight: Float,
    val deferFlexibleWidth: Boolean,
    val deferFlexibleHeight: Boolean,
    val allowWidthOverflow: Boolean,
    val allowHeightOverflow: Boolean,
)

internal data class InlineWidgetMetricsCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val availableWidth: Float,
    val availableHeight: Float,
)

internal data class IntrinsicSizeCacheKey(
    val nodeId: Int,
    val subtreeRevision: Long,
    val availableWidth: Float,
    val availableHeight: Float,
    val knownContentWidth: Float?,
    val knownContentHeight: Float?,
)

internal data class MeasureRequest(
    val node: UiNode,
    val resolved: ResolvedUiTree,
    val availableWidth: Float,
    val availableHeight: Float,
    val scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    val widthOverride: Float? = null,
    val heightOverride: Float? = null,
    val deferFlexibleWidth: Boolean = false,
    val deferFlexibleHeight: Boolean = false,
    val allowWidthOverflow: Boolean = false,
    val allowHeightOverflow: Boolean = false,
) {
    fun cacheKey(): MeasureCacheKey {
        val reserve = scrollbarReserves[node] ?: UiScrollbarReserve.None
        return MeasureCacheKey(
            nodeId = System.identityHashCode(node),
            subtreeRevision = node.layoutState.subtreeRevision,
            availableWidth = availableWidth.layoutCacheValue(),
            availableHeight = availableHeight.layoutCacheValue(),
            widthOverride = widthOverride?.layoutCacheValue(),
            heightOverride = heightOverride?.layoutCacheValue(),
            deferFlexibleWidth = deferFlexibleWidth,
            deferFlexibleHeight = deferFlexibleHeight,
            allowWidthOverflow = allowWidthOverflow,
            allowHeightOverflow = allowHeightOverflow,
            reserve = reserve,
        )
    }
}

internal class MeasureContext(
    private val compute: (MeasureRequest) -> LayoutSize,
) {
    private val activeKeys = HashSet<MeasureCacheKey>()
    private val measured = HashMap<MeasureCacheKey, LayoutSize>()
    private val measuredChildren = HashMap<FlowChildrenCacheKey, List<MeasuredChild>>()
    private val inlineWidgetMetrics = HashMap<InlineWidgetMetricsCacheKey, Map<String, UiInlineWidgetMetrics>>()
    private val intrinsicSizes = HashMap<IntrinsicSizeCacheKey, LayoutSize>()

    fun measure(request: MeasureRequest): LayoutSize {
        val key = request.cacheKey()
        measured[key]?.let { return it }
        if (!activeKeys.add(key)) return compute(request)
        return try {
            compute(request).also { measured[key] = it }
        } finally {
            activeKeys.remove(key)
        }
    }

    fun measureChildren(key: FlowChildrenCacheKey, compute: () -> List<MeasuredChild>): List<MeasuredChild> {
        measuredChildren[key]?.let { return it }
        return compute().also { measuredChildren[key] = it }
    }

    fun inlineMetrics(
        key: InlineWidgetMetricsCacheKey,
        compute: () -> Map<String, UiInlineWidgetMetrics>,
    ): Map<String, UiInlineWidgetMetrics> {
        inlineWidgetMetrics[key]?.let { return it }
        return compute().also { inlineWidgetMetrics[key] = it }
    }

    fun intrinsicSize(key: IntrinsicSizeCacheKey, compute: () -> LayoutSize): LayoutSize {
        intrinsicSizes[key]?.let { return it }
        return compute().also { intrinsicSizes[key] = it }
    }
}
