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

import imgui.ImDrawList
import imgui.ImGui
import imgui.ImVec2
import imgui.ImVec4
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor
import imgui.type.ImBoolean
import imgui.type.ImInt
import kotlin.math.abs

object Sequentity {
    private var initialTime = 0
    var initialEventTime = 0
    var initialEventLength = 0
    val indicator_size = ImVec2(9.0f, 9.0f)

    fun draw(registry: Registry<State, Track>) {
        val state = registry.state

        state.pan.x = transition(state.pan.x, state.targetPan.x, CommonTheme.transitionSpeed, 1.0f)
        state.pan.y = transition(state.pan.y, state.targetPan.y, CommonTheme.transitionSpeed, 1.0f)
        state.zoom.x = transition(state.zoom.x, state.targetZoom.x, CommonTheme.transitionSpeed)
        state.zoom.y = transition(state.zoom.y, state.targetZoom.y, CommonTheme.transitionSpeed)

        val painter = ImGui.getWindowDrawList()
        val titlebarHeight = 0f //ImGui.getFontSize() + ImGui.getStyle().framePaddingY * 2 // default: 24
        val windowSize = ImGui.getWindowSize()
        val windowPos = ImGui.getWindowPos() + ImVec2(0.0f, titlebarHeight)
        val padding = ImVec2(7.0f, 2.0f)

        val x = windowPos.clone()
        val y = windowPos.clone() + ImVec2(0.0f, TimelineTheme.height)
        val z = windowPos.clone() + ImVec2(0f, 0.0f)
        val w = windowPos.clone() + ImVec2(0f, TimelineTheme.height)

        val zoom = state.zoom.x / state.stride
        val stride = state.stride * 5  // How many frames to skip drawing
        val minTime = (state.range.x / stride).toInt()
        val maxTime = (state.range.y / stride).toInt()

        val multiplier = zoom / stride

        val timeToPx = { time: Float -> time * multiplier }
        val pxToTime = { px: Float -> px / multiplier }

        /**
         * Draw
         *
         */
        painter.editorBackground(w, windowPos, windowSize)
        painter.verticalGrid(minTime, maxTime, zoom, windowSize, state, w)

        fun point(time: Float) {
            val pos = ImVec2(timeToPx(time), 0f)

            val cursor = ImVec2(w.x + state.pan.x, w.y + state.pan.y)
            val cursorPos = cursor.clone() + pos - ImGui.getWindowPos()
            val size = timeToPx(1f)
            painter.addCircleFilled(
                cursorPos.x,
                cursorPos.y,
                size,
                ImGui.colorConvertFloat4ToU32(0.2f, 0.63f, 0.92f, 1f),
                4
            )
            if (ImGui.isMouseHoveringRect(
                    cursorPos.x - size / 2,
                    cursorPos.y - size / 2,
                    cursorPos.x + size / 2,
                    cursorPos.y + size / 2
                )
            ) {
                painter.addCircle(cursorPos.x, cursorPos.y, size, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 1f), 4, 3f)
            }
        }
        point(10f)
        point(25f)
        point(80f)

        //painter.events(w, state, registry, windowSize, timeToPx, pxToTime)
        painter.timelineBackground(z, windowSize)
        painter.timeline(minTime, maxTime, zoom, stride, state, z)
        painter.range(w, state, zoom, stride, windowSize)

        // Can intercept mouse events
        var hoveringBackground = true
        val rangeX = ImInt(state.range.x.toInt())
        val rangeY = ImInt(state.range.y.toInt())
        painter.timeIndicator(
            rangeX,
            TimelineTheme.start_time.imVec4,
            EditorTheme.start_time.imVec4,
            zoom,
            stride,
            windowSize,
            state,
            z,
            0
        )
        if (ImGui.isItemHovered()) hoveringBackground = false
        painter.timeIndicator(
            rangeY,
            TimelineTheme.end_time.imVec4,
            EditorTheme.end_time.imVec4,
            zoom,
            stride,
            windowSize,
            state,
            z,
            1
        )
        if (ImGui.isItemHovered()) hoveringBackground = false
        painter.timeIndicator(
            state.currentTime,
            TimelineTheme.current_time.imVec4,
            EditorTheme.current_time.imVec4,
            zoom,
            stride,
            windowSize,
            state,
            z,
            2
        )
        if (ImGui.isItemHovered()) hoveringBackground = false

