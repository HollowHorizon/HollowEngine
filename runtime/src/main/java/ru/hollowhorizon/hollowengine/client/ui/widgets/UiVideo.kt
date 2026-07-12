package ru.hollowhorizon.hollowengine.client.ui.widgets

import androidx.compose.runtime.*
import com.mojang.blaze3d.systems.RenderSystem
import ru.hollowhorizon.hollowengine.api.VideoApi
import ru.hollowhorizon.hollowengine.api.VideoPlaybackOptions
import ru.hollowhorizon.hollowengine.api.VideoPlayer
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.style.UiGradientStop
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageFit
import ru.hollowhorizon.hollowengine.client.ui.style.UiShadow
import ru.hollowhorizon.hollowengine.client.utils.lang
import kotlin.math.abs

/**
 * Inline video playback. Decoding is provided by the video addon through [VideoApi]; when the addon
 * is not installed the widget renders a "no video addon" placeholder instead of playing.
 *
 * The player session lives for as long as the composable stays in the composition and is released
 * through a [DisposableEffect]. With [controls] enabled, hovering the video shows a control bar:
 * play/pause, elapsed/total time, a seekable timeline and a volume popup that opens on hover and
 * closes when the pointer leaves its bounds.
 */
@Composable
fun Video(
    source: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifier: Modifier? = null,
    controls: Boolean = true,
    autoPlay: Boolean = true,
    volume: Float = 1f,
    startSeconds: Double = 0.0,
    onEnded: (() -> Unit)? = null,
) {
    val api = remember { VideoApi.find() }
    if (api == null) {
        VideoPlaceholder(id, tags, modifier) {
            Text("hollowengine.gui.video.missing_addon".lang, tags = listOf("video-message"))
        }
        return
    }

    var player by remember(source) { mutableStateOf<VideoPlayer?>(null) }
    val state = remember(source) { VideoWidgetState() }
    val endedCallback = rememberUpdatedState(onEnded)

    DisposableEffect(source) {
        val created = api.createPlayer(
            source,
            VideoPlaybackOptions(
                closeOnEnd = false,
                volume = volume,
                startSeconds = startSeconds,
                autoPlay = autoPlay,
            ),
        )

        created?.setFrameListener {
            state.texture = created.texture?.toString()
            state.positionSeconds = created.positionSeconds
            state.durationSeconds = created.durationSeconds
            state.playing = created.playing
            state.failed = created.error != null
            if (created.ended && !state.ended) {
                state.ended = true
                endedCallback.value?.invoke()
            } else if (!created.ended && state.ended) {
                state.ended = false
            }
        }
        player = created
        onDispose {
            RenderSystem.recordRenderCall {
                created?.setFrameListener(null)
                created?.close()
            }
        }
    }

    var bounds by remember { mutableStateOf(UiRect.Zero) }
    var volumeOpen by remember { mutableStateOf(false) }

    VideoPlaceholder(id, tags, modifier, Modifier.onPlaced { bounds = it }) {
        val texture = state.texture
        if (texture != null) {
            Image(
                texture,
                tags = listOf("video-frame"),
                modifier = Modifier.size(100.percent, 100.percent).imageFit(UiImageFit.CONTAIN),
            )
        }
        if (state.failed) {
            Text("hollowengine.gui.video.playback_failed".lang, tags = listOf("video-message"))
        }

        val activePlayer = player
        if (controls && !state.failed && activePlayer != null) {
            val pointer = LocalPointer.current
            val hovered = pointer.isKnown && bounds.contains(pointer.x, pointer.y)
            if (hovered || volumeOpen) {
                VideoControlsBar(
                    player = activePlayer,
                    state = state,
                    volumeOpen = volumeOpen,
                    onVolumeOpenChange = { volumeOpen = it },
                )
            }
        }
    }
}

/** Shared black letterbox container for the video, the missing-addon stub and the failure state. */
@Composable
private fun VideoPlaceholder(
    id: String?,
    tags: Iterable<String>,
    modifier: Modifier?,
    extraModifier: Modifier = Modifier,
    content: HollowUiContent,
) {
    Box(
        id = id,
        mode = UiBoxMode.STACK,
        tags = tags + "video",
        modifier = Modifier.size(100.percent, 100.percent)
            .background(VideoBackgroundColor)
            .clip()
            .alignItems(UiAlign.CENTER, UiAlign.CENTER)
            .then(extraModifier)
            .then(modifier ?: Modifier),
        content = content,
    )
}

