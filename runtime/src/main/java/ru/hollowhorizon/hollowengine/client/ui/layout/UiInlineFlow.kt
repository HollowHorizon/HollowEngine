package ru.hollowhorizon.hollowengine.client.ui.layout

import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.style.*
import ru.hollowhorizon.hollowengine.client.ui.text.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiInlineStyle

/**
 * Per-line box-decoration geometry for an inline group (a [SpanNode] or a nested inline flow).
 * [lines] are the group's per-line boxes relative to the node's own rect origin; the renderer
 * paints background/border per line, honouring [decorationBreak] (slice vs clone).
 */
class InlineGroupDecoration(
    val lines: List<UiRect>,
    val decorationBreak: UiBoxDecorationBreak,
)

/** How a parent inline flow wants one of its (possibly transitive) descendants placed. */
internal class InlinePlacement(
    val node: UiNode,
    val rect: UiRect,
    val textLayout: UiTextLayout? = null,
    val decoration: InlineGroupDecoration? = null,
)

internal class InlineFlowLayout(
    val width: Float,
    val height: Float,
    /** Direct-child placements keyed by each inline group node in the subtree (incl. the root). */
    val childLayouts: Map<UiNode, List<InlinePlacement>>,
)

internal fun UiNode.isInlineFlow(): Boolean =
    (measurePolicy as? UiBuiltInMeasurePolicy)?.kind == UiBuiltInMeasurePolicyKind.INLINE_FLOW

/** The container-level horizontal alignment of an inline flow (unifies UiAlign and textAlign). */
internal fun inlineFlowAlign(style: UiComputedStyle): UiAlign {
    style.alignItemsHorizontal.takeUnless { it == UiAlign.AUTO }?.let { return it }
    return when (style.textAlign) {
        UiTextAlign.LEFT -> UiAlign.START
        UiTextAlign.CENTER -> UiAlign.CENTER
        UiTextAlign.RIGHT -> UiAlign.END
        UiTextAlign.JUSTIFY -> UiAlign.JUSTIFY
    }
}

/** An inline decoration group: a span or nested inline flow that owns pieces and draws per line. */
private class GroupCtx(
    val node: UiNode,
    val parent: UiNode,
    val leadPad: Float,
    val trailPad: Float,
    val padTop: Float,
    val padBottom: Float,
    val decorationBreak: UiBoxDecorationBreak,
    val isSpan: Boolean,
)

private sealed class Piece {
    var x = 0f
    var lineIndex = 0
    var lineTop = 0f
    var lineHeight = 0f
    abstract val width: Float
    abstract val height: Float

    /** Enclosing groups, outer -> inner; the innermost of a word is its span. */
    abstract val groups: List<GroupCtx>
}

private class WordPiece(
    val text: String,
    val effects: List<UiTextEffect>,
    override val width: Float,
    override val height: Float,
    override val groups: List<GroupCtx>,
) : Piece()

private class SpacePiece(
    override val width: Float,
    override val height: Float,
    override val groups: List<GroupCtx>,
) : Piece()

private class AtomPiece(
    val child: MeasuredChild,
    val parent: UiNode,
    override val width: Float,
    override val height: Float,
    override val groups: List<GroupCtx>,
) : Piece()

/** A group's leading/trailing padding advance - zero-height, part of the group's extent. */
private class PadPiece(
    override val width: Float,
    override val groups: List<GroupCtx>,
) : Piece() {
    override val height: Float get() = 0f
}

private class BreakPiece(override val groups: List<GroupCtx>) : Piece() {
    override val width: Float get() = 0f
    override val height: Float get() = 0f
}


internal fun UiLayoutPipeline.computeInlineFlow(
    container: UiNode,
    resolved: UiNode,
    /** The container's content width - the reference alignment lays lines out against. */
    availableWidth: Float,
    availableHeight: Float,
    align: UiAlign,
    lineSpacing: Float,
    /** Whether lines break at [availableWidth]; when false, only an explicit '\n' breaks. */
    wrap: Boolean,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
): InlineFlowLayout {
    val pieces = ArrayList<Piece>()
    tokenizeInline(layoutChildren(container), resolved, container, emptyList(), availableWidth, availableHeight, scrollbarReserves, pieces)
    val lines = breakIntoLines(pieces, availableWidth, wrap)
    // Alignment still uses the container width even with wrap off, so a single line can sit
    // centred/right/justified when the container is wider than the text (a fit container just
    // hugs the text, making alignment a natural no-op).
    positionLines(lines, availableWidth, align, lineSpacing)

    val width = lines.maxOfOrNull { it.naturalWidth } ?: 0f
    val height = lines.sumOf { it.height.toDouble() }.toFloat() +
        (if (lines.size > 1) lineSpacing * (lines.size - 1) else 0f)

    val childLayouts = assembleChildLayouts(container, pieces)
    return InlineFlowLayout(width, height, childLayouts)
}

