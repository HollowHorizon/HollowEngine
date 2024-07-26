/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.client.gui

import imgui.ImGui
import imgui.extension.imguizmo.ImGuizmo
import imgui.extension.imguizmo.flag.Mode
import imgui.extension.imguizmo.flag.Operation
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import org.lwjgl.glfw.GLFW.*
import java.awt.Desktop
import java.net.URI
import java.util.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

const val URL = "https://github.com/CedricGuillemet/ImGuizmo/tree/f7bbbe"

private const val CAM_DISTANCE = 8
private const val CAM_Y_ANGLE = 165f / 180f * Math.PI.toFloat()
private const val CAM_X_ANGLE = 32f / 180f * Math.PI.toFloat()
private const val FLT_EPSILON = 1.19209290E-07f

private val OBJECT_MATRICES = arrayOf(
    floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f
    ),
    floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        2f, 0f, 0f, 1f
    ),
    floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        2f, 0f, 2f, 1f
    ),
    floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 2f, 1f
    )
)

private val IDENTITY_MATRIX = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f
)

private val VIEW_MANIPULATE_SIZE = floatArrayOf(128f, 128f)

private val EMPTY = floatArrayOf(0f)

private val INPUT_CAMERA_VIEW = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    0f, 0f, 0f, 1f
)

private val INPUT_BOUNDS = floatArrayOf(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f)
private val INPUT_BOUNDS_SNAP = floatArrayOf(1f, 1f, 1f)

private val INPUT_SNAP_VALUE = floatArrayOf(1f, 1f, 1f)
private val INPUT_MATRIX_TRANSLATION = FloatArray(3)
private val INPUT_MATRIX_SCALE = FloatArray(3)
private val INPUT_MATRIX_ROTATION = FloatArray(3)

private val INPUT_FLOAT = ImFloat()

private val BOUNDING_SIZE = ImBoolean(false)
private val USE_SNAP = ImBoolean(false)

private var currentMode: Int = Mode.LOCAL
private var currentGizmoOperation = 0

private var boundSizingSnap = false
private var firstFrame = true

