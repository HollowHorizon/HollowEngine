package ru.hollowhorizon.hollowengine.client.ui.ide.files.shadergraph

import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraph
import ru.hollowhorizon.hollowengine.client.shadergraph.ShaderGraphGroup
import ru.hollowhorizon.hollowengine.client.ui.graph.GROUP_HEADER
import ru.hollowhorizon.hollowengine.client.ui.graph.GROUP_PADDING
import ru.hollowhorizon.hollowengine.client.ui.graph.GraphRect

/**
 * A collapsed group, drawn as one node at the corner of where its nodes are: a pin for every input
 * something outside links into, and one for every output that feeds something outside.
 */
internal class ShaderGroupCard(
    val group: ShaderGraphGroup,
    val rect: GraphRect,
    val inputs: List<ShaderPin>,
    val outputs: List<ShaderPin>,
) {
    val pins: List<ShaderPin> get() = inputs + outputs
}

/** What is displayed on the canvas from the graph, taking its groups into account */
internal class ShaderGraphScene(
    val boxes: Map<String, ShaderNodeBox>,
    val frames: Map<String, GraphRect>,
    val cards: Map<String, ShaderGroupCard>,
    val pins: List<ShaderPin>,
) {
    companion object {
        fun of(graph: ShaderGraph, all: Map<String, ShaderNodeBox>): ShaderGraphScene {
            val frames = LinkedHashMap<String, GraphRect>()
            val cards = LinkedHashMap<String, ShaderGroupCard>()
            val hidden = HashSet<String>()
            graph.groups.forEach { group ->
                val members = group.nodes.filter { it in all }.toSet()
                val around = GraphRect.around(members.map { all.getValue(it).rect }) ?: return@forEach
                if (!group.collapsed) {
                    frames[group.id] =
                        around.expanded(GROUP_PADDING, GROUP_HEADER + GROUP_PADDING, GROUP_PADDING, GROUP_PADDING)
                    return@forEach
                }
                hidden += members
                cards[group.id] = card(graph, group, members, around)
            }
            val boxes = all.filterKeys { it !in hidden }
            return ShaderGraphScene(
                boxes,
                frames,
                cards,
                boxes.values.flatMap { it.pins } + cards.values.flatMap { it.pins })
        }

        private fun card(
            graph: ShaderGraph,
            group: ShaderGraphGroup,
            members: Set<String>,
            around: GraphRect,
        ): ShaderGroupCard {
            val entering =
                graph.links.filter { it.to in members && it.from !in members }.map { it.to to it.input }.distinct()
            val leaving =
                graph.links.filter { it.from in members && it.to !in members }.map { it.from to it.output }.distinct()
            val rows = entering.size + leaving.size
            val height =
                ShaderNodeLayout.HEADER + if (rows == 0) 0f else ShaderNodeLayout.PADDING * 2 + rows * ShaderNodeLayout.ROW
            val rect = GraphRect(around.x, around.y, ShaderNodeLayout.WIDTH, height)

            fun rowY(index: Int) =
                rect.y + ShaderNodeLayout.HEADER + ShaderNodeLayout.PADDING + ShaderNodeLayout.ROW * (index + 0.5f)
            return ShaderGroupCard(
                group = group,
                rect = rect,
                outputs = leaving.mapIndexed { index, (node, pin) ->
                    ShaderPin(
                        node,
                        pin,
                        true,
                        rect.x + rect.width,
                        rowY(index)
                    )
                },
                inputs = entering.mapIndexed { index, (node, pin) ->
                    ShaderPin(
                        node,
                        pin,
                        false,
                        rect.x,
                        rowY(leaving.size + index)
                    )
                },
            )
        }
    }
}
