package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import ru.hollowhorizon.hollowengine.client.shadergraph.*
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.graph.GraphRect

/** One pin of a placed node, where it sits in graph units. */
internal data class ShaderPin(val node: String, val name: String, val output: Boolean, val x: Float, val y: Float)

/** What a row of a node shows. */
internal sealed interface ShaderNodeRow {
    val top: Float
    val height: Float

    data class Output(val name: String, override val top: Float) : ShaderNodeRow {
        override val height get() = ShaderNodeLayout.ROW
    }

    data class Option(
        val name: String,
        val kind: ShaderOptionKind,
        override val top: Float,
        override val height: Float,
    ) : ShaderNodeRow

    /** An input that takes a link, or what it falls back to, or a texture: its name, and no field. */
    data class Input(val pin: ShaderPinSpec, override val top: Float) : ShaderNodeRow {
        override val height get() = ShaderNodeLayout.ROW
    }

    /** An input with nothing linked to it: its name and a field per component of its value. */
    data class Value(val pin: ShaderPinSpec, val components: Int, override val top: Float) : ShaderNodeRow {
        override val height get() = ShaderNodeLayout.FIELD_ROW
    }
}

/** Where everything of one node sits, in graph units from the corner of the node. */
internal class ShaderNodeBox(
    val node: ShaderGraphNode,
    val kind: ShaderNodeType,
    val width: Float,
    val height: Float,
    val rows: List<ShaderNodeRow>,
    val preview: Float,
    val previewHeight: Float,
    val previewTop: Float,
) {
    val rect: GraphRect get() = GraphRect(node.x, node.y, width, height)

    val isReroute: Boolean get() = kind.id == ShaderNodeLibrary.REROUTE

    val pins: List<ShaderPin>
        get() = when {
            isReroute -> reroutePins()
            node.collapsed -> collapsedPins()
            else -> rowPins()
        }

    /** A reroute is a dot, and a link goes in and comes out of its middle. */
    private fun reroutePins(): List<ShaderPin> {
        val x = node.x + width / 2f
        val y = node.y + height / 2f
        return listOf(
            ShaderPin(node.id, ShaderNodeLibrary.REROUTE_INPUT, false, x, y),
            ShaderPin(node.id, ShaderNodeLibrary.REROUTE_OUTPUT, true, x, y),
        )
    }

    private fun rowPins(): List<ShaderPin> = rows.mapNotNull { row ->
        val y = node.y + row.top + row.height / 2f
        when (row) {
            is ShaderNodeRow.Output -> ShaderPin(node.id, row.name, true, node.x + width, y)
            is ShaderNodeRow.Input -> ShaderPin(node.id, row.pin.name, false, node.x, y)
            is ShaderNodeRow.Value -> ShaderPin(node.id, row.pin.name, false, node.x, y)
            is ShaderNodeRow.Option -> null
        }
    }

    /** A collapsed node has no rows, so what is linked to it reaches the middle of its title bar. */
    private fun collapsedPins(): List<ShaderPin> {
        val y = node.y + ShaderNodeLayout.COLLAPSED / 2f
        return kind.outputs.map { ShaderPin(node.id, it.name, true, node.x + width, y) } +
                kind.inputs(node).map { ShaderPin(node.id, it.name, false, node.x, y) }
    }
}

internal object ShaderNodeLayout {
    const val WIDTH = 216f

    /** The title bar of a collapsed node, which is all of it; an open node has a line under it too. */
    const val COLLAPSED = 32f
    const val HEADER = COLLAPSED + 1f

    /** A row that is a name alone; one with a field to edit is [FIELD_ROW] tall. */
    const val ROW = 21f
    const val FIELD_ROW = 27f

    /** How far the rows sit from the edges of the node, sideways and above the first and under the last. */
    const val INSET = 12f
    const val PADDING = 10f
    const val PIN = 9f

    /** How wide a reroute dot is. */
    const val REROUTE = 12f

    fun of(graph: ShaderGraph, node: ShaderGraphNode, kind: ShaderNodeType, types: ShaderGraphTypes): ShaderNodeBox {
        if (kind.id == ShaderNodeLibrary.REROUTE) return ShaderNodeBox(node, kind, REROUTE, REROUTE, emptyList(), 0f, 0f, 0f)
        if (node.collapsed) return ShaderNodeBox(node, kind, WIDTH, COLLAPSED, emptyList(), 0f, 0f, 0f)
        val rows = ArrayList<ShaderNodeRow>()
        var y = HEADER + PADDING
        kind.outputs.forEach { output ->
            rows += ShaderNodeRow.Output(output.name, y)
            y += ROW
        }
        kind.options.forEach { option ->
            rows += ShaderNodeRow.Option(option.name, option.kind, y, FIELD_ROW)
            y += FIELD_ROW
        }
        kind.inputs(node).forEach { pin ->
            val linked = graph.linkInto(node.id, pin.name) != null
            val row = if (linked || pin.fallback != null || pin.type.fixed == ShaderType.TEXTURE) {
                ShaderNodeRow.Input(pin, y)
            } else {
                ShaderNodeRow.Value(pin, components(node, pin, types), y)
            }
            rows += row
            y += row.height
        }
        y += PADDING
        val preview = if (kind.showsPreview(node)) WIDTH - INSET * 2 else 0f
        val previewHeight = if (graph.target == ShaderTarget.POST) preview * ShaderGraphPreview.SCREEN_ASPECT else preview
        val previewTop = y
        if (preview > 0f) y += previewHeight + PADDING
        return ShaderNodeBox(node, kind, WIDTH, y, rows, preview, previewHeight, previewTop)
    }

    private fun components(node: ShaderGraphNode, pin: ShaderPinSpec, types: ShaderGraphTypes): Int {
        pin.type.fixed?.let { return it.width }
        val stored = node.values[pin.name]?.size ?: pin.default.size
        val resolved = types.input(node.id, pin.name)?.width ?: 1
        return maxOf(stored, resolved).coerceIn(1, 4)
    }
}

/** The colors of the pins and of the links between them; the pin rules of `shader-graph.hss` use the same. */
internal fun ShaderType.color(): UiColor = when (this) {
    ShaderType.FLOAT -> rgb(0xA5ABC0)
    ShaderType.VEC2 -> rgb(0x44BC74)
    ShaderType.VEC3 -> rgb(0xF0CF60)
    ShaderType.VEC4 -> rgb(0xC774AA)
    ShaderType.TEXTURE -> rgb(0xC9874A)
}

private fun rgb(value: Int) = UiColor.fromArgb(OPAQUE or value)

private const val OPAQUE = 0xFF shl 24

internal val GraphLinkColor = rgb(0xA5ABC0)
internal val GraphSelectedColor = UiColor(0.43f, 0.61f, 0.86f)

internal fun ShaderType.tag(): String = "sg-type-${name.lowercase()}"

/** The icon of the nodes of a category that have none of their own. */
internal fun ShaderNodeCategory.icon(): String = graphIcon(
    when (this) {
        ShaderNodeCategory.INPUT -> "object"
        ShaderNodeCategory.MATH -> "math"
        ShaderNodeCategory.VECTOR -> "coordinates"
        ShaderNodeCategory.NORMAL -> "material"
        ShaderNodeCategory.UV -> "coordinates"
        ShaderNodeCategory.TEXTURE -> "texture"
        ShaderNodeCategory.PROCEDURAL -> "noise"
        ShaderNodeCategory.OUTPUT -> "output"
    }
)

/** The icon of a kind in the title bar of its nodes and in the add menu. */
internal fun ShaderNodeType.displayIcon(): String = icon ?: category.icon()