fun show(showImGuizmoWindow: ImBoolean) {
    ImGuizmo.beginFrame()

    if (ImGui.begin("ImGuizmo Demo", showImGuizmoWindow)) {
        ImGui.text("This a demo for ImGuizmo")

        ImGui.alignTextToFramePadding()
        ImGui.text("Repo:")
        ImGui.sameLine()
        if (ImGui.button(URL)) {
            try {
                Desktop.getDesktop().browse(URI(URL))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        ImGui.separator()

        if (firstFrame) {
            val eye = floatArrayOf(
                (cos(CAM_Y_ANGLE.toDouble()) * cos(CAM_X_ANGLE.toDouble()) * CAM_DISTANCE).toFloat(),
                (sin(CAM_X_ANGLE.toDouble()) * CAM_DISTANCE).toFloat(),
                (sin(CAM_Y_ANGLE.toDouble()) * cos(CAM_X_ANGLE.toDouble()) * CAM_DISTANCE).toFloat()
            )
            val at = floatArrayOf(0f, 0f, 0f)
            val up = floatArrayOf(0f, 1f, 0f)
            lookAt(eye, at, up, INPUT_CAMERA_VIEW)
            firstFrame = false
        }

        ImGui.text("Keybindings:")
        ImGui.text("T - Translate")
        ImGui.text("R - Rotate")
        ImGui.text("S - Scale")
        ImGui.separator()

        if (ImGuizmo.isUsing()) {
            ImGui.text("Using gizmo")
            if (ImGuizmo.isOver()) {
                ImGui.text("Over a gizmo")
            }
            if (ImGuizmo.isOver(Operation.TRANSLATE)) {
                ImGui.text("Over translate gizmo")
            } else if (ImGuizmo.isOver(Operation.ROTATE)) {
                ImGui.text("Over rotate gizmo")
            } else if (ImGuizmo.isOver(Operation.SCALE)) {
                ImGui.text("Over scale gizmo")
            }
        } else {
            ImGui.text("Not using gizmo")
        }

        editTransform(showImGuizmoWindow)
        ImGui.end()
    }
}

private fun editTransform(showImGuizmoWindow: ImBoolean) {
    if (ImGui.isKeyPressed(GLFW_KEY_T)) {
        currentGizmoOperation = Operation.TRANSLATE
    } else if (ImGui.isKeyPressed(GLFW_KEY_R)) {
        currentGizmoOperation = Operation.ROTATE
    } else if (ImGui.isKeyPressed(GLFW_KEY_S)) {
        currentGizmoOperation = Operation.SCALE
    } else if (ImGui.isKeyPressed(GLFW_KEY_LEFT_SHIFT)) {
        USE_SNAP.set(!USE_SNAP.get())
    }

    if (ImGuizmo.isUsing()) {
        ImGuizmo.decomposeMatrixToComponents(
            OBJECT_MATRICES[0],
            INPUT_MATRIX_TRANSLATION,
            INPUT_MATRIX_ROTATION,
            INPUT_MATRIX_SCALE
        )
    }

    ImGui.inputFloat3("Tr", INPUT_MATRIX_TRANSLATION, "%.3f", ImGuiInputTextFlags.ReadOnly)
    ImGui.inputFloat3("Rt", INPUT_MATRIX_ROTATION, "%.3f", ImGuiInputTextFlags.ReadOnly)
    ImGui.inputFloat3("Sc", INPUT_MATRIX_SCALE, "%.3f", ImGuiInputTextFlags.ReadOnly)

    if (ImGuizmo.isUsing()) {
        ImGuizmo.recomposeMatrixFromComponents(
            OBJECT_MATRICES[0],
            INPUT_MATRIX_TRANSLATION,
            INPUT_MATRIX_ROTATION,
            INPUT_MATRIX_SCALE
        )
    }

    if (currentGizmoOperation != Operation.SCALE) {
        if (ImGui.radioButton("Local", currentMode == Mode.LOCAL)) {
            currentMode = Mode.LOCAL
        }
        ImGui.sameLine()
        if (ImGui.radioButton("World", currentMode == Mode.WORLD)) {
            currentMode = Mode.WORLD
        }
    }

    ImGui.checkbox("Snap Checkbox", USE_SNAP)

    INPUT_FLOAT.set(INPUT_SNAP_VALUE[0])
    when (currentGizmoOperation) {
        Operation.TRANSLATE -> ImGui.inputFloat3("Snap Value", INPUT_SNAP_VALUE)
        Operation.ROTATE -> {
            ImGui.inputFloat("Angle Value", INPUT_FLOAT)
            val rotateValue = INPUT_FLOAT.get()
            Arrays.fill(INPUT_SNAP_VALUE, rotateValue) //avoiding allocation
        }

        Operation.SCALE -> {
            ImGui.inputFloat("Scale Value", INPUT_FLOAT)
            val scaleValue = INPUT_FLOAT.get()
            Arrays.fill(INPUT_SNAP_VALUE, scaleValue)
        }
    }
    ImGui.checkbox("Show Bound Sizing", BOUNDING_SIZE)

    if (BOUNDING_SIZE.get()) {
        if (ImGui.checkbox("BoundSizingSnap", boundSizingSnap)) {
            boundSizingSnap = !boundSizingSnap
        }
        ImGui.sameLine()
        ImGui.inputFloat3("Snap", INPUT_BOUNDS_SNAP)
    }

    ImGui.setNextWindowPos(
        ImGui.getMainViewport().posX + 100,
        ImGui.getMainViewport().posY + 100,
        ImGuiCond.Once
    )
    ImGui.setNextWindowSize(800f, 400f, ImGuiCond.Once)
    ImGui.begin("Gizmo", showImGuizmoWindow)
    ImGui.beginChild("prevent_window_from_moving_by_drag", 0f, 0f, false, ImGuiWindowFlags.NoMove)

    val aspect: Float = ImGui.getWindowWidth() / ImGui.getWindowHeight()
    val cameraProjection = perspective(27f, aspect, 0.1f, 100f)

    ImGuizmo.setOrthographic(false)
    ImGuizmo.setEnabled(true)
    ImGuizmo.setDrawList()

    val windowWidth: Float = ImGui.getWindowWidth()
    val windowHeight: Float = ImGui.getWindowHeight()
    ImGuizmo.setRect(ImGui.getWindowPosX(), ImGui.getWindowPosY(), windowWidth, windowHeight)

    ImGuizmo.drawGrid(INPUT_CAMERA_VIEW, cameraProjection, IDENTITY_MATRIX, 100)
    ImGuizmo.setId(0)
    ImGuizmo.drawCubes(INPUT_CAMERA_VIEW, cameraProjection, *OBJECT_MATRICES)

    when {
        USE_SNAP.get() && BOUNDING_SIZE.get() && boundSizingSnap -> {
            ImGuizmo.manipulate(
                INPUT_CAMERA_VIEW,
                cameraProjection,
                OBJECT_MATRICES[0],
                currentGizmoOperation,
                currentMode,
                INPUT_SNAP_VALUE,
                INPUT_BOUNDS,
                INPUT_BOUNDS_SNAP
            )
        }

        USE_SNAP.get() && BOUNDING_SIZE.get() -> {
            ImGuizmo.manipulate(
                INPUT_CAMERA_VIEW, cameraProjection,
                OBJECT_MATRICES[0], currentGizmoOperation, currentMode, INPUT_SNAP_VALUE, INPUT_BOUNDS
            )
        }

        BOUNDING_SIZE.get() && boundSizingSnap -> {
            ImGuizmo.manipulate(
                INPUT_CAMERA_VIEW, cameraProjection,
                OBJECT_MATRICES[0], currentGizmoOperation, currentMode, EMPTY, INPUT_BOUNDS, INPUT_BOUNDS_SNAP
            )
        }

        BOUNDING_SIZE.get() -> {
            ImGuizmo.manipulate(
                INPUT_CAMERA_VIEW, cameraProjection,
                OBJECT_MATRICES[0], currentGizmoOperation, currentMode, EMPTY, INPUT_BOUNDS
            )
        }

        USE_SNAP.get() -> {
            ImGuizmo.manipulate(
                INPUT_CAMERA_VIEW, cameraProjection,
                OBJECT_MATRICES[0], currentGizmoOperation, currentMode, INPUT_SNAP_VALUE
            )
        }

        else -> {
            ImGuizmo.manipulate(
                INPUT_CAMERA_VIEW,
                cameraProjection,
                OBJECT_MATRICES[0],
                currentGizmoOperation,
                currentMode
            )
        }
    }

    val viewManipulateRight: Float = ImGui.getWindowPosX() + windowWidth
    val viewManipulateTop: Float = ImGui.getWindowPosY()
    ImGuizmo.viewManipulate(
        INPUT_CAMERA_VIEW,
        CAM_DISTANCE.toFloat(),
        floatArrayOf(viewManipulateRight - 128, viewManipulateTop),
        VIEW_MANIPULATE_SIZE,
        0x10101010
    )

    ImGui.endChild()
    ImGui.end()
}

private fun perspective(fovY: Float, aspect: Float, near: Float, far: Float): FloatArray {
    val xmax: Float
    val ymax = (near * tan(fovY * Math.PI / 180.0f)).toFloat()
    xmax = ymax * aspect
    return frustum(-xmax, xmax, -ymax, ymax, near, far)
}

private fun frustum(left: Float, right: Float, bottom: Float, top: Float, near: Float, far: Float): FloatArray {
    val r = FloatArray(16)
    val temp = 2.0f * near
    val temp2 = right - left
    val temp3 = top - bottom
    val temp4 = far - near
    r[0] = temp / temp2
    r[1] = 0.0f
    r[2] = 0.0f
    r[3] = 0.0f
    r[4] = 0.0f
    r[5] = temp / temp3
    r[6] = 0.0f
    r[7] = 0.0f
    r[8] = (right + left) / temp2
    r[9] = (top + bottom) / temp3
    r[10] = (-far - near) / temp4
    r[11] = -1.0f
    r[12] = 0.0f
    r[13] = 0.0f
    r[14] = (-temp * far) / temp4
    r[15] = 0.0f
    return r
}

private fun cross(a: FloatArray, b: FloatArray): FloatArray {
    val r = FloatArray(3)
    r[0] = a[1] * b[2] - a[2] * b[1]
    r[1] = a[2] * b[0] - a[0] * b[2]
    r[2] = a[0] * b[1] - a[1] * b[0]
    return r
}

private fun dot(a: FloatArray, b: FloatArray): Float {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}

private fun normalize(a: FloatArray): FloatArray {
    val r = FloatArray(3)
    val il = (1f / (sqrt(dot(a, a).toDouble()) + FLT_EPSILON)).toFloat()
    r[0] = a[0] * il
    r[1] = a[1] * il
    r[2] = a[2] * il
    return r
}

private fun lookAt(eye: FloatArray, at: FloatArray, up: FloatArray, m16: FloatArray) {
    val x: FloatArray
    val z: FloatArray
    var tmp = FloatArray(3)

    tmp[0] = eye[0] - at[0]
    tmp[1] = eye[1] - at[1]
    tmp[2] = eye[2] - at[2]
    z = normalize(tmp)
    var y = normalize(up)

    tmp = cross(y, z)
    x = normalize(tmp)

    tmp = cross(z, x)
    y = normalize(tmp)

    m16[0] = x[0]
    m16[1] = y[0]
    m16[2] = z[0]
    m16[3] = 0.0f
    m16[4] = x[1]
    m16[5] = y[1]
    m16[6] = z[1]
    m16[7] = 0.0f
    m16[8] = x[2]
    m16[9] = y[2]
    m16[10] = z[2]
    m16[11] = 0.0f
    m16[12] = -dot(x, eye)
    m16[13] = -dot(y, eye)
    m16[14] = -dot(z, eye)
    m16[15] = 1.0f
}