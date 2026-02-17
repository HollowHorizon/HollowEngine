package ru.hollowhorizon.hollowengine.client.gui.animations

import de.fabmax.kool.modules.ui2.mutableStateOf
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.models.internal.controller.WrapMode
import ru.hollowhorizon.hollowengine.client.utils.math.Interpolation
import java.util.*

enum class NodeType {
    STATE,
    ENTRY,
    ANY,
}

class GraphNode(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    x: Float,
    y: Float,
    val color: Color,
    var type: NodeType = NodeType.STATE,
    var animationName: String = "",
    var wrapMode: WrapMode = WrapMode.Loop,
    var speed: Float = 1.0f,
    var weight: Float = 1.0f,
    var priority: Int = 0,
    var fadeIn: Float = 0.2f,
    var fadeOut: Float = 0.2f,
    var blendCurve: Interpolation = Interpolation.QUINT_IN,
    var overrideTranslation: Boolean = false,
    var overrideRotation: Boolean = false,
    var overrideScale: Boolean = false,
    var extras: MutableMap<String, String> = mutableMapOf(),
) {
    val xState = mutableStateOf(x)
    val yState = mutableStateOf(y)

    val widthState = mutableStateOf(150f)
    val heightState = mutableStateOf(75f)
}

@Serializable
data class ConnectionProperties(
    val weight: Float = 1.0f,
    val condition: String = "",
    val duration: Float = 0.25f,
    val exitTime: Float? = null,
    val fadeIn: Float = 0.2f,
    val fadeOut: Float = 0.2f,
    val mute: Boolean = false,
    val extras: MutableMap<String, String> = mutableMapOf(),
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
    val properties: ConnectionProperties = ConnectionProperties.DEFAULT,
)

@Serializable
data class GraphNodeData(
    val id: String,
    val title: String,
    val x: Float,
    val y: Float,
    val type: NodeType = NodeType.STATE,
    val animationName: String = "",
    val wrapMode: WrapMode = WrapMode.Loop,
    val speed: Float = 1.0f,
    val weight: Float = 1.0f,
    val priority: Int = 0,
    val fadeIn: Float = 0.2f,
    val fadeOut: Float = 0.2f,
    val blendCurve: Interpolation = Interpolation.QUINT_IN,
    val overrideTranslation: Boolean = false,
    val overrideRotation: Boolean = false,
    val overrideScale: Boolean = false,
    val extras: Map<String, String> = emptyMap(),
)

@Serializable
data class GraphConnectionData(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val label: String = "",
    val properties: ConnectionProperties = ConnectionProperties.DEFAULT,
)

@Serializable
data class AnimationControllerGraph(
    val modelPath: String = "hollowengine:models/entity/player_model.gltf",
    val nodes: List<GraphNodeData> = emptyList(),
    val connections: List<GraphConnectionData> = emptyList(),
)