        state.range.x = rangeX.get().toFloat()
        state.range.y = rangeY.get().toFloat()

        //painter.listerBackground(y, windowSize)
        //painter.lister(y, state, registry, padding)
        //painter.crossBackground(x)

        /**
         * User Input
         *
         */
        if (hoveringBackground) {
            ImGui.setCursorPos(0.0f, titlebarHeight)
            ImGui.invisibleButton("##mpan", ListerTheme.width, TimelineTheme.height)

            if (ImGui.isWindowHovered() && ImGui.getIO().getMouseDown(1)) ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
            val panM = (ImGui.isWindowHovered() && ImGui.getIO().getMouseDown(1))

            ImGui.setCursorPos(ListerTheme.width, titlebarHeight)
            ImGui.invisibleButton("##pan[0]", windowSize.x, TimelineTheme.height)

            if (ImGui.isItemHovered()) ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW)
            val panH = ImGui.isItemActive()

            when {
                panM -> {
                    state.targetPan.x += ImGui.getIO().mouseDelta.x
                    state.targetPan.y += ImGui.getIO().mouseDelta.y
                    val posY = state.targetPan.y - ImGui.getWindowPos().y - timeToPx(1f)
                    if (posY > 0) state.targetPan.y = ImGui.getWindowPos().y + timeToPx(1f)
                }

                panH -> state.targetPan.x += ImGui.getIO().mouseDelta.x
            }

