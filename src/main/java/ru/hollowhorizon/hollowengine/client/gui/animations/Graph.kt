package ru.hollowhorizon.hollowengine.client.gui.animations

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
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

data class ConnectionProperties(
    val weight: Float = 1.0f,
    val condition: String = "",
    val exitTime: Float? = null,
    val transitionDuration: Float = 0.25f,
    val mute: Boolean = false,
    val extras: MutableMap<String, String> = mutableMapOf()
) {
    val hasCondition: Boolean get() = condition.isNotBlank()

    val hasExitTime: Boolean get() = exitTime != null

    companion object {
        val DEFAULT = ConnectionProperties()
    }
}

data class GraphConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val color: Color = ColorTheme.Accents.Main.withAlpha(0.4f),
    val id: String = UUID.randomUUID().toString(),
    val properties: ConnectionProperties = ConnectionProperties.DEFAULT
)
