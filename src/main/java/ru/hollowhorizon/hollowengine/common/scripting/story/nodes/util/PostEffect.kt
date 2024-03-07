package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.util

import ru.hollowhorizon.hc.client.utils.rl

object PostEffect {
    val SEPIA = "hc:shaders/post/sepia.json".rl
    val GRAY = "hc:shaders/post/gray.json".rl
    val SHAKE = "hc:shaders/post/shake.json".rl
    val VIGNETTE = "hc:shaders/post/vig.json".rl
    val BLUR = "shaders/post/blur.json".rl

    fun create(string: String) = string.rl
}