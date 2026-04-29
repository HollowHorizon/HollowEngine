package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.clamp
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.UiVertexLayout.position
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.set
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.hollowhorizon.hollowengine.client.gui.timeline.BaseAnimTrack
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.timeline.AnimTrack
import ru.hollowhorizon.hollowengine.client.gui.timeline.Keyframe
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import kotlin.math.*

fun UiScope.TimelineArea(controller: TimelineController) {
    Box(Grow.Std, Grow.Std) {
        ScrollArea(
            state = controller.scrollState,
            isScrollableVertical = false,
            isScrollableHorizontal = false,
            containerModifier = {
                it.backgroundColor(colors.background)
                it.onClick {
                    controller.selectedKeyframes.clear()
                    controller.isWorkAreaSelected.set(false)
                }
                it.onWheelY { ev ->
                    if (KeyboardInput.isCtrlDown) {
                        val oldZoom = controller.pixelsPerSecond.value
                        val newZoom = (oldZoom + ev.pointer.scroll.y * 10f).clamp(10f, 500f)

                        val mouseX = ev.position.x
                        val viewX = Dp.fromPx(mouseX).value
                        val currentScrollX = controller.scrollState.xScrollDp.value

                        val timeUnderMouse = (currentScrollX + viewX) / oldZoom
                        val newScrollX = (timeUnderMouse * newZoom) - viewX

                        controller.pixelsPerSecond.set(newZoom)
                        controller.scrollState.xScrollDp.set(max(0f, newScrollX))
                        controller.scrollState.xScrollDpDesired.set(max(0f, newScrollX))

                    } else if (KeyboardInput.isShiftDown) {
                        controller.scrollState.scrollDpX(ev.pointer.scroll.y * -50f)
                    } else {
                        controller.scrollState.scrollDpY(ev.pointer.scroll.y * -50f)
                    }
                    ev.pointer.consume()
                }
                it.onWheelX { ev ->
                    controller.scrollState.scrollDpX(ev.pointer.scroll.x * -50f)
                    ev.pointer.consume()
                }
                it.onDrag { ev ->
                    if (ev.pointer.isMiddleButtonDown || ev.pointer.isLeftButtonDown) {
                        val dx = Dp.fromPx(ev.pointer.delta.x).value
                        val dy = Dp.fromPx(ev.pointer.delta.y).value
                        controller.scrollState.scrollDpX(-dx)
                        controller.scrollState.scrollDpY(-dy)
                        ev.pointer.consume()
                    }
                }
            }
        ) {
            val pxPerSec = controller.pixelsPerSecond.use()
            val allTracks = controller.getAllTracks()

            val maxKeyTime = allTracks.flatMap { it.getKeysAsList() }.maxOfOrNull { it.time } ?: 0f

            val endLimit = controller.workAreaEnd.use()
            val viewWidthPx = controller.scrollState.viewWidthDp.use().dp.px
            val scrollX = controller.scrollState.xScrollDp.use().dp.px

            val contentWidthPx = max(
                (endLimit + 5f) * pxPerSec,
                max((maxKeyTime + 5f) * pxPerSec, scrollX + viewWidthPx * 1.5f)
            )

            Column(width = Dp.fromPx(contentWidthPx), height = Grow.Std) {
                TimeRuler(pxPerSec, maxKeyTime, controller)

                controller.groups.use().forEach { group ->
                    Box(width = Grow.Std, height = 30.dp) {
                        modifier
                            .border(RectBorder(colors.secondaryVariant.withAlpha(0.2f), 1.dp))
                            .backgroundColor(Color("24272E"))
                    }

                    if (!group.isCollapsed.use()) {
                        val isGroupLocked = group.isLocked.use()
                        val isGroupVisible = group.isVisible.use()

                        group.tracks.forEach { track ->
                            if (track is AnimTrack<*>) {
                                val isTrackLocked = track.isLocked.use() || isGroupLocked
                                val isTrackVisible = track.isVisible.use() && isGroupVisible
                                TrackLane(track, pxPerSec, isTrackLocked, isTrackVisible, controller)
                            }
                        }
                    }
                }

                Box(Grow.Std, Grow.Std) {
                    modifier.onClick {
                        controller.selectedKeyframes.clear()
                        controller.isWorkAreaSelected.set(false)
                    }
                }
            }

            WorkAreaOverlay(pxPerSec, controller)
        }

        PlayheadOverlay(controller.pixelsPerSecond.use(), controller.scrollState, controller)
    }
}

