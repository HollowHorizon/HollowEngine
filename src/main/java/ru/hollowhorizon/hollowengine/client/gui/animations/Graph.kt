package ru.hollowhorizon.hollowengine.client.gui.animations

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import java.util.*

class GraphNode(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    x: Float,
    y: Float,
    val color: Color,
    var properties: MutableMap<String, String> = mutableMapOf()
) {
    val xState = mutableStateOf(x)
    val yState = mutableStateOf(y)

    val widthState = mutableStateOf(150f)
    val heightState = mutableStateOf(75f)
}

data class GraphConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val color: Color = Color("D77F1C")
)