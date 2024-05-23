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

package ru.hollowhorizon.hollowengine.client.gui.sequencer

import imgui.ImDrawList
import imgui.ImGui
import imgui.ImVec2
import imgui.ImVec4
import imgui.flag.ImDrawFlags
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiMouseCursor
import imgui.type.ImBoolean
import ru.hollowhorizon.hollowengine.client.gui.*
import kotlin.math.max
import kotlin.math.min

private fun ImDrawList.header(track: Track, cursor: ImVec2, windowSize: ImVec2, w: ImVec2) {
    val size = ImVec2(windowSize.x, CommonTheme.trackHeight)

    addRectFilled(
        ImVec2(w.x, cursor.y),
        ImVec2(w.x, cursor.y) + size,
        EditorTheme.background
    )

    if (CommonTheme.tintTrack) {
        // Tint empty area with the track color
        addRectFilled(
            ImVec2(w.x, cursor.y),
            ImVec2(w.x, cursor.y) + size,
            track.color.imVec4.apply { this.w = 0.1f }
        )
    }

    addRect(
        ImVec2(w.x, cursor.y),
        ImVec2(w.x, cursor.y) + size,
        EditorTheme.mid
    )

    cursor.y += size.y
}

fun ImDrawList.events(w: ImVec2, state: State, registry: Registry<State, Track>, windowSize: ImVec2, timeToPx: (Float) -> Float, pxToTime: (Float) -> Float) {
    val cursor = ImVec2(w.x + state.pan.x, w.y + state.pan.y)

    registry.view().forEach { track ->
        header(track, cursor, windowSize, w)

        // Give each event a unique ImGui ID
        var eventCount = 0

        for ((_, channel) in track.channels) {
            for (event in channel.events) {
                val pos = ImVec2(timeToPx(event.time.toFloat()), 0.0f)
                val size = ImVec2(timeToPx(event.length.toFloat()), state.zoom.y)

                val headTailSize =
                    ImVec2(
                        min(
                            EditorTheme.head_tail_handle_width.y,
                            max(EditorTheme.head_tail_handle_width.x, size.x / 6)
                        ),
                        size.y
                    )
                val tailPos = ImVec2(pos.x + size.x - headTailSize.x, pos.y)
                val bodyPos = ImVec2(pos.x + headTailSize.x, pos.y)
                val bodySize = ImVec2(size.x - 2 * headTailSize.x, size.y)

                // Transitions
                var targetHeight = 0.0f



                ImGui.pushID(track.label)
                ImGui.pushID(eventCount)

                /* Event Head Start */
                val cursorPos = cursor.clone() + pos - ImGui.getWindowPos()
                ImGui.setCursorPos(cursorPos.x, cursorPos.y)
                ImGui.setItemAllowOverlap()
                ImGui.invisibleButton("##event_head", headTailSize.x, headTailSize.y)

                val headHovered = ImGui.isItemHovered() || ImGui.isItemActive()

                if (ImGui.isItemActivated()) {
                    Sequentity.initialEventTime = event.time
                    Sequentity.initialEventLength = event.length
                    state.selectedEvent = event
                }

                if (!ImGui.getIO().keyAlt && ImGui.isItemActive()) {
                    val delta = ImGui.getMouseDragDelta().x
                    event.time = Sequentity.initialEventTime + pxToTime(delta).toInt()
                    event.length = Sequentity.initialEventLength - pxToTime(delta).toInt()
                    event.removed = (event.time > state.range.y || event.time + event.length < state.range.x)

                    event.enabled = !event.removed
                    targetHeight = EditorTheme.active_clip_raise / 2
                }
                /* Event Head End */

                /* Event Tail Start */
                cursorPos.set(cursor.clone() + tailPos - ImGui.getWindowPos())
                ImGui.setCursorPos(cursorPos.x, cursorPos.y)
                ImGui.setItemAllowOverlap()
                ImGui.invisibleButton("##event_tail", headTailSize.x, headTailSize.y)

                val tailHovered = ImGui.isItemHovered() || ImGui.isItemActive()

                if (ImGui.isItemActivated()) {
                    Sequentity.initialEventTime = event.time
                    Sequentity.initialEventLength = event.length
                    state.selectedEvent = event
                }

                if (!ImGui.getIO().keyAlt && ImGui.isItemActive()) {
                    val delta = ImGui.getMouseDragDelta().x
                    event.length = Sequentity.initialEventLength + pxToTime(delta).toInt()
                    event.removed = (event.time > state.range.y || event.time + event.length < state.range.x)

                    event.enabled = !event.removed
                    targetHeight = EditorTheme.active_clip_raise / 2
                }
                /* Event Tail End */

                /* Event Body Start */
                cursorPos.set(cursor.clone() + bodyPos - ImGui.getWindowPos())
                ImGui.setCursorPos(cursorPos.x, cursorPos.y)
                ImGui.setItemAllowOverlap()
                ImGui.invisibleButton("##event_body", bodySize.x, bodySize.y)

                ImGui.popID()
                ImGui.popID()

                var color = channel.color

                if (!event.enabled || track.mute.get() || track.notsoloed) {
                    color = hsv(0.0f, 0.0f, 0.5f)
                } else if (ImGui.isItemHovered() || ImGui.isItemActive()) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.Hand)
                }

                // User Input
                if (ImGui.isItemActivated()) {
                    Sequentity.initialEventTime = event.time
                    Sequentity.initialEventLength = event.length
                    state.selectedEvent = event
                }

                if (!ImGui.getIO().keyAlt && ImGui.isItemActive()) {
                    val delta = ImGui.getMouseDragDeltaX()
                    event.time = Sequentity.initialEventTime + pxToTime(delta).toInt()
                    event.removed = (event.time > state.range.y || event.time + event.length < state.range.x)
                    event.enabled = !event.removed
                    targetHeight = EditorTheme.active_clip_raise
                }
                /* Event Body End */

                event.height = transition(event.height, targetHeight, CommonTheme.transitionSpeed)
                pos.set(pos.x - event.height, pos.y - event.height)
                tailPos.set(tailPos.x - event.height, tailPos.y - event.height)

                val shadow = 2
                val shadowMovement = event.height * (1.0f + EditorTheme.active_clip_raise_shadow_movement)
                addRectFilled(
                    cursor.clone() + pos + shadow + shadowMovement,
                    cursor.clone() + pos + size + shadow + shadowMovement,
                    hsva(0.0f, 0.0f, 0.0f, 0.3f), EditorTheme.radius
                )

                addRectFilled(
                    cursor.clone() + pos,
                    cursor.clone() + pos + size,
                    color
                )

                val coli = ImVec4()
                ImGui.colorConvertU32ToFloat4(color, coli)
                // Add a dash to the bottom of each event.
                addRectFilled(
                    cursor.clone() + pos + ImVec2(0.0f, size.y - 5.0f),
                    cursor.clone() + pos + size,
                    coli * 0.8f
                )

                if (ImGui.isItemHovered() || ImGui.isItemActive() || headHovered || tailHovered || state.selectedEvent == event) {
                    addRect(
                        cursor.clone() + pos + event.thickness * 0.25f,
                        cursor.clone() + pos + size - event.thickness * 0.25f,
                        EditorTheme.selection,
                        EditorTheme.radius,
                        ImDrawFlags.RoundCornersAll,
                        event.thickness
                    )
                } else {
                    addRect(
                        cursor.clone() + pos + event.thickness,
                        cursor.clone() + pos + size - event.thickness,
                        EditorTheme.outline, EditorTheme.radius, 0, 0f
                    )
                }

                if (headHovered) {
                    addRectFilled(
                        cursor.clone() + pos,
                        cursor.clone() + pos + headTailSize,
                        EditorTheme.head_tail_hover, EditorTheme.radius
                    )
                    ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW)
                }

                if (tailHovered) {
                    addRectFilled(
                        cursor.clone() + tailPos,
                        cursor.clone() + tailPos + headTailSize,
                        EditorTheme.head_tail_hover, EditorTheme.radius
                    )
                    ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW)
                }


                if (event.enabled && (ImGui.isItemHovered() || ImGui.isItemActive() || headHovered || tailHovered)) {
                    if (event.length > 5.0f) {
                        addText(
                            ImGui.getFont(),
                            ImGui.getFontSize() * 0.85f,
                            cursor.clone() + pos + ImVec2(3.0f + event.thickness, 0.0f),
                            EditorTheme.text,
                            (event.time).toString()
                        )
                    }

                    if (event.length > 30.0f) {
                        addText(
                            ImGui.getFont(),
                            ImGui.getFontSize() * 0.85f,
                            cursor.clone() + pos + ImVec2(size.x - 20.0f, 0.0f),
                            EditorTheme.text,
                            (event.length).toString()
                        )
                    }
                }


                event.enabled = !(event.time + event.length < 0f || event.time > state.range.y)


                eventCount++
            }

            // Next event type
            cursor.y += state.zoom.y + EditorTheme.spacing
        }

        // Next track
        cursor.y += 2f
    }
}