private fun UiLayoutPipeline.tokenizeInline(
    nodes: List<UiNode>,
    resolved: UiNode,
    parent: UiNode,
    groups: List<GroupCtx>,
    availableWidth: Float,
    availableHeight: Float,
    scrollbarReserves: Map<UiNode, UiScrollbarReserve>,
    out: MutableList<Piece>,
) {
    for (child in nodes) {
        val style = resolved[child]
        when {
            child is SpanNode -> {
                val group = groupCtx(child, parent, style, availableWidth, availableHeight, isSpan = true)
                tokenizeSpanWords(child, style, groups + group, out)
            }

            child.isInlineFlow() -> {
                val group = groupCtx(child, parent, style, availableWidth, availableHeight, isSpan = false)
                val inner = groups + group
                if (group.leadPad > 0f) out += PadPiece(group.leadPad, inner)
                tokenizeInline(
                    layoutChildren(child), resolved, child, inner,
                    availableWidth, availableHeight, scrollbarReserves, out,
                )
                if (group.trailPad > 0f) out += PadPiece(group.trailPad, inner)
            }

            else -> {
                val margin = style.margin.resolve(availableWidth, availableHeight)
                val size = measureNode(child, resolved, availableWidth, availableHeight, scrollbarReserves)
                out += AtomPiece(
                    MeasuredChild(child, style, size, margin),
                    parent,
                    size.width + margin.horizontal,
                    size.height + margin.vertical,
                    groups,
                )
            }
        }
    }
}

private fun groupCtx(
    node: UiNode,
    parent: UiNode,
    style: UiComputedStyle,
    availableWidth: Float,
    availableHeight: Float,
    isSpan: Boolean,
): GroupCtx {
    // Spans are pure text runs: no box padding (wrap a fragment in a nested inline flow to pad it).
    val padding = if (isSpan) ResolvedUiInsets(0f, 0f, 0f, 0f) else style.padding.resolve(availableWidth, availableHeight)
    val margin = if (isSpan) ResolvedUiInsets(0f, 0f, 0f, 0f) else style.margin.resolve(availableWidth, availableHeight)
    // A decorated group gets at least its corner radius as horizontal padding so text never runs
    // into the rounded corners / right up to the background edge.
    val decorated = !isSpan && (style.background != UiPaint.None || style.border.width != UiInsets.Zero)
    val minSidePad = if (decorated) style.border.radius else 0f
    return GroupCtx(
        node = node,
        parent = parent,
        leadPad = maxOf(padding.left, minSidePad) + margin.left,
        trailPad = maxOf(padding.right, minSidePad) + margin.right,
        padTop = padding.top,
        padBottom = padding.bottom,
        decorationBreak = style.boxDecorationBreak,
        isSpan = isSpan,
    )
}

private fun tokenizeSpanWords(span: SpanNode, style: UiComputedStyle, groups: List<GroupCtx>, out: MutableList<Piece>) {
    val fontSize = style.fontSize
    val fontFamily = style.fontFamily
    // The span's effects (bold/italic/underline/strike/wave/etc.) ride on each run's inline style,
    // which is where the renderer reads glyph-level flags and per-run effects from. Measure with
    // them too, so a bold word reserves its wider advance and the following word keeps its space.
    val effects = style.textEffects
    val inlineStyle = UiInlineStyle(effects = effects)
    val spaceWidth = style.spaceWidth ?: UiTextLayouter.measureStyledTextWidth(" ", fontSize, fontFamily, inlineStyle)
    val text = span.text.resolve()
    val word = StringBuilder()
    fun flush() {
        if (word.isEmpty()) return
        val wordText = word.toString()
        val width = UiTextLayouter.measureStyledTextWidth(wordText, fontSize, fontFamily, inlineStyle)
        out += WordPiece(wordText, effects, width, fontSize, groups)
        word.clear()
    }
    for (ch in text) {
        when {
            ch == '\n' -> {
                flush()
                out += BreakPiece(groups)
            }

            ch.isWhitespace() -> {
                flush()
                if (out.lastOrNull() !is SpacePiece) out += SpacePiece(spaceWidth, fontSize, groups)
            }

            else -> word.append(ch)
        }
    }
    flush()
}

