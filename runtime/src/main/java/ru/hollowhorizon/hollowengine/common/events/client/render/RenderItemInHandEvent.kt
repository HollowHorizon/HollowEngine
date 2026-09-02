package ru.hollowhorizon.hollowengine.common.events.client.render

import net.minecraft.client.Camera
import org.joml.Matrix4f
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class RenderItemInHandEvent(
    val camera: Camera,
    val partialTick: Float,
    val projectionMatrix: Matrix4f,
) : ClientEvent, Cancellable {
    override var isCanceled = false

    companion object : EventHandler<RenderItemInHandEvent>()
}