@Suppress("UNCHECKED_CAST")
private fun UiScope.TrackLane(
    track: AnimTrack<*>,
    pxPerSec: Float,
    isLocked: Boolean,
    isVisible: Boolean,
    controller: TimelineController
) {
    Box(width = Grow.Std, height = 40.dp) {
        val borderColor = if (isLocked) Color("764713") else colors.secondaryVariant.withAlpha(0.2f)
        modifier.border(RectBorder(borderColor, 1.dp))

        val trackBgColor = when {
            isLocked -> Color("1A1A1D")
            !isVisible -> Color("1B1B1C")
            else -> Color("1E1F22")
        }

        fun insertKeyAtPointer(ev: PointerEvent): Keyframe<*>? {
            val t = (ev.position.x / pxPerSec).coerceAtLeast(0f)
            if (t <= controller.workAreaEnd.value) {
                val typedTrack = track as AnimTrack<Any?>
                val value = typedTrack.getInsertionValue(t)
                val newKey = Keyframe(t, value)
                typedTrack.keyframes.add(newKey)

                controller.selectedKeyframes.clear()
                controller.selectedKeyframes.add(newKey)
                controller.isWorkAreaSelected.set(false)
                surface.triggerUpdate()
                return newKey
            }
            return null
        }

        if (!isLocked) {
            modifier
                .onClick { ev ->
                    if (ev.pointer.leftButtonRepeatedClickCount == 1) {
                        controller.selectedKeyframes.clear()
                        controller.isWorkAreaSelected.set(false)
                    }
                    else if (ev.pointer.leftButtonRepeatedClickCount == 2) {
                        insertKeyAtPointer(ev)
                    }
                }
                .onDragStart { ev ->
                    if (ev.pointer.leftButtonRepeatedClickCount == 2 && ev.pointer.isLeftButtonDown) {
                        insertKeyAtPointer(ev)
                        val endKey = insertKeyAtPointer(ev)

                        if (endKey != null) {
                            controller.activeDragKeyframe = endKey
                            ev.pointer.consume()
                        }
                    } else {
                        ev.isConsumed = false
                    }
                }
                .onDrag { ev ->
                    controller.activeDragKeyframe?.let { key ->
                        val t = (ev.position.x / pxPerSec).clamp(0f, controller.workAreaEnd.value)
                        key.time = t
                        surface.triggerUpdate()
                        ev.pointer.consume()
                    }
                }
                .onDragEnd {
                    controller.activeDragKeyframe = null
                }
        }

        modifier.background(UiRenderer { node ->
            node.apply {
                val draw = getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                draw.localRect(0f, 0f, widthPx, heightPx, trackBgColor)
            }
        })

        val connAlpha = if (isVisible) 1f else 0.2f
        var connectionColor = track.color.withAlpha(connAlpha)
        if (isLocked) {
            connectionColor = connectionColor.mix(Color.BLACK, 0.5f)
        }
        val lineHeight = 2.dp

        val sortedKeys = track.keyframes.sortedBy { it.time }
        for (i in 0 until sortedKeys.size - 1) {
            val k1 = sortedKeys[i]
            val k2 = sortedKeys[i + 1]

            val x1 = k1.time * pxPerSec
            val w = (k2.time - k1.time) * pxPerSec

            if (w > 1f) {
                Box(width = Dp.fromPx(w), height = lineHeight) {
                    modifier
                        .margin(start = Dp.fromPx(x1))
                        .alignY(AlignmentY.Center)
                        .backgroundColor(connectionColor)
                }
            }
        }

        track.keyframes.forEach { key ->
            TimelineKeyframe(key, track, pxPerSec, isLocked, isVisible, controller)
        }
    }
}