private class FlowLine {
    val pieces = ArrayList<Piece>()
    var hardBreak = false
    var width = 0f
    var height = 0f
    var top = 0f
    val naturalWidth: Float get() = width

    fun trimTrailingSpaces() {
        while (pieces.lastOrNull() is SpacePiece) width -= pieces.removeLast().width
    }
}

private fun breakIntoLines(pieces: List<Piece>, wrapWidth: Float, wrap: Boolean): List<FlowLine> {
    val wrapping = wrap && wrapWidth.isFinite() && wrapWidth > 0f
    val lines = ArrayList<FlowLine>()
    var line = FlowLine()

    fun commit(hard: Boolean) {
        line.trimTrailingSpaces()
        line.hardBreak = hard
        lines += line
        line = FlowLine()
    }

    var breakBefore = false
    for (piece in pieces) {
        when (piece) {
            is BreakPiece -> {
                commit(hard = true)
                breakBefore = false
            }

            is SpacePiece -> {
                if (line.pieces.isNotEmpty()) {
                    line.pieces += piece
                    line.width += piece.width
                }
                breakBefore = true
            }

            is PadPiece -> {
                line.pieces += piece
                line.width += piece.width
            }

            is WordPiece, is AtomPiece -> {
                if (wrapping && breakBefore && line.pieces.isNotEmpty() && line.width + piece.width > wrapWidth) {
                    commit(hard = false)
                }
                line.pieces += piece
                line.width += piece.width
                breakBefore = false
            }
        }
    }
    if (line.pieces.isNotEmpty() || lines.isEmpty()) commit(hard = true)
    return lines
}


private fun positionLines(lines: List<FlowLine>, wrapWidth: Float, align: UiAlign, lineSpacing: Float) {
    val hasWidth = wrapWidth.isFinite() && wrapWidth > 0f
    var y = 0f
    lines.forEachIndexed { lineIndex, line ->
        val lineHeight = line.pieces.maxOfOrNull { it.height } ?: 0f
        line.height = lineHeight
        line.top = y

        val justify = align == UiAlign.JUSTIFY && !line.hardBreak && hasWidth
        val spaceCount = line.pieces.count { it is SpacePiece }
        val extraPerSpace = if (justify && spaceCount > 0) (wrapWidth - line.width) / spaceCount else 0f

        val offset = if (!hasWidth) 0f else when (align) {
            UiAlign.CENTER -> (wrapWidth - line.width) / 2f
            UiAlign.END -> wrapWidth - line.width
            else -> 0f
        }

        var x = offset
        for (piece in line.pieces) {
            piece.x = x
            piece.lineIndex = lineIndex
            piece.lineTop = y
            piece.lineHeight = lineHeight
            x += piece.width
            if (piece is SpacePiece) x += extraPerSpace
        }
        y += lineHeight
        if (lineIndex != lines.lastIndex) y += lineSpacing
    }
}

private fun assembleChildLayouts(container: UiNode, pieces: List<Piece>): Map<UiNode, List<InlinePlacement>> {
    val placementByNode = LinkedHashMap<UiNode, InlinePlacement>()
    val groupCtxByNode = LinkedHashMap<UiNode, GroupCtx>()

    for (piece in pieces) {
        for (group in piece.groups) groupCtxByNode.putIfAbsent(group.node, group)
    }

    for ((node, ctx) in groupCtxByNode) {
        val owned = pieces.filter { p -> (p is WordPiece || p is AtomPiece) && p.groups.any { it.node === node } }
        if (owned.isEmpty()) continue
        val byLine = owned.groupBy { it.lineIndex }.toSortedMap()
        val firstLine = byLine.keys.first()
        val lastLine = byLine.keys.last()

        val lineBoxes = byLine.entries.map { (lineIndex, linePieces) ->
            val padLeft = if (lineIndex == firstLine) ctx.leadPad else 0f
            val padRight = if (lineIndex == lastLine) ctx.trailPad else 0f
            val left = linePieces.minOf { it.x } - padLeft
            val right = linePieces.maxOf { it.x + it.width } + padRight
            val top = linePieces.first().lineTop - ctx.padTop
            val height = linePieces.first().lineHeight + ctx.padTop + ctx.padBottom
            UiRect(left, top, right - left, height)
        }
        val boundLeft = lineBoxes.minOf { it.x }
        val boundTop = lineBoxes.minOf { it.y }
        val boundRight = lineBoxes.maxOf { it.x + it.width }
        val boundBottom = lineBoxes.maxOf { it.y + it.height }
        val bounds = UiRect(boundLeft, boundTop, boundRight - boundLeft, boundBottom - boundTop)

        val decoration = InlineGroupDecoration(
            lines = lineBoxes.map { UiRect(it.x - boundLeft, it.y - boundTop, it.width, it.height) },
            decorationBreak = ctx.decorationBreak,
        )
        val textLayout = if (ctx.isSpan) spanTextLayout(byLine, boundLeft, boundTop) else null

        placementByNode[node] = InlinePlacement(node, bounds, textLayout, decoration)
    }

    for (piece in pieces) {
        if (piece !is AtomPiece) continue
        val margin = piece.child.margin
        val rect = UiRect(
            piece.x + margin.left,
            piece.lineTop + (piece.lineHeight - piece.height) / 2f + margin.top,
            piece.child.size.width,
            piece.child.size.height,
        )
        placementByNode[piece.child.node] = InlinePlacement(piece.child.node, rect)
    }

    val result = LinkedHashMap<UiNode, MutableList<InlinePlacement>>()
    for ((node, placement) in placementByNode) {
        val parent = parentOf(node, groupCtxByNode, pieces, container)
        result.getOrPut(parent) { ArrayList() } += placement
    }
    return result
}

