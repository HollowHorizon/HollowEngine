package ru.hollowhorizon.hollowengine.client.gui.timeline.ui

import de.fabmax.kool.math.Easing
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.deg
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.timeline.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.ComboBox as ThemeComboBox

fun UiScope.Toolbar(controller: TimelineController, background: Color, extraContent: UiScope.() -> Unit = {}) {
    Row(width = Grow.Std, Grow.Std) {
        modifier
            .backgroundColor(background)
            .onClick {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(false)
            }

        Row(height = Grow.Std) {
            modifier.alignY(AlignmentY.Center)

            ToolbarIconButton(
                icon = controller.iconPrev,
                padding = 0.dp,
                size = Dimensions.PaddingLarge
            ) {
                controller.isPlaying.set(false)
                controller.setCurrentTime(0f)
            }

            Box(width = Dimensions.PaddingMedium) {}

            val isPlaying = controller.isPlaying.use()
            val playIcon = if (isPlaying) controller.iconPause else controller.iconPlay

            ToolbarIconButton(
                icon = playIcon,
                padding = Dimensions.PaddingSmall,
                size = Dimensions.PaddingHuge
            ) {
                controller.togglePlayback()
            }

            Box(width = Dimensions.PaddingMedium) {}

            ToolbarIconButton(
                icon = controller.iconNext,
                padding = 0.dp,
                size = Dimensions.PaddingLarge
            ) {
                controller.setCurrentTime(controller.workAreaEnd.value)
            }
        }

        Box(Dimensions.PaddingLarge, Grow.Std) {
            modifier
                .width(Dimensions.PaddingSmall)
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
                    .alignY(AlignmentY.Center)
                    .background(RoundRectBackground(ColorTheme.UI.BackgroundGeneral, Dimensions.PaddingNormal))
                    .padding(horizontal = Dimensions.PaddingMedium, vertical = Dimensions.PaddingNormal)
                    .size(FitContent, Dimensions.PaddingNormal + Dimensions.PaddingHuge)
                    .colors(
                        textColor = ColorTheme.UI.WhiteReplacement,
                        lineColor = Color(0f, 0f, 0f, 0f),
                        lineColorFocused = Color(0f, 0f, 0f, 0f),
                        selectionColor = colors.primaryAlpha(0.3f)
                    )
                    .textAlignX(AlignmentX.Center)
                    .onChange { txt ->
                        txt.replace(',', '.').toFloatOrNull()?.let {
                            controller.setCurrentTime(it)
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

        Box(Dimensions.PaddingLarge, Grow.Std) {
            modifier
                .width(Dimensions.PaddingSmall)
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

        val playbackModeIndex = remember(PlaybackMode.entries.indexOf(controller.playbackMode.use()))
        if (PlaybackMode.entries[playbackModeIndex.use()] != controller.playbackMode.use()) {
            playbackModeIndex.set(PlaybackMode.entries.indexOf(controller.playbackMode.value))
        }
        ThemeComboBox(
            preview = controller.playbackMode.use().name,
            items = PlaybackMode.entries.map { mode ->
                Composable {
                    Text(mode.name) {
                        modifier
                            .alignY(AlignmentY.Center)
                            .margin(horizontal = Dimensions.PaddingMedium)
                            .textColor(ColorTheme.UI.WhiteReplacement)
                    }
                }
            },
            itemIndex = playbackModeIndex,
        )
        if (PlaybackMode.entries[playbackModeIndex.use()] != controller.playbackMode.use()) {
            controller.playbackMode.set(PlaybackMode.entries[playbackModeIndex.value])
        }

        Box(Dimensions.PaddingLarge, Grow.Std) {
            modifier
                .width(Dimensions.PaddingSmall)
                .margin(horizontal = Dimensions.PaddingHuge)
                .backgroundColor(ColorTheme.UI.BackgroundElements)
                .alignY(AlignmentY.Center)
        }

        Row(height = Grow.Std) {
            modifier
                .alignY(AlignmentY.Center)
                .onClick { controller.setCameraPreviewEnabled(!controller.isCameraPreviewEnabled.value) }

            Checkbox(controller.isCameraPreviewEnabled.use()) {
                modifier
                    .alignY(AlignmentY.Center)
                    .colors(
                        borderColor = ColorTheme.UI.BackgroundAccent,
                        backgroundColor = ColorTheme.UI.BackgroundDarker,
                        fillColor = ColorTheme.UI.BackgroundElements,
                        checkMarkColor = ColorTheme.Accents.Main
                    )
                    .onToggle { controller.setCameraPreviewEnabled(it) }
            }

            Text("Preview") {
                modifier
                    .alignY(AlignmentY.Center)
                    .margin(start = Dimensions.PaddingMedium)
                    .textColor(ColorTheme.UI.WhiteReplacement)
            }
        }

        extraContent()
    }
}

fun UiScope.ToolbarIconButton(
    icon: Texture2d,
    padding: Dp = 28.dp,
    size: Dimension = Grow.Std,
    onClick: () -> Unit,
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
            .padding(padding)
            .alignY(AlignmentY.Center)
            .onEnter { isHovered.set(true) }
            .onExit { isHovered.set(false) }
            .onClick { onClick() }
            .background(RoundRectBackground(Color("393D48").withAlpha(alpha), 6.dp))

        Image(icon) {
            modifier
                .size(size, size)
                .align(AlignmentX.Center, AlignmentY.Center)
                .tint(ColorTheme.UI.WhiteReplacement)
        }
    }
}


fun UiScope.TrackHeaderList(controller: TimelineController) {
    Column(height = Grow.Std) {
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

        Column {
            modifier.margin(top = Dp(-controller.scrollState.yScrollDp.use()))

            Box(width = Grow.Std, height = 30.dp) {
                modifier
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
                GroupHeader(group, controller, 0)
            }
        }

    }
}

private fun UiScope.GroupHeader(group: TrackGroup, controller: TimelineController, depth: Int) {
    Row(Grow.Std, height = 30.dp) {
        modifier
            .backgroundColor(Color("24272E"))
            .onClick {
                controller.selectedKeyframes.clear()
                controller.isWorkAreaSelected.set(false)
            }

        Box(Dimensions.PaddingNormal + Dp(depth * 14f)) {}

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
                .padding(start = Dimensions.PaddingMedium)
                .alignY(AlignmentY.Center)
                .textColor(ColorTheme.Accents.Main)
        }

        ToggleIcon(
            state = group.isVisible,
            iconOn = controller.visible,
            iconOff = controller.invisible,
            iconSize = 22.dp,
            marginEnd = Dimensions.PaddingNormal,
            onToggle = { isVisible ->
                setGroupVisible(group, isVisible)
            },
        )

        ToggleIcon(
            state = group.isLocked,
            iconOn = controller.locked,
            iconOff = controller.unlocked,
            iconSize = 11.dp,
            marginEnd = Dimensions.PaddingNormal,
            onToggle = { isLocked ->
                setGroupLocked(group, isLocked)
            },
        )
    }

    if (!group.isCollapsed.use()) {
        group.children.forEach { child ->
            GroupHeader(child, controller, depth + 1)
        }
        group.tracks.forEach { track ->
            TrackHeader(track, controller, depth + 1)
        }
    }
}

private fun UiScope.TrackHeader(track: BaseAnimTrack, controller: TimelineController, depth: Int) {
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
            .backgroundColor(bgColor)
            .onClick { ev ->
                if (ev.pointer.isRightButtonClicked && track is AnimTrack<*>) {
                    controller.trackContextMenuTime = null
                    (controller.onTrackHeaderContextMenu ?: controller.onTrackContextMenu)?.invoke(ev, track)
                    ev.pointer.consume()
                } else {
                    controller.selectedKeyframes.clear()
                    controller.isWorkAreaSelected.set(false)
                }
            }

        Row(width = Grow.Std, height = Grow.Std) {
            Box(Dimensions.PaddingNormal + Dp((depth + 1) * 14f)) {}


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
                marginEnd = Dimensions.PaddingNormal,
                tint = contentColor,
            )

            ToggleIcon(
                state = track.isLocked,
                iconOn = controller.locked,
                iconOff = controller.unlocked,
                iconSize = 11.dp,
                marginEnd = Dimensions.PaddingNormal,
                tint = contentColor,
            )
        }
    }
}

private fun setGroupVisible(group: TrackGroup, isVisible: Boolean) {
    group.isVisible.set(isVisible)
    group.tracks.forEach { it.isVisible.set(isVisible) }
    group.children.forEach { setGroupVisible(it, isVisible) }
}

private fun setGroupLocked(group: TrackGroup, isLocked: Boolean) {
    group.isLocked.set(isLocked)
    group.tracks.forEach { it.isLocked.set(isLocked) }
    group.children.forEach { setGroupLocked(it, isLocked) }
}

private fun UiScope.ToggleIcon(
    state: MutableStateValue<Boolean>,
    iconOn: Texture2d,
    iconOff: Texture2d,
    iconSize: Dp,
    marginEnd: Dp = Dp.ZERO,
    tint: Color = Color.WHITE,
    onToggle: ((Boolean) -> Unit)? = null,
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
