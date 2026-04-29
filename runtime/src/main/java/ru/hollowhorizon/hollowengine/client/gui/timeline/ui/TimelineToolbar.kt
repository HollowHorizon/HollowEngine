package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.clamp
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.timeline.BaseAnimTrack
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.timeline.PlaybackMode
import ru.hollowhorizon.hollowengine.client.gui.timeline.TimelineController
import ru.hollowhorizon.hollowengine.client.gui.timeline.TrackGroup

private val playbackSpeeds = listOf(0.1f, 0.25f, 0.5f, 1.0f, 2.0f, 5.0f, 10.0f)

fun UiScope.Toolbar(controller: TimelineController) {
    Row(width = Grow.Std, height = 57.dp) {
        modifier
            .border(RectBorder(ColorTheme.UI.BackgroundElements, 1.dp))
            .backgroundColor(ColorTheme.UI.BackgroundSecondary)
            .padding(start = Dimensions.PaddingHuge, top = 12.dp, end = Dimensions.PaddingHuge, bottom = 12.dp)
            .onClick {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(false)
            }

        Row(height = Grow.Std) {
            modifier.alignY(AlignmentY.Center)

            ToolbarIconButton(
                icon = controller.iconPrev,
                size = 32.dp,
                iconSize = 32.dp
            ) {
                controller.isPlaying.set(false)
                controller.currentTime.set(0f)
            }

            Box(width = Dimensions.PaddingMedium) {}

            val isPlaying = controller.isPlaying.use()
            val playIcon = if (isPlaying) controller.iconPause else controller.iconPlay

            ToolbarIconButton(
                icon = playIcon,
                size = 32.dp,
                iconSize = 16.dp
            ) {
                controller.isPlaying.set(!controller.isPlaying.value)
            }

            Box(width = Dimensions.PaddingMedium) {}

            ToolbarIconButton(
                icon = controller.iconNext,
                size = 32.dp,
                iconSize = 32.dp
            ) {
                controller.currentTime.set(controller.workAreaEnd.value)
            }
        }

        Box(width = sizes.largeGap) {
            modifier
                .width(1.dp)
                .height(24.dp)
                .margin(horizontal = Dimensions.PaddingHuge)
                .backgroundColor(ColorTheme.UI.BackgroundElements)
                .alignY(AlignmentY.Center)
        }

        Row(height = Grow.Std) {
            modifier.alignY(AlignmentY.Center)

            Text("Frame:") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = Dimensions.PaddingMedium)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }

            TextField("%.2f".format(controller.currentTime.use())) {
                modifier
                    .width(42.dp)
                    .height(26.dp)
                    .alignY(AlignmentY.Center)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundGeneral, Dimensions.PaddingNormal))
                    .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                    .colors(
                        textColor = ColorTheme.UI.WhiteReplacement,
                        lineColor = Color(0f, 0f, 0f, 0f),
                        lineColorFocused = Color(0f, 0f, 0f, 0f),
                        selectionColor = colors.primaryAlpha(0.3f)
                    )
                    .textAlignX(AlignmentX.Center)
                    .onChange { txt ->
                        txt.toFloatOrNull()?.let {
                            controller.currentTime.set(it)
                        }
                    }
            }

            Text("/ ${controller.workAreaEnd.use()}") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(start = Dimensions.PaddingMedium)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }
        }

        Box(width = sizes.largeGap) {
            modifier
                .width(1.dp)
                .height(24.dp)
                .margin(horizontal = Dimensions.PaddingHuge)
                .backgroundColor(ColorTheme.UI.BackgroundElements)
                .alignY(AlignmentY.Center)
        }

        Row(height = Grow.Std) {
            modifier.alignY(AlignmentY.Center)

            Text("Zoom:") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = Dimensions.PaddingMedium)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }

            ToolbarIconButton(
                icon = controller.iconZoomOut,
                size = 28.dp
            ) {
                val newZoom = (controller.pixelsPerSecond.value * 0.8f).clamp(10f, 500f)
                controller.pixelsPerSecond.set(newZoom)
            }

            Box(width = Dimensions.PaddingMedium) {}

            Slider(controller.pixelsPerSecond.use(), 10f, 500f) {
                modifier
                    .width(150.dp)
                    .alignY(AlignmentY.Center)
                    .colors(
                        trackColor = ColorTheme.UI.BackgroundElements,
                        trackColorActive = ColorTheme.Accents.Main,
                        knobColor = ColorTheme.Accents.Main
                    )
                    .onChange { controller.pixelsPerSecond.set(it) }
            }

            Box(width = Dimensions.PaddingMedium) {}

            ToolbarIconButton(
                icon = controller.iconZoomIn,
                size = 28.dp
            ) {
                val newZoom = (controller.pixelsPerSecond.value * 1.2f).clamp(10f, 500f)
                controller.pixelsPerSecond.set(newZoom)
            }
        }

        Box(width = sizes.largeGap) {
            modifier
                .width(1.dp)
                .height(24.dp)
                .margin(horizontal = Dimensions.PaddingHuge)
                .backgroundColor(ColorTheme.UI.BackgroundElements)
                .alignY(AlignmentY.Center)
        }

        Text("Mode:") {
            modifier
                .alignY(AlignmentY.Center)
                .padding(end = Dimensions.PaddingMedium)
                .textColor(ColorTheme.UI.WhiteReplacement)
        }

        ComboBox {
            modifier
                .width(100.dp)
                .alignY(AlignmentY.Center)
                .items(PlaybackMode.entries)
                .selectedIndex(PlaybackMode.entries.indexOf(controller.playbackMode.use()))
                .onItemSelected { idx -> controller.playbackMode.set(PlaybackMode.entries[idx]) }
        }

        Box(width = sizes.largeGap) {
            modifier
                .width(1.dp)
                .height(24.dp)
                .margin(horizontal = Dimensions.PaddingHuge)
                .backgroundColor(ColorTheme.UI.BackgroundElements)
                .alignY(AlignmentY.Center)
        }

        Text("Speed:") {
            modifier
                .alignY(AlignmentY.Center)
                .padding(end = Dimensions.PaddingMedium)
                .textColor(ColorTheme.UI.WhiteReplacement)
        }

        ComboBox {
            val currentSpeed = controller.playbackSpeed.use()
            var selIndex = playbackSpeeds.indexOfFirst { it == currentSpeed }
            if (selIndex == -1) selIndex = playbackSpeeds.indexOf(1.0f)

            modifier
                .width(80.dp)
                .alignY(AlignmentY.Center)
                .items(playbackSpeeds.map { "${it}x" })
                .selectedIndex(selIndex)
                .onItemSelected { idx -> controller.playbackSpeed.set(playbackSpeeds[idx]) }
        }

        Box(width = Grow.Std) {}

        Row(height = Grow.Std) {
            modifier.alignY(AlignmentY.Center)

            ToolbarIconButton(controller.iconPulse) {}

            Box(width = 13.dp) {}

            ToolbarIconButton(controller.iconFilm) {}

            Box(width = 13.dp) {}

            ToolbarIconButton(controller.iconCompress) {}
        }
    }
}

