package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util

import ru.hollowhorizon.hc.client.models.gltf.animations.PlayMode
import ru.hollowhorizon.hc.client.models.gltf.manager.LayerMode

class AnimationContainer {
    var animation = ""
    var layerMode = LayerMode.ADD
    var playType = PlayMode.LOOPED
    var speed = 1.0f
}