package ru.hollowhorizon.hollowengine.client.render

import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer
import ru.hollowhorizon.hollowengine.common.events.ClientEvent

class CameraSetupEvent(
    val gameRenderer: GameRenderer,
    val camera: Camera,
    val partialTick: Float,
    var yaw: Float,
    var pitch: Float,
    var roll: Float,
) : ClientEvent