private fun UiScope.ToolbarIconButton(
    icon: Texture2d,
    size: Dp = 28.dp,
    iconSize: Dimension = Grow.Std,
    onClick: () -> Unit
) {
    val isHovered = remember(false)
    val hoverAnim = remember { FloatAnimator(0.15f, Easing.smooth) }
    val lastTarget = remember(0f)

    val target = if (isHovered.use()) 1f else 0f
    if (lastTarget.value != target) {
        lastTarget.set(target)
        hoverAnim.start(target)
    }

    val alpha = hoverAnim.updateUsing()

    Box {
        modifier
            .size(size, size)
            .alignY(AlignmentY.Center)
            .onEnter { isHovered.set(true) }
            .onExit { isHovered.set(false) }
            .onClick { onClick() }
            .background(RoundRectBackground(Color("393D48").withAlpha(alpha), 6.dp))

        Image(icon) {
            modifier
                .size(iconSize, iconSize)
                .align(AlignmentX.Center, AlignmentY.Center)
                .tint(ColorTheme.UI.WhiteReplacement)
        }
    }
}


fun UiScope.TrackHeaderList(controller: TimelineController) {
    Column(width = 300.dp, height = Grow.Std) {
        modifier
            .zLayer(10)
            .backgroundColor(Color("1B1B1B"))
            .onClick {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(false)
            }
            .onWheelY { ev ->
                controller.scrollState.scrollDpY(ev.pointer.scroll.y * -50f)
                ev.pointer.consume()
            }

        Box(width = Grow.Std, height = Grow.Std) {
            Column(width = Grow.Std, height = FitContent) {
                modifier.margin(top = Dp(-controller.scrollState.yScrollDp.use()))

                Box(width = Grow.Std, height = 30.dp) {
                    modifier
                        .border(RectBorder(colors.secondaryVariant.withAlpha(0.5f), 1.dp))
                        .backgroundColor(ColorTheme.UI.BackgroundSecondary)
                        .onClick {
                            controller.selectedKeyframes.clear()
                            controller.isWorkAreaSelected.set(false)
                        }
                    Text("Треки") {
                        modifier
                            .align(AlignmentX.Start, AlignmentY.Center)
                            .padding(start = Dimensions.PaddingMedium)
                            .textColor(colors.onBackground.withAlpha(0.5f))
                    }
                }

                controller.groups.use().forEach { group ->
                    GroupHeader(group, controller)

                    if (!group.isCollapsed.use()) {
                        group.tracks.forEach { track ->
                            TrackHeader(track, controller)
                        }
                    }
                }
            }
        }
    }
}