private fun UiScope.TimelineKeyframe(
    keyframe: Keyframe<*>,
    track: BaseAnimTrack,
    pxPerSec: Float,
    isLocked: Boolean,
    isVisible: Boolean,
    controller: TimelineController
) {
    val isSelected = keyframe in controller.selectedKeyframes
    val containerSize = 40.dp
    val isHovered = remember(false)
    val centerPos = keyframe.time * pxPerSec

    val scale = animateFloatAsState(if (isSelected) 1.4f else 1f).use()

    val scrollX = controller.scrollState.xScrollDp.value * UiScale.measuredScale
    val viewW = controller.scrollState.viewWidthDp.value * UiScale.measuredScale

    if (centerPos < scrollX - 100 || centerPos > scrollX + viewW + 100) return

    Box {
        modifier
            .margin(start = Dp.fromPx(centerPos) - containerSize * 0.5f)
            .alignY(AlignmentY.Center)
            .size(containerSize, containerSize)
            .zLayer(if (isSelected) 5 else 0)

        if (!isLocked) {
            modifier
                .onEnter { isHovered.set(true) }
                .onExit { isHovered.set(false) }
                .onClick { ev ->
                    ev.pointer.consume()

                    if (ev.pointer.leftButtonRepeatedClickCount == 2) {
                        controller.duplicateKeyframe(track, keyframe)
                        surface.triggerUpdate()
                    } else {
                        if (KeyboardInput.isCtrlDown) {
                            if (isSelected) controller.selectedKeyframes.remove(keyframe) else controller.selectedKeyframes.add(keyframe)
                        } else {
                            controller.selectedKeyframes.clear()
                            controller.selectedKeyframes.add(keyframe)
                        }
                        controller.isWorkAreaSelected.set(false)
                    }
                }
                .onDragStart { ev ->
                    ev.pointer.consume()

                    if (KeyboardInput.isAltDown) {
                        val newKey = controller.duplicateKeyframe(track, keyframe)
                        controller.activeDragKeyframe = newKey
                    } else {
                        controller.activeDragKeyframe = keyframe
                        if (!isSelected && !KeyboardInput.isCtrlDown) {
                            controller.selectedKeyframes.clear()
                            controller.selectedKeyframes.add(keyframe)
                            controller.isWorkAreaSelected.set(false)
                        }
                    }
                }
                .onDrag { ev ->
                    controller.activeDragKeyframe?.let { k ->
                        val dt = ev.pointer.delta.x / pxPerSec
                        k.time = (k.time + dt).clamp(0f, controller.workAreaEnd.value)
                        surface.triggerUpdate()
                    }
                }
                .onDragEnd { controller.activeDragKeyframe = null }
        }

        var bgColor = track.color

        if (isLocked) {
            bgColor = bgColor.mix(Color.BLACK, 0.5f)
        } else if (!isVisible) {
            bgColor = bgColor.withAlpha(0.5f)
        }

        modifier.background(UiRenderer { node ->
            node.apply {
                val draw = getPlainBuilder()
                val cx = widthPx * 0.5f
                val cy = heightPx * 0.5f

                val r = 7.dp.px * scale
                val cornerR = r * 0.4f

                val glowScale = 1.6f
                val glowR = r * glowScale
                val glowCR = cornerR * glowScale

                val coreColor = bgColor
                val glowCenter = bgColor.withAlpha(0.5f)
                val glowEdge = bgColor.withAlpha(0f)

                draw.configured(null, clipped = true) {
                    translate(cx, cy, 0f)
                    rotate(45f.deg, Vec3f.Z_AXIS)

                    this.color = glowCenter
                    val centerIdx = vertex { position.set(0f, 0f, 0f) }

                    var firstEdgeIdx = -1
                    var prevEdgeIdx = -1

                    fun addCornerArc(centerX: Float, centerY: Float, startAng: Float, endAng: Float) {
                        val steps = 6
                        for (i in 0..steps) {
                            val ang = startAng + (endAng - startAng) * (i / steps.toFloat())
                            val px = centerX + cos(ang) * glowCR
                            val py = centerY + sin(ang) * glowCR

                            this.color = glowEdge
                            val idx = vertex { position.set(px, py, 0f) }

                            if (firstEdgeIdx == -1) firstEdgeIdx = idx
                            if (prevEdgeIdx != -1) {
                                geometry.addTriIndices(centerIdx, prevEdgeIdx, idx)
                            }
                            prevEdgeIdx = idx
                        }
                    }

                    val off = glowR - glowCR
                    val pi = PI.toFloat()

                    addCornerArc(off, off, 0f, pi * 0.5f)
                    addCornerArc(-off, off, pi * 0.5f, pi)
                    addCornerArc(-off, -off, pi, pi * 1.5f)
                    addCornerArc(off, -off, pi * 1.5f, pi * 2f)

                    if (prevEdgeIdx != -1 && firstEdgeIdx != -1) {
                        geometry.addTriIndices(centerIdx, prevEdgeIdx, firstEdgeIdx)
                    }

                    this.color = coreColor
                    rect {
                        size.set(r * 2f, r * 2f)
                        cornerRadius = cornerR
                    }
                }
            }
        })

        if (isHovered.use() && !isLocked) {
            Tooltip("${"%.2f".format(keyframe.time)} s")
        }
    }
}

