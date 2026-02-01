package ru.hollowhorizon.hollowengine.client.render.shaders

import de.fabmax.kool.pipeline.Texture2d

interface TransitionShader {
    var inputTexture: Texture2d?
    var targetTexture: Texture2d?
    var progress: Float
}