private fun UiScope.GroupHeader(group: TrackGroup, controller: TimelineController) {
    Row(width = Grow.Std, height = 30.dp) {
        modifier
            .padding(start = sizes.smallGap, end = sizes.gap)
            .border(RectBorder(colors.secondaryVariant.withAlpha(0.2f), 1.dp))
            .backgroundColor(Color("24272E"))
            .onClick {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(false)
            }

        Box(width = 20.dp, height = Grow.Std) {
            modifier
                .onClick { group.isCollapsed.toggle() }

            val isCollapsed = group.isCollapsed.use()
            val targetAngle = if (isCollapsed) -180f else 0f
            val arrowAngle = animateFloatAsState(targetAngle).use()

            Box(width = 12.dp, height = 12.dp) {
                modifier
                    .align(AlignmentX.Center, AlignmentY.Center)
                    .background(UiRenderer { node ->
                        node.apply {
                            val imgMesh = surface.getMeshLayer(modifier.zLayer).addImage(controller.arrow)

                            imgMesh.builder.clear()

                            imgMesh.builder.configured(colors.onBackground) {
                                val cx = widthPx * 0.5f
                                val cy = heightPx * 0.5f

                                translate(cx, cy, 0f)
                                rotate(arrowAngle.deg, Vec3f.Z_AXIS)
                                translate(-cx, -cy, 0f)

                                rect {
                                    isCenteredOrigin = false
                                    origin.set(0f, 0f, 0f)
                                    size.set(widthPx, heightPx)
                                }
                            }

                            imgMesh.applyShader(controller.arrow, null)
                        }
                    })
            }
        }


        Text(group.nameState.use()) {
            modifier
                .width(Grow.Std)
                .alignY(AlignmentY.Center)
                .padding(start = sizes.smallGap)
                .font(sizes.normalText)
                .textColor(colors.primary)
        }

        ToggleIcon(
            state = group.isVisible,
            iconOn = controller.visible,
            iconOff = controller.invisible,
            iconSize = 22.dp,
            marginEnd = sizes.smallGap,
            onToggle = { isVisible ->
                group.tracks.forEach { it.isVisible.set(isVisible) }
            },
        )

        ToggleIcon(
            state = group.isLocked,
            iconOn = controller.locked,
            iconOff = controller.unlocked,
            iconSize = 11.dp,
            onToggle = { isLocked ->
                group.tracks.forEach { it.isLocked.set(isLocked) }
            },
        )
    }
}

private fun UiScope.TrackHeader(track: BaseAnimTrack, controller: TimelineController) {
    val isLocked = track.isLocked.use()
    val isVisible = track.isVisible.use()

    val bgColor = when {
        isLocked -> Color("171717")
        !isVisible -> Color("1A1A1A")
        else -> Color("1B1B1B")
    }

    val contentColor = if (isVisible) colors.onBackground else colors.onBackground.withAlpha(0.4f)

    Box(width = Grow.Std, height = 40.dp) {
        modifier
            .border(RectBorder(colors.secondaryVariant.withAlpha(0.2f), 1.dp))
            .backgroundColor(bgColor)
            .onClick {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(false)
            }

        Row(width = Grow.Std, height = Grow.Std) {
            modifier.padding(start = sizes.gap, end = sizes.gap)

            Box(width = 4.dp, height = 18.dp) {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(end = sizes.gap)
                    .background(RoundRectBackground(track.color, 4.dp))
            }

            Text(track.nameState.use()) {
                modifier
                    .width(Grow.Std)
                    .alignY(AlignmentY.Center)
                    .textColor(contentColor)
            }

            ToggleIcon(
                state = track.isVisible,
                iconOn = controller.visible,
                iconOff = controller.invisible,
                iconSize = 22.dp,
                marginEnd = sizes.smallGap,
                tint = contentColor,
            )

            ToggleIcon(
                state = track.isLocked,
                iconOn = controller.locked,
                iconOff = controller.unlocked,
                iconSize = 11.dp,
                tint = contentColor,
            )
        }
    }
}

private fun UiScope.ToggleIcon(
    state: MutableStateValue<Boolean>,
    iconOn: Texture2d,
    iconOff: Texture2d,
    iconSize: Dp,
    marginEnd: Dp = Dp.ZERO,
    tint: Color = Color.WHITE,
    onToggle: ((Boolean) -> Unit)? = null
) {
    val currentIcon = if (state.use()) iconOn else iconOff

    Image(currentIcon) {
        modifier
            .alignY(AlignmentY.Center)
            .size(iconSize, iconSize)
            .margin(end = marginEnd)
            .tint(tint)
            .onClick {
                val newState = !state.value
                state.set(newState)
                onToggle?.invoke(newState)
            }
    }
}