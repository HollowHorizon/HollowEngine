package ru.hollowhorizon.hollowengine.client.gui.animations

import de.fabmax.kool.util.Color
import java.util.*

// Модель одного узла (State)
data class GraphNode(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var x: Float,
    var y: Float,
    val color: Color,
    val width: Float = 150f,
    var properties: MutableMap<String, String> = mutableMapOf() // Для правой панели
)

// Модель связи (Transition)
data class GraphConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val color: Color = Color("D77F1C") // Оранжевый как на скрине
)