fun ImDrawList.lister(y: ImVec2, state: State, registry: Registry<State, Track>, padding: ImVec2) {
    val cursor = ImVec2(y.x, y.y + state.pan.y)

    registry.view().forEach { track ->
        val textSize = ImGui.calcTextSize(track.label)
        val pos = ImVec2(
            ListerTheme.width - textSize.x - padding.x - padding.x,
            CommonTheme.trackHeight / 2.0f - textSize.y / 2.0f
        )

        addRectFilled(
            cursor.clone() + ImVec2(ListerTheme.width - 5.0f, 0.0f),
            cursor.clone() + ImVec2(ListerTheme.width, CommonTheme.trackHeight),
            track.color
        )

        addText(
            cursor.clone() + pos, ListerTheme.text, track.label
        )

        val cursorPos = cursor.clone() + ImVec2(padding.x, 0.0f) - ImGui.getWindowPos()
        ImGui.setCursorPos(cursorPos.x, cursorPos.y)
        ImGui.pushID(track.label)
        button("m", track.mute, ImVec2(CommonTheme.trackHeight, CommonTheme.trackHeight))
        ImGui.sameLine()

        if (button("s", track.solo, ImVec2(CommonTheme.trackHeight, CommonTheme.trackHeight))) {
            solo(registry)
        }

        ImGui.popID()

        val trackCorner = cursor.clone()
        cursor.y += CommonTheme.trackHeight

        for ((_, channel) in track.channels) {

            val indicatorPos = ImVec2(
                ListerTheme.width - Sequentity.indicator_size.x - padding.x,
                state.zoom.y * 0.5f - Sequentity.indicator_size.y * 0.5f
            )

            addRectFilled(
                cursor.clone() + indicatorPos,
                cursor.clone() + indicatorPos + Sequentity.indicator_size,
                channel.color
            )

            val colori = ImVec4()
            ImGui.colorConvertU32ToFloat4(channel.color, colori)
            addRect(
                cursor.clone() + indicatorPos,
                cursor.clone() + indicatorPos + Sequentity.indicator_size,
                colori * 1.25f
            )

            val labelSize = ImGui.calcTextSize(channel.label) * 0.85f
            val textPos = ImVec2(
                ListerTheme.width - labelSize.x - padding.x - Sequentity.indicator_size.x - padding.x,
                state.zoom.y * 0.5f - labelSize.y * 0.5f
            )

            addText(
                ImGui.getFont(),
                labelSize.y,
                cursor.clone() + textPos,
                ListerTheme.text, channel.label
            )

            // Next channel
            cursor.y += state.zoom.y + EditorTheme.spacing
        }

        // Next track
        cursor.y += padding.y

        // Visualise mute and solo state
        if (track.mute.get() || track.notsoloed) {
            val faded = ImVec4().apply { ImGui.colorConvertU32ToFloat4(ListerTheme.background, this) }
            faded.w = 0.8f

            addRectFilled(
                trackCorner.clone() + ImVec2(ListerTheme.buttons_width, 0.0f),
                trackCorner.clone() + ImVec2(ListerTheme.width, cursor.y),
                faded
            )
        }
    }
}

fun button(label: String, checked: ImBoolean, size: ImVec2): Boolean {
    if (checked.get()) {
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.25f))
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.colorConvertFloat4ToU32(0f, 0f, 0f, 0.15f))
    } else {
        ImGui.pushStyleColor(ImGuiCol.Button, ImGui.colorConvertFloat4ToU32(1f, 1f, 1f, 0.1f))
    }

    val pressed = ImGui.button(label, size.x, size.y)

    if (checked.get()) ImGui.popStyleColor(2)
    else ImGui.popStyleColor(1)

    if (pressed) checked.set(checked.get() xor true)

    return pressed
}