@Composable
private fun VideoControlsBar(
    player: VideoPlayer,
    state: VideoWidgetState,
    volumeOpen: Boolean,
    onVolumeOpenChange: (Boolean) -> Unit,
) {
    Column(
        tags = listOf("video-controls"),
        modifier = Modifier.size(100.percent, UiLength.Auto)
            .align(vertical = UiAlign.END)
            .background(ScrimAngleDegrees, ScrimStops)
            .padding(10.px, 6.px)
            .gap(2.px),
    ) {
        VideoTimeline(player, state)
        Row(
            tags = listOf("video-controls-row"),
            modifier = Modifier.size(100.percent, UiLength.Auto)
                .alignItems(vertical = UiAlign.CENTER)
                .gap(10.px),
        ) {
            val playing = state.playing
            VideoIconButton(
                source = if (playing) PauseIcon else PlayIcon,
                tag = "video-controls-toggle",
            ) {
                if (playing) player.pause() else player.play()
            }
            VideoVolumeControl(player, volumeOpen, onVolumeOpenChange)
            Text(
                "${formatTime(state.positionSeconds)} / ${formatTime(state.durationSeconds)}",
                tags = listOf("video-controls-time"),
                modifier = Modifier.fontSize(9f).foreground(TimeTextColor),
            )
        }
    }
}

@Composable
private fun VideoIconButton(
    source: String,
    tag: String,
    extraModifier: Modifier = Modifier,
    onClick: (UiEvent) -> Unit,
) {
    var hovered by remember { mutableStateOf(false) }
    Image(
        source,
        tags = listOf("video-icon-button", tag),
        modifier = Modifier.size(13.px, 13.px)
            .cursor(UiCursorShape.HAND)
            .opacity(if (hovered) 1f else 0.78f)
            .onEnter { hovered = true }
            .onExit { hovered = false }
            .onClick { event ->
                onClick(event)
                event.consume()
            }
            .then(extraModifier),
    )
}

@Composable
private fun VideoTimeline(player: VideoPlayer, state: VideoWidgetState) {
    var seekPreview by remember { mutableStateOf<Double?>(null) }
    val duration = state.durationSeconds
    val position = state.positionSeconds
    seekPreview?.let { preview ->
        if (abs(position - preview) < SeekPreviewReleaseSeconds) seekPreview = null
    }
    val shown = seekPreview ?: position
    val fraction = if (duration > 0.0) (shown / duration).toFloat() else 0f
    VideoBarSlider(
        fraction = fraction,
        tag = "video-timeline",
        onScrub = { if (duration > 0.0) seekPreview = it * duration },
        onCommit = {
            if (duration > 0.0) {
                val target = it * duration
                player.seek(target)
                seekPreview = target
            }
        },
    )
}

