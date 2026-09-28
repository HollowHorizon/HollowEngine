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
        override val height get() = ShaderNodeLayout.ROW
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
    val previewTop: Float,
) {
    val rect: GraphRect get() = GraphRect(node.x, node.y, width, height)

    val pins: List<ShaderPin>
        get() = rows.mapNotNull { row ->
            val y = node.y + row.top + row.height / 2f
            when (row) {
                is ShaderNodeRow.Output -> ShaderPin(node.id, row.name, true, node.x + width, y)
                is ShaderNodeRow.Input -> ShaderPin(node.id, row.pin.name, false, node.x, y)
                is ShaderNodeRow.Value -> ShaderPin(node.id, row.pin.name, false, node.x, y)
                is ShaderNodeRow.Option -> null
            }
        }
}

internal object ShaderNodeLayout {
    const val WIDTH = 176f
    const val OUTPUT_WIDTH = 196f
    const val HEADER = 22f
    const val ROW = 18f
    const val OPTION_ROW = 20f
    const val EXPRESSION_ROW = 24f
    const val PADDING = 5f
    const val PREVIEW = 116f
    const val OUTPUT_PREVIEW = 176f
    const val PIN = 10f

    fun of(graph: ShaderGraph, node: ShaderGraphNode, kind: ShaderNodeType, types: ShaderGraphTypes): ShaderNodeBox {
        val width = if (kind.master != null) OUTPUT_WIDTH else WIDTH
        val rows = ArrayList<ShaderNodeRow>()
        var y = HEADER + PADDING
        kind.outputs.forEach { output ->
            rows += ShaderNodeRow.Output(output.name, y)
            y += ROW
        }
        kind.options.forEach { option ->
            val height = if (option.kind == ShaderOptionKind.EXPRESSION) EXPRESSION_ROW else OPTION_ROW
            rows += ShaderNodeRow.Option(option.name, option.kind, y, height)
            y += height
        }
        kind.inputs(node).forEach { pin ->
            val linked = graph.linkInto(node.id, pin.name) != null
            rows += if (linked || pin.fallback != null || pin.type.fixed == ShaderType.TEXTURE) {
                ShaderNodeRow.Input(pin, y)
            } else {
                ShaderNodeRow.Value(pin, components(node, pin, types), y)
            }
            y += ROW
        }
        y += PADDING
        val preview = if (kind.showsPreview(node)) (if (kind.master != null) OUTPUT_PREVIEW else PREVIEW) else 0f
        val previewTop = y
        if (preview > 0f) y += preview + PADDING
        return ShaderNodeBox(node, kind, width, y, rows, preview, previewTop)
    }

    private fun components(node: ShaderGraphNode, pin: ShaderPinSpec, types: ShaderGraphTypes): Int {
        pin.type.fixed?.let { return it.width }
        val stored = node.values[pin.name]?.size ?: pin.default.size
        val resolved = types.input(node.id, pin.name)?.width ?: 1
        return maxOf(stored, resolved).coerceIn(1, 4)
    }
}

internal fun ShaderType.color(): UiColor = when (this) {
    ShaderType.FLOAT -> UiColor(0.62f, 0.66f, 0.74f)
    ShaderType.VEC2 -> UiColor(0.47f, 0.80f, 0.56f)
    ShaderType.VEC3 -> UiColor(0.96f, 0.82f, 0.38f)
    ShaderType.VEC4 -> UiColor(0.90f, 0.52f, 0.78f)
    ShaderType.TEXTURE -> UiColor(0.95f, 0.55f, 0.35f)
}

internal val GraphLinkColor = UiColor(0.60f, 0.64f, 0.72f)
internal val GraphSelectedColor = UiColor(0.43f, 0.61f, 0.86f)

internal fun ShaderType.tag(): String = "sg-type-${name.lowercase()}"