            val wheel = ImGui.getIO().mouseWheel
            if (wheel != 0.0f && ImGui.getIO().keyAlt) {
                if (ImGui.getIO().keyCtrl) {
                    state.targetZoom.y += wheel * 10f
                    state.targetZoom.y = state.targetZoom.y.coerceIn(40f, 250f)
                } else {
                    state.targetZoom.x += wheel * 10f
                    state.targetZoom.x = state.targetZoom.x.coerceIn(90f, 500f)
                }
            }
        }
    }

    private fun ImDrawList.range(w: ImVec2, state: State, zoom: Float, stride: Int, windowSize: ImVec2) {
        val cursor = ImVec2(w.x, w.y)
        val rangeCursorStart = ImVec2(state.range.x * zoom / stride + state.pan.x, TimelineTheme.height)
        val rangeCursorEnd = ImVec2(state.range.y * zoom / stride + state.pan.x, TimelineTheme.height)

        addRectFilled(
            cursor,
            cursor.clone() + ImVec2(rangeCursorStart.x, windowSize.y),
            ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.3f)
        )

        addRectFilled(
            cursor.clone() + ImVec2(rangeCursorEnd.x, 0.0f),
            cursor.clone() + ImVec2(windowSize.x, windowSize.y),
            ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.3f)
        )
    }

    private fun ImDrawList.timeIndicator(
        time: ImInt,
        cursorColor: ImVec4,
        lineColor: ImVec4,
        zoom: Float,
        stride: Int,
        windowSize: ImVec2,
        state: State,
        z: ImVec2,
        id: Int,
    ) {
        var xMin = time.get() * zoom / stride
        var xMax = 0.0f
        var yMin = TimelineTheme.height
        var yMax = windowSize.y

        xMin += z.x + state.pan.x
        xMax += z.x + state.pan.x
        yMin += z.y
        yMax += z.y

        addLine(ImVec2(xMin, yMin), ImVec2(xMin, yMax), lineColor, 2.0f)

        val size = ImVec2(10.0f, 20.0f)
        val topPos = ImVec2(xMin, yMin)

        ImGui.pushID(id)
        val pos = topPos.clone() - ImVec2(size.x, size.y) - ImGui.getWindowPos()
        ImGui.setCursorPos(pos.x, pos.y)
        ImGui.setItemAllowOverlap()
        ImGui.invisibleButton("##indicator", size.x * 2.0f, size.y * 2.0f)
        ImGui.popID()

        if (ImGui.isItemActivated()) initialTime = time.get()

        var color = cursorColor
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW)
            color = ImVec4(color.x * 1.2f, color.y * 1.2f, color.z * 1.2f, color.w * 1.2f)
        }

        if (ImGui.isItemActive()) {
            var new = (initialTime + ImGui.getMouseDragDeltaX() / (zoom / stride)).toInt()


            if (TimelineTheme.start_time.imVec4 == color) new = new.coerceAtLeast(0)
            if (TimelineTheme.current_time.imVec4 == color) new = new.coerceIn(0, state.range.y.toInt())

            time.set(new)
        }

        val points = arrayOf(
            topPos.clone(),
            topPos.clone() - ImVec2(-size.x, size.y / 2.0f),
            topPos.clone() - ImVec2(-size.x, size.y),
            topPos.clone() - ImVec2(size.x, size.y),
            topPos.clone() - ImVec2(size.x, size.y / 2.0f)
        )

        val shadow1 = Array(5) {
            points[it].clone() + ImVec2(1.0f, 1.0f)
        }
        val shadow2 = Array(5) {
            points[it].clone() + ImVec2(3.0f, 3.0f)
        }
        addConvexPolyFilled(shadow1, 5, CommonTheme.shadow)
        addConvexPolyFilled(shadow2, 5, CommonTheme.shadow)
        addConvexPolyFilled(points, 5, ImGui.colorConvertFloat4ToU32(color.x, color.y, color.z, color.w))
        addPolyline(points, 5, color * 1.25f, 1.0f)
        addLine(
            topPos.clone() - ImVec2(2.0f, size.y * 0.3f),
            topPos.clone() - ImVec2(2.0f, size.y * 0.8f),
            EditorTheme.accent_dark
        )
        addLine(
            topPos.clone() - ImVec2(-2.0f, size.y * 0.3f),
            topPos.clone() - ImVec2(-2.0f, size.y * 0.8f),
            EditorTheme.accent_dark
        )
    }
}

private fun ImDrawList.crossBackground(x: ImVec2) {
    addRectFilled(x, x.clone() + ImVec2(ListerTheme.width + 1, TimelineTheme.height), ListerTheme.background)

    // Border
    addLine(
        x.clone() + ImVec2(ListerTheme.width, 0.0f),
        x.clone() + ImVec2(ListerTheme.width, TimelineTheme.height),
        CommonTheme.dark,
        CommonTheme.borderWidth
    )

    addLine(
        x.clone() + ImVec2(0.0f, TimelineTheme.height),
        x.clone() + ImVec2(ListerTheme.width + 1, TimelineTheme.height),
        CommonTheme.dark,
        CommonTheme.borderWidth
    )
}

private fun ImDrawList.verticalGrid(
    minTime: Int,
    maxTime: Int,
    zoom: Float,
    windowSize: ImVec2,
    state: State,
    w: ImVec2,
) {
    for (time in minTime..maxTime) {
        var xMin = time * zoom
        var xMax = 0.0f
        var yMin = 0.0f
        var yMax = windowSize.y

        xMin += w.x + state.pan.x
        xMax += w.x + state.pan.x
        yMin += w.y
        yMax += w.y

        addLine(ImVec2(xMin, yMin), ImVec2(xMin, yMax), EditorTheme.dark)

        if (time == maxTime) break

        for (j in 0 until 5 - 1) {
            val innerSpacing = zoom / 5
            val subline = innerSpacing * (j + 1)
            addLine(ImVec2(xMin + subline, yMin), ImVec2(xMin + subline, yMax), EditorTheme.mid)
        }
    }
}