private fun UiScope.TimeRuler(pxPerSec: Float, maxKeyTime: Float, controller: TimelineController) {
    Box(width = Grow.Std, height = 30.dp) {
        modifier
            .border(RectBorder(colors.secondaryVariant.withAlpha(0.2f), 1.dp))
            .onDrag {
                if (!controller.isDraggingWorkAreaEnd) {
                    controller.seekJob?.cancel()
                    controller.seekJob = null

                    val localX = it.position.x
                    val t = (localX / pxPerSec).coerceAtLeast(0f)
                    controller.currentTime.set(min(t, controller.workAreaEnd.value))
                }
            }
            .onClick {
                if (!controller.isDraggingWorkAreaEnd) {
                    val localX = it.position.x
                    val t = (localX / pxPerSec).coerceAtLeast(0f)

                    controller.selectedKeyframes.clear()
                    controller.isWorkAreaSelected.set(false)

                    if (it.pointer.leftButtonRepeatedClickCount == 1) {
                        controller.seekJob?.cancel()
                        controller.seekJob = coroutineScope.launch {
                            delay(250)
                            controller.currentTime.set(min(t, controller.workAreaEnd.value))
                        }
                    } else if (it.pointer.leftButtonRepeatedClickCount == 2) {
                        controller.seekJob?.cancel()
                        controller.seekJob = null

                        val newEnd = max(t, maxKeyTime)
                        controller.workAreaEnd.set(round(newEnd * 10) / 10f)

                        if (controller.currentTime.value > controller.workAreaEnd.value) {
                            controller.currentTime.set(0f)
                        }
                    }
                }
            }

        modifier.background(UiRenderer { node ->
            node.apply {
                val draw = getUiPrimitives()
                draw.localRect(0f, 0f, widthPx, heightPx, Color("24272E"))
                val clipStartPx = max(0f, node.clipLeftPx - node.leftPx)
                val clipEndPx = node.clipRightPx - node.leftPx
                val startSec = floor(clipStartPx / pxPerSec).toInt()
                val endSec = ceil(clipEndPx / pxPerSec).toInt()
                val step = if (pxPerSec < 50) 1f else if (pxPerSec < 150) 0.5f else 0.1f

                for (sec in startSec..endSec) {
                    val x = sec * pxPerSec
                    draw.localRect(x, 15f, 1f, 15f, colors.onBackground.withAlpha(0.5f))
                    if (step < 1f) {
                        val subSteps = (1f / step).toInt()
                        for (j in 1 until subSteps) {
                            val subX = x + (j * step * pxPerSec)
                            if (subX in clipStartPx..clipEndPx) {
                                draw.localRect(subX, 22f, 1f, 8f, colors.onBackground.withAlpha(0.3f))
                            }
                        }
                    }
                }
            }
        })

        val scrollX = controller.scrollState.xScrollDp.use().dp.px
        val viewW = controller.scrollState.viewWidthDp.use().dp.px
        val step = if (pxPerSec < 50) 5 else 1
        val startSec = floor(scrollX / pxPerSec).toInt() / step * step
        val endSec = ceil((scrollX + viewW) / pxPerSec).toInt()
        for (i in startSec..endSec step step) {
            if (i >= 0) {
                Text("$i") {
                    modifier
                        .margin(start = Dp.fromPx(i * pxPerSec + 4f))
                        .font(sizes.smallText)
                        .textColor(colors.onBackground.withAlpha(0.7f))
                }
            }
        }

        val endX = controller.workAreaEnd.use() * pxPerSec
        val markerSize = 10.dp
        val isSelected = controller.isWorkAreaSelected.use()

        Box {
            modifier
                .margin(start = Dp.fromPx(endX) - markerSize * 0.5f)
                .width(markerSize)
                .height(Grow.Std)
                .zLayer(30)
                .onEnter { PointerInput.cursorShape = CursorShape.RESIZE_E }
                .onExit { PointerInput.cursorShape = CursorShape.DEFAULT }
                .onClick {
                    controller.isWorkAreaSelected.set(true)
                    controller.selectedKeyframes.clear()
                    it.pointer.consume()
                }
                .onDragStart {
                    controller.isDraggingWorkAreaEnd = true
                    controller.isWorkAreaSelected.set(true)
                    controller.selectedKeyframes.clear()
                    it.pointer.consume()
                }
                .onDrag {
                    val dragDeltaSeconds = Dp.fromPx(it.pointer.delta.x).value / pxPerSec

                    var newTime = controller.workAreaEnd.value + dragDeltaSeconds
                    newTime = max(newTime, maxKeyTime)
                    newTime = max(newTime, 0.1f)

                    controller.workAreaEnd.set(round(newTime * 100) / 100f)

                    if (controller.currentTime.value > controller.workAreaEnd.value) {
                        controller.currentTime.set(0f)
                    }

                    it.pointer.consume()
                }
                .onDragEnd { controller.isDraggingWorkAreaEnd = false }

            val markerColor = if (isSelected) Color.WHITE else ColorTheme.UI.BackgroundAccent
            modifier.background(UiRenderer { node ->
                node.apply {
                    val draw = getPlainBuilder()
                    val h = heightPx
                    val w = widthPx
                    val x = w * 0.5f
                    val stroke = 2f
                    draw.configured(markerColor) {
                        line(x, 0f, x, h, stroke)
                        line(x, stroke/2, x - 6f, stroke/2, stroke)
                        line(x, h - stroke/2, x - 6f, h - stroke/2, stroke)
                    }
                }
            })
            Tooltip("${controller.workAreaEnd.value} s")
        }
    }
}

