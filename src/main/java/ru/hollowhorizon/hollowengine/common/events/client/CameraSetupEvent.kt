package ru.hollowhorizon.hollowengine.common.events.client

import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.mixins.client.CameraInvoker

class CameraSetupEvent(
    val gameRenderer: GameRenderer,
    val camera: Camera,
    val partialTick: Float,
    var yaw: Float,
    var pitch: Float,
    var roll: Float
) : Event

var Camera.pos: Vec3
    get() = this.position
    set(value) {
        (this as CameraInvoker).`hollowcore$setPosition`(value.x, value.y, value.z)
    }