private fun ImDrawList.listerBackground(y: ImVec2, windowSize: ImVec2) {
    if (CommonTheme.bling) {
        // Drop Shadow
        addRectFilled(
            y,
            y.clone() + ImVec2(ListerTheme.width + 3.0f, windowSize.y),
            ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.1f)
        )
        addRectFilled(
            y,
            y.clone() + ImVec2(ListerTheme.width + 2.0f, windowSize.y),
            ImGui.colorConvertFloat4ToU32(0.0f, 0.0f, 0.0f, 0.2f)
        )
    }

    // Fill
    addRectFilled(
        y, y.clone() + ImVec2(ListerTheme.width, windowSize.y),
        ImGui.getStyle().getColor(ImGuiCol.TitleBg)
    )

    // Border
    addLine(
        y.clone() + ImVec2(ListerTheme.width, 0.0f),
        y.clone() + ImVec2(ListerTheme.width, windowSize.y),
        CommonTheme.dark, CommonTheme.borderWidth
    )
}

private fun ImDrawList.timelineBackground(z: ImVec2, windowSize: ImVec2) {
    if (CommonTheme.bling) {
        // Drop Shadow
        addRectFilled(
            z.clone(), z.clone() + ImVec2(windowSize.x, TimelineTheme.height + 3.0f),
            ImVec4(0.0f, 0.0f, 0.0f, 0.1f)
        )
        addRectFilled(
            z.clone(), z.clone() + ImVec2(windowSize.x, TimelineTheme.height + 2.0f),
            ImVec4(0.0f, 0.0f, 0.0f, 0.2f)
        )
    }

    // Fill
    addRectFilled(
        z.clone(), z.clone() + ImVec2(windowSize.x, TimelineTheme.height),
        TimelineTheme.background
    )

    // Border
    addLine(
        z.clone() + ImVec2(0.0f, TimelineTheme.height),
        z.clone() + ImVec2(windowSize.x, TimelineTheme.height),
        CommonTheme.dark,
        CommonTheme.borderWidth
    )
}

private fun ImDrawList.editorBackground(w: ImVec2, windowPos: ImVec2, windowSize: ImVec2) {
    addRectFilled(
        w,
        w.clone() + windowPos + windowSize,
        EditorTheme.background
    )
}

private fun ImDrawList.timeline(minTime: Int, maxTime: Int, zoom: Float, stride: Int, state: State, z: ImVec2) {
    for (time in minTime..maxTime) {
        var xMin = time * zoom
        var xMax = 0.0f
        var yMin = 0.0f
        var yMax = TimelineTheme.height - 1

        xMin += z.x + state.pan.x
        xMax += z.x + state.pan.x
        yMin += z.y
        yMax += z.y

        addLine(ImVec2(xMin, yMin), ImVec2(xMin, yMax), TimelineTheme.dark)
        addText(
            ImGui.getFont(),
            ImGui.getFontSize() * 0.85f,
            ImVec2(xMin + 5.0f, yMin),
            TimelineTheme.text,
            (time * stride).toString()
        )

        if (time == maxTime) break

        for (j in 0 until 5 - 1) {
            val innerSpacing = zoom / 5
            val subline = innerSpacing * (j + 1)
            addLine(
                ImVec2(xMin + subline, yMin + (TimelineTheme.height * 0.5f)),
                ImVec2(xMin + subline, yMax),
                TimelineTheme.mid
            )
        }
    }
}

fun solo(registry: Registry<State, Track>) {
    var anySolo = false
    registry.view().forEach { track ->
        if (track.solo.get()) anySolo = true
        track.notsoloed = false
    }

    if (anySolo) registry.view().forEach { track ->
        track.notsoloed = !track.solo.get()
    }
}