private fun UiScope.WorkAreaOverlay(pxPerSec: Float, controller: TimelineController) {
    val end = controller.workAreaEnd.use()
    val xPos = end * pxPerSec

    Box(width = Grow.Std, height = Grow.Std) {
        modifier
            .margin(start = Dp.fromPx(xPos))
            .backgroundColor(colors.background.withAlpha(0.5f))
            .zLayer(5)
            .onClick { it.isConsumed = false }
    }
}


private fun UiScope.PlayheadOverlay(pxPerSec: Float, scrollState: ScrollState, controller: TimelineController) {
    val curT = controller.currentTime.use()
    val scrollXDp = scrollState.xScrollDp.use()

    val playheadColor = Color("FF8904")

    Box(width = Grow.Std, height = Grow.Std) {
        modifier
            .zLayer(UiSurface.LAYER_FLOATING)
            .background(UiRenderer { node ->
                val areaHeight = node.heightPx
                val areaWidth = node.widthPx

                node.apply {
                    val draw = getPlainBuilder()
                    val prim = getUiPrimitives()

                    val xPos = (curT * pxPerSec - scrollXDp.dp.px) + paddingStartPx

                    if (xPos < -20f || xPos > areaWidth + 20f) return@apply

                    prim.localRect(xPos - 0.5f.dp.px, 0f, 1.dp.px, areaHeight, playheadColor)

                    val glowRadius = 10.dp.px
                    val coreRadius = 4.dp.px

                    draw.configured(null, clipped = false) {
                        translate(xPos, 0f, 0f)

                        this.color = playheadColor.withAlpha(0.6f)
                        val centerIdx = vertex { position.set(0f, 0f, 0f) }

                        this.color = playheadColor.withAlpha(0f)
                        val stepsGlow = 20
                        val firstEdgeIdx = geometry.numVertices

                        for (i in 0..stepsGlow) {
                            val ang = (i.toFloat() / stepsGlow) * PI.toFloat()
                            vertex {
                                position.set(cos(ang) * glowRadius, sin(ang) * glowRadius, 0f)
                            }
                        }

                        for (i in 0 until stepsGlow) {
                            geometry.addTriIndices(centerIdx, firstEdgeIdx + i, firstEdgeIdx + i + 1)
                        }

                        this.color = playheadColor
                        circle {
                            radius = coreRadius
                            steps = 16
                            startDeg = 0f
                            sweepDeg = 180f
                        }
                    }
                }
            })
            .onClick { it.isConsumed = false }
    }
}