@Composable
private fun VideoBarSlider(
    fraction: Float,
    tag: String,
    modifier: Modifier? = null,
    onScrub: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    var bounds by remember { mutableStateOf(UiRect.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var lastScrubbed by remember { mutableStateOf(0f) }
    val pointer = LocalPointer.current
    val hovered = pointer.isKnown && bounds.contains(pointer.x, pointer.y)
    val active = hovered || dragging
    val clamped = fraction.coerceIn(0f, 1f)

    fun fractionAt(event: UiEvent): Float = (event.localX / event.width.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(
        mode = UiBoxMode.STACK,
        tags = listOf("video-slider", tag),
        modifier = Modifier.size(100.percent, 12.px)
            .cursor(UiCursorShape.HAND)
            .onPlaced { bounds = it }
            .onPress { event ->
                dragging = true
                lastScrubbed = fractionAt(event)
                onScrub(lastScrubbed)
            }
            .onDrag { event ->
                lastScrubbed = fractionAt(event)
                onScrub(lastScrubbed)
            }
            .onRelease {
                if (dragging) onCommit(lastScrubbed)
                dragging = false
            }
            .then(modifier ?: Modifier),
    ) {
        val trackHeight = if (active) 4.px else 2.px
        Box(
            tags = listOf("video-slider-track"),
            modifier = Modifier.size(100.percent, trackHeight)
                .align(vertical = UiAlign.CENTER)
                .background(SliderTrackColor)
                .borderRadius(2f),
        )
        Box(
            tags = listOf("video-slider-fill"),
            modifier = Modifier.size((clamped * 100f).percent, trackHeight)
                .align(vertical = UiAlign.CENTER)
                .background(AccentColor)
                .borderRadius(2f),
        )
        if (active) {
            Box(
                tags = listOf("video-slider-thumb"),
                modifier = Modifier.size(9.px, 9.px)
                    .align(vertical = UiAlign.CENTER)
                    .position((clamped * 100f).percent - 4.px, 0.px)
                    .background(ThumbColor)
                    .borderRadius(4.5f)
                    .shadow(UiShadow(offset = UiVec3(0f, 1f, 0f), blur = 3f, color = UiColor(0f, 0f, 0f, 0.5f))),
            )
        }
    }
}

@Composable
private fun VideoVolumeControl(
    player: VideoPlayer,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
) {
    var anchorBounds by remember { mutableStateOf(UiRect.Zero) }
    var popupBounds by remember { mutableStateOf(UiRect.Zero) }
    var level by remember { mutableStateOf(player.volume) }

    VideoIconButton(
        source = VolumeIcon,
        tag = "video-controls-volume",
        extraModifier = Modifier.input(hoverable = true)
            .onPlaced { anchorBounds = it }
            .onEnter { onOpenChange(true) },
    ) {
        onOpenChange(!open)
    }

    if (!open) return
    Popup(
        anchorBounds = anchorBounds,
        alignment = AboveStart,
        tags = listOf("video-volume-popup"),
        modifier = Modifier.background(PopupBackgroundColor)
            .border(1.px, PopupBorderColor, radius = 6f)
            .padding(10.px, 8.px)
            .gap(6.px)
            .onPlaced { popupBounds = it }
            .onExit { event ->
                if (!popupBounds.contains(event.x, event.y)) onOpenChange(false)
            },
        onDismiss = { onOpenChange(false) },
    ) {
        Row(modifier = Modifier.alignItems(vertical = UiAlign.CENTER).gap(6.px)) {
            Box(modifier = Modifier.size(72.px, 12.px)) {
                VideoBarSlider(
                    fraction = level,
                    tag = "video-volume-slider",
                    onScrub = {
                        level = it
                        player.setVolume(it)
                    },
                    onCommit = {
                        level = it
                        player.setVolume(it)
                    },
                )
            }
            Text(
                "${(level * 100).toInt()}%",
                tags = listOf("video-volume-value"),
                modifier = Modifier.fontSize(9f).foreground(TimeTextColor),
            )
        }
    }
}

@Stable
private class VideoWidgetState {
    var texture by mutableStateOf<String?>(null)
    var positionSeconds by mutableStateOf(0.0)
    var durationSeconds by mutableStateOf(0.0)
    var playing by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var failed by mutableStateOf(false)
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toLong().coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val secs = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

private val AboveStart = UiPopupAlignment(anchorVertical = UiAlign.START, popupVertical = UiAlign.END, offsetY = -4f)
private val VideoBackgroundColor = UiColor(0f, 0f, 0f, 1f)

private val AccentColor = UiColor(0.996f, 0.682f, 0.247f, 1f)
private val SliderTrackColor = UiColor(1f, 1f, 1f, 0.25f)
private val ThumbColor = UiColor(1f, 1f, 1f, 1f)
private val TimeTextColor = UiColor(1f, 1f, 1f, 0.85f)
private val PopupBackgroundColor = UiColor(0.09f, 0.1f, 0.12f, 0.95f)
private val PopupBorderColor = UiColor(1f, 1f, 1f, 0.12f)

private const val ScrimAngleDegrees = 90f
private val ScrimStops = listOf(
    UiGradientStop(0f, UiColor(0f, 0f, 0f, 0f)),
    UiGradientStop(1f, UiColor(0f, 0f, 0f, 0.8f)),
)

private const val PlayIcon = "hollowengine:textures/gui/icons/play.svg"
private const val PauseIcon = "hollowengine:textures/gui/icons/pause.svg"
private const val VolumeIcon = "hollowengine:textures/gui/icons/volume.svg"
private const val SeekPreviewReleaseSeconds = 0.3