fun transition(current: Float, target: Float, velocity: Float, epsilon: Float = 0.1f): Float {
    var mCurrent = current
    val delta = target - current

    // Prevent transitions between too small values
    // (Especially damaging to fonts)
    if (abs(delta) < epsilon) mCurrent = target

    mCurrent += delta * velocity
    return mCurrent
}

class State {
    val currentTime = ImInt(0)
    val range = ImVec2(0f, 100f)

    var selectedEvent: Event? = null
    var selectedTrack: Track? = null
    var selectedChannel: Channel? = null

    // Visual
    val zoom = ImVec2(250.0f, 20.0f)
    val pan = ImVec2(8.0f, 8.0f)
    val stride = 2

    // Transitions
    val targetZoom = ImVec2(200.0f, 20.0f)
    val targetPan = ImVec2(15.0f, 20.0f)
}

class Track {
    var label = "Untitled track"
    val color = hsv(0.66f, 0.5f, 1.0f)
    val solo = ImBoolean(false)
    val mute = ImBoolean(false)
    val channels = HashMap<EventType, Channel>()
    internal var notsoloed = false
}

class Channel {
    var label = "Untitled channel"
    var color = hsv(0.33f, 0.5f, 1.0f)
    val type = EventType.MOVE
    val events = ArrayList<Event>()
}

class Event {
    var time = 0
    var length = 0
    val color = hsv(0f, 0f, 1f)
    val type = EventType.MOVE
    var enabled = true
    var removed = false
    val scale = 1.0f
    var height = 0.0f
    var thickness = 0.0f
}

enum class EventType {
    MOVE, ROTATE, SCALE
}

object CommonTheme {
    val dark = hsv(0.0f, 0.0f, 0.3f)
    val shadow = hsva(0.0f, 0.0f, 0.0f, 0.1f)

    var bling = true
    var tintTrack = true

    var borderWidth = 1.0f
    var trackHeight = 25.0f
    var transitionSpeed = 0.2f
}

object TimelineTheme {
    val background = hsv(0.0f, 0.0f, 0.250f)
    val text = hsv(0.0f, 0.0f, 0.850f)
    val dark = hsv(0.0f, 0.0f, 0.322f)
    val mid = hsv(0.0f, 0.0f, 0.314f)
    val start_time = hsv(0.33f, 0.0f, 0.50f)
    val current_time = hsv(0.57f, 0.62f, 0.99f)
    val end_time = hsv(0.0f, 0.0f, 0.40f)

    var height = 40.0f
}

object ListerTheme {
    val background = hsv(0.0f, 0.0f, 0.188f)
    val text = hsv(0.0f, 0.0f, 0.850f)
    var width = 220.0f
    var buttons_width = 90.0f
}

object EditorTheme {
    val background = hsv(0.0f, 0.00f, 0.651f)
    val text = hsv(0.0f, 0.00f, 0.950f)
    val mid = hsv(0.0f, 0.00f, 0.600f)
    val dark = hsv(0.0f, 0.00f, 0.498f)
    val accent_dark = hsva(0.0f, 0.0f, 0.0f, 0.1f)
    val selection = hsv(0.0f, 0.0f, 1.0f)
    val outline = hsv(0.0f, 0.0f, 0.1f)
    val head_tail_hover = hsva(0.0f, 0.0f, 1.0f, 0.25f)

    val start_time = hsv(0.33f, 0.0f, 0.25f)
    val current_time = hsv(0.6f, 0.5f, 0.5f)
    val end_time = hsv(0.0f, 0.0f, 0.25f)

    var radius = 0.0f
    var spacing = 1.0f
    val head_tail_handle_width = ImVec2(10.0f, 100.0f)
    var active_clip_raise = 5.0f
    var active_clip_raise_shadow_movement = 0.25f

}

class Registry<K, V>(val state: K) {

    val values = ArrayList<V>()

    fun view(): List<V> {
        return values
    }
}