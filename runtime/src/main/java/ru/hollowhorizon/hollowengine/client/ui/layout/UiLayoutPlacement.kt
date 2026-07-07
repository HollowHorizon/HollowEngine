package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.UiInlineWidgetRun
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldNode
import ru.hollowhorizon.hollowengine.client.ui.widgets.textFieldDisplayLayout
import ru.hollowhorizon.hollowengine.client.ui.widgets.textFieldTextOffset

internal fun UiLayoutPipeline.placeNodeNow(task: NodePlacementTask) {
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
    val textLayout = when (node) {
        is TextFieldNode -> layoutTextFieldNode(node, resolved, style, boxes.content, scrollbarReserves)
        // Computed by the parent inline flow (wrapping depends on where the span starts in a line)
        is SpanNode -> node.lineLayout
        else -> null
    }
    val clip = if (style.clip || style.scrollable) parentClip.intersect(boxes.content) else parentClip
    val localX = rect.x - parentRect.x
    val localY = rect.y - parentRect.y
    val pivot = style.transform.pivot.resolve(rect.width, rect.height)
    val transform = parentTransform * UiMatrix4.translation(localX, localY, style.position.z) *
            style.transform.matrix(pivot)
    val inputTransform = parentInputTransform * UiMatrix4.translation(localX, localY, style.position.z) *
            style.transform.matrix(pivot)
    val opacityNeedsLayer = style.opacity < 1f && node.children.isNotEmpty()
    val needsFramebuffer = opacityNeedsLayer ||
            style.transform.needsFramebuffer || !insideFramebuffer && node.requiresTextLayer(transform) ||
            style.filter.requiresLayer ||
            style.backdropFilter.requiresLayer ||
            style.clipShape != null && style.clip

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
    if (node is TextFieldNode) {
        placeTextFieldInlineChildren(node, childScope)
        return
    }
    node.measurePolicy.policy().place(this, childScope)
}

private fun UiLayoutPipeline.placeTextFieldInlineChildren(node: TextFieldNode, scope: ChildPlacementScope) {
    val widgets = layoutChildren(node).associateBy { it.id }
    if (widgets.isEmpty() || scope.style.textField.inlayHints != true) return
    val content = scope.content
    val textLayout = scope.layouts[node]?.textLayout
        ?: layoutTextFieldNode(node, scope.resolved, scope.style, content, scope.scrollbarReserves)
    val textOffset = textFieldTextOffset(node, scope.style)
    for (line in textLayout.lines) {
        for (fragment in line.fragments) {
            if (fragment !is UiInlineWidgetRun) continue
            val child = widgets[fragment.widget.id] ?: continue
            val margin = scope.resolved[child].margin.resolve(content.width, content.height)
            placeScopedNode(
                scope,
                child,
                UiRect(
                    content.x + textOffset + line.x + fragment.x + margin.left,
                    content.y + line.y + fragment.y + margin.top,
                    (fragment.width - margin.horizontal).coerceAtLeast(0f),
                    (fragment.height - margin.vertical).coerceAtLeast(0f),
                ),
            )
        }
    }
}

internal fun UiLayoutPipeline.layoutTextFieldNode(
    node: TextFieldNode,
    resolved: UiNode,
    style: UiComputedStyle,
    content: UiRect,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
): UiTextLayout {
    val layout = temporaryTextFieldLayoutNode(node, content)
    val widgetMetrics = measureInlineWidgetMetrics(
        node,
        resolved,
        content.width,
        content.height,
        scrollbarReserves,
    )
    return textFieldDisplayLayout(node, style, layout, widgetMetrics)
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
