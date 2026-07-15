package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.*

internal fun UiLayoutPipeline.placeNodeNow(task: NodePlacementTask) {
    val profile = activeProfile
    if (profile != null) {
        profile.placedNodes++
        profile.matrixCalculations++
    }
    val node = task.node
    val resolved = task.resolved
    val rect = task.rect
    val parentRect = task.parentRect
    val parentClip = task.parentClip
    val parentTransform = task.parentTransform
    val parentInputTransform = task.parentInputTransform
    val insideFramebuffer = task.insideFramebuffer
    val scrollState = task.scrollState
    val scrollbarReserves = task.scrollbarReserves
    val layouts = task.layouts
    val style = resolved[node]
    val boxes = nodeBoxes(rect, style, scrollbarReserves[node] ?: UiScrollbarReserve.None)
    val scrollOffset = scrollState.offset(node)
    // Computed by the parent inline flow (wrapping depends on where the span starts in a line)
    val textLayout = (node as? SpanNode)?.lineLayout
    val clip = if (style.clip || style.scrollable) parentClip.intersect(boxes.content) else parentClip
    val localX = rect.x - parentRect.x
    val localY = rect.y - parentRect.y
    val pivot = style.transform.pivot.resolve(rect.width, rect.height)
    val localTransform = style.transform.matrix(pivot, localX, localY, style.position.z)
    val transform = parentTransform * localTransform
    val inputTransform = if (parentInputTransform === parentTransform) {
        transform
    } else {
        parentInputTransform * localTransform
    }
    val opacityNeedsLayer = style.opacity < 1f && node.children.isNotEmpty()
    val needsFramebuffer = opacityNeedsLayer ||
            style.transform.needsFramebuffer || !insideFramebuffer && node.requiresTextLayer(transform) ||
            style.filter.requiresLayer ||
            style.backdropFilter.requiresLayer ||
            style.clipShape != null && style.clip
    if (profile != null) {
        if (needsFramebuffer) profile.framebufferNodes++
        if (textLayout != null) profile.recordTextLayout(textLayout)
    }

    layouts[node] = UiLayoutNode(
        node = node,
        rect = rect,
        content = boxes.content,
        clip = clip,
        worldTransform = transform,
        inputTransform = inputTransform,
        needsFramebuffer = needsFramebuffer,
        scrollOffset = scrollOffset,
        scrollArea = boxes.scrollArea,
        textLayout = textLayout,
        inlineDecoration = (node as? BaseUiNode)?.inlineDecoration,
    )

    placeChildren(
        ChildPlacementScope(
            node = node,
            resolved = resolved,
            style = style,
            measurePolicy = node.measurePolicy,
            content = boxes.content,
            parentRect = rect,
            transform = transform,
            inputTransform = inputTransform,
            clip = clip,
            insideFramebuffer = insideFramebuffer || needsFramebuffer,
            scrollState = scrollState,
            scrollbarReserves = scrollbarReserves,
            layouts = layouts,
        )
    )
}

private fun UiLayoutPipeline.placeChildren(scope: ChildPlacementScope) {
    val node = scope.node
    if (node.children.isEmpty()) return
    val viewport = if (scope.style.scrollable) {
        val offset = scope.scrollState.offset(node)
        scope.content.copy(x = scope.content.x - offset.x, y = scope.content.y - offset.y)
    } else {
        scope.content
    }
    val childScope = scope.copy(content = viewport)
    node.measurePolicy.policy().place(this, childScope)
}

internal fun UiLayoutPipeline.placeScopedNode(
    scope: ChildPlacementScope,
    node: UiNode,
    rect: UiRect,
    parentStyle: UiComputedStyle = scope.style,
    clip: UiRect? = scope.clip,
) {
    placeNode(
        node,
        scope.resolved,
        rect,
        scope.parentRect,
        parentStyle,
        clip,
        scope.transform,
        scope.inputTransform,
        scope.insideFramebuffer,
        scope.scrollState,
        scope.scrollbarReserves,
        scope.layouts,
    )
}
