package ru.hollowhorizon.hollowengine.client.screen

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.math.Vector3d
import com.mojang.math.Vector3f
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.ClientEvents
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.abs

object CameraHelper {
    private var mouseBasedViewVector = Vector3d(0.0, 0.0, 0.0)
    private var oldMouseX = 0.0
    private var oldMouseY = 0.0
    private var oldPlayerXRot = 0.0F
    private var oldPlayerYRot = 0.0F

    fun getMouseBasedViewVector(minecraft: Minecraft, xRot: Float, yRot: Float): Vector3d {
        val xpos = minecraft.mouseHandler.xpos()
        val ypos = minecraft.mouseHandler.ypos()
        if (abs(xpos - oldMouseX) + abs(ypos - oldMouseY) > 0.01 || xRot != oldPlayerXRot || yRot != oldPlayerYRot) {
            mouseBasedViewVector = getMouseBasedViewVector(minecraft, xpos, ypos)
            oldMouseX = xpos
            oldMouseY = ypos
            oldPlayerXRot = xRot
            oldPlayerYRot = yRot
        }
        return mouseBasedViewVector
    }

    fun getMouseBasedViewVector(minecraft: Minecraft, xpos: Double, ypos: Double): Vector3d {
        val winWidth: Int = minecraft.window.width
        val winHeight: Int = minecraft.window.height
        val resultingViewBuffer = FloatBuffer.allocate(3)
        val modelViewBuffer: FloatBuffer = getModelViewMatrix(minecraft)
        val projectionBuffer: FloatBuffer = getProjectionMatrix(minecraft)
        val viewport: IntBuffer = getViewport(winWidth, winHeight)
        GLU.gluUnProject(
            xpos.toFloat(), (winHeight.toDouble() - ypos).toFloat(), 1.0f,
            modelViewBuffer, projectionBuffer, viewport, resultingViewBuffer
        )
        return calculateResultingVector(resultingViewBuffer)
    }

    private fun calculateResultingVector(res: FloatBuffer): Vector3d {
        val v = Vector3f(res[0], res[1], res[2])
        v.normalize()
        return Vector3d(v.x().toDouble(), v.y().toDouble(), v.z().toDouble())
    }

    private fun getModelViewMatrix(minecraft: Minecraft): FloatBuffer {
        val modelViewBuffer = FloatBuffer.allocate(16)
        val modelViewMatrix = RenderSystem.getModelViewMatrix().copy()
        modelViewMatrix.store(modelViewBuffer)
        modelViewBuffer.rewind()
        return modelViewBuffer
    }

    private fun getProjectionMatrix(minecraft: Minecraft): FloatBuffer {
        val projectionBuffer = FloatBuffer.allocate(16)
        val projectionMatrix = RenderSystem.getProjectionMatrix().copy()
        projectionMatrix.store(projectionBuffer)
        projectionBuffer.rewind()
        return projectionBuffer
    }

    private fun getViewport(winWidth: Int, winHeight: Int): IntBuffer {
        val viewport = IntBuffer.allocate(4)
        viewport.put(0)
        viewport.put(0)
        viewport.put(winWidth)
        viewport.put(winHeight)
        viewport.rewind()
        return viewport
    }
}