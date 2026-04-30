package ru.hollowhorizon.hollowengine.client.render

import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer
import ru.hollowhorizon.hollowengine.common.events.ClientEvent

class CameraFovEvent(
    val gameRenderer: GameRenderer,
    val camera: Camera,
    val partialTick: Float,
    val changingFov: Boolean,
    var fov: Double,
) : ClientEvent