private fun parentOf(
    node: UiNode,
    groupCtxByNode: Map<UiNode, GroupCtx>,
    pieces: List<Piece>,
    container: UiNode,
): UiNode {
    groupCtxByNode[node]?.let { return it.parent }
    val atom = pieces.firstOrNull { it is AtomPiece && it.child.node === node } as? AtomPiece
    return atom?.parent ?: container
}

private fun spanTextLayout(
    byLine: Map<Int, List<Piece>>,
    boundLeft: Float,
    boundTop: Float,
): UiTextLayout {
    val textLines = byLine.values.map { linePieces ->
        val words = linePieces.filterIsInstance<WordPiece>()
        val lineLeft = words.minOfOrNull { it.x } ?: 0f
        val lineRight = words.maxOfOrNull { it.x + it.width } ?: 0f
        val lineTop = linePieces.first().lineTop
        val lineHeight = linePieces.first().lineHeight
        UiTextLine(
            text = words.joinToString(" ") { it.text },
            x = 0f,
            y = lineTop - boundTop,
            width = lineRight - lineLeft,
            naturalWidth = lineRight - lineLeft,
            height = lineHeight,
            fragments = words.map { word ->
                UiTextRun(
                    text = word.text,
                    style = UiInlineStyle(effects = word.effects),
                    x = word.x - boundLeft,
                    // Words centre within the line height — same rule atoms use — so mixed-height
                    // lines (text + a taller inline group/chip) stay vertically aligned.
                    y = (lineHeight - word.height) / 2f,
                    width = word.width,
                    height = word.height,
                )
            },
        )
    }
    val width = textLines.maxOfOrNull { it.width } ?: 0f
    val height = textLines.sumOf { it.height.toDouble() }.toFloat()
    return UiTextLayout(textLines, width, height)
}

internal fun UiLayoutPipeline.placeInlineFlowChildren(scope: ChildPlacementScope) {
    val node = scope.node
    val directChildren = if (node in inlineFlowFlattened) {
        inlineFlowChildLayouts[node].orEmpty()
    } else {
        val flow = computeInlineFlow(
            node,
            scope.resolved,
            scope.content.width,
            scope.content.height,
            inlineFlowAlign(scope.style),
            scope.style.lineSpacing,
            wrap = scope.style.textWrap,
            scope.scrollbarReserves,
        )
        val originX = scope.content.x
        val originY = scope.content.y
        for ((groupNode, placements) in flow.childLayouts) {
            inlineFlowChildLayouts[groupNode] = placements.map { it.shifted(originX, originY) }
            if (groupNode !== node) inlineFlowFlattened += groupNode
        }
        inlineFlowChildLayouts[node].orEmpty()
    }

    for (placement in directChildren) {
        val target = placement.node
        if (target is SpanNode) target.lineLayout = placement.textLayout
        (target as? BaseUiNode)?.inlineDecoration = placement.decoration
        placeScopedNode(scope, target, placement.rect)
    }
}

private fun InlinePlacement.shifted(dx: Float, dy: Float) =
    InlinePlacement(node, UiRect(rect.x + dx, rect.y + dy, rect.width, rect.height), textLayout, decoration)
