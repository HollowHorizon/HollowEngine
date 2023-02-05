package ru.hollowhorizon.hollowstory.client.screen

import com.sun.javafx.geom.Vec3f
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GLAllocation
import net.minecraft.util.math.vector.Vector3d
import net.minecraft.util.math.vector.Vector3f
import ru.hollowhorizon.hollowstory.client.ClientEvents
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.abs

object CameraHelper {
    private var mouseBasedViewVector = Vector3d.ZERO
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
        return Vector3d(v)
    }

    private fun getModelViewMatrix(minecraft: Minecraft): FloatBuffer {
        val modelViewBuffer = GLAllocation.createFloatBuffer(16)
        val modelViewMatrix = ClientEvents.VIEW_MAT.copy()
        modelViewMatrix.store(modelViewBuffer)
        modelViewBuffer.rewind()
        return modelViewBuffer
    }

    private fun getProjectionMatrix(minecraft: Minecraft): FloatBuffer {
        val projectionBuffer = GLAllocation.createFloatBuffer(16)
        ClientEvents.PROJ_MAT.store(projectionBuffer)
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