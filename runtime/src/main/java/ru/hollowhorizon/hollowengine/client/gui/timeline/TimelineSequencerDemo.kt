package ru.hollowhorizon.hollowengine.client.gui.timeline

import de.fabmax.kool.KoolContext
import de.fabmax.kool.input.CursorShape
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.math.Vec3f
import de.fabmax.kool.math.clamp
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import ru.dan_bat.demo.DemoScene
import ru.dan_bat.demo.scenes.hollowengine.ColorTheme
import ru.dan_bat.demo.scenes.hollowengine.Dimensions
import ru.dan_bat.demo.scenes.hollowengine.timeline.ui.*
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.PropertiesPanel
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TimelineArea
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.Toolbar
import ru.hollowhorizon.hollowengine.client.gui.timeline.ui.TrackHeaderList

class TimelineSequencerDemo : DemoScene("Timeline Sequencer") {

    private val controller = TimelineController()
    private lateinit var bloomScene: Bloom3dScene

    private val ibl by hdriImage("assets/common/hdri/syferfontein_0d_clear_1k.rgbe.png")
    private val topAreaHeight = mutableStateOf(Dp(500f))
    private var isDraggingSplitter = false
    private var lastViewportSize = Vec2i.ZERO

    object Palette {
        val TransformPosition = Color.fromHex("EF4444")
        val TransformRotation = Color.fromHex("22C55E")
        val TransformScale    = Color.fromHex("3B82F6")
        val VisualColors      = Color.fromHex("F472B6")
        val Camera            = Color.fromHex("FCD34D")
        val Audio             = Color.fromHex("06B6D4")
        val System            = Color.fromHex("BAE6FD")
    }

    override suspend fun loadResources(ctx: KoolContext) {
        controller.iconPrev = texture2d("assets/hollowengine/textures/gui/icons/step_backward.svg").load().getOrThrow()
        controller.iconPlay = texture2d("assets/hollowengine/textures/gui/icons/play.svg").load().getOrThrow()
        controller.iconPause = texture2d("assets/hollowengine/textures/gui/icons/pause.svg").load().getOrThrow()
        controller.iconNext = texture2d("assets/hollowengine/textures/gui/icons/step_forward.svg").load().getOrThrow()
        controller.iconZoomOut = texture2d("assets/hollowengine/textures/gui/icons/zoom_out.svg").load().getOrThrow()
        controller.iconZoomIn = texture2d("assets/hollowengine/textures/gui/icons/zoom_in.svg").load().getOrThrow()
        controller.iconPulse = texture2d("assets/hollowengine/textures/gui/icons/pulse.svg").load().getOrThrow()
        controller.iconFilm = texture2d("assets/hollowengine/textures/gui/icons/film.svg").load().getOrThrow()
        controller.iconCompress = texture2d("assets/hollowengine/textures/gui/icons/compress.svg").load().getOrThrow()
        controller.visible = texture2d("assets/hollowengine/textures/gui/icons/visible.svg").load().getOrThrow()
        controller.invisible = texture2d("assets/hollowengine/textures/gui/icons/invisible.svg").load().getOrThrow()
        controller.unlocked = texture2d("assets/hollowengine/textures/gui/icons/unlocked.svg").load().getOrThrow()
        controller.locked = texture2d("assets/hollowengine/textures/gui/icons/locked.svg").load().getOrThrow()
        controller.arrow = texture2d("assets/hollowengine/textures/gui/icons/arrow.svg").load().getOrThrow()
        super.loadResources(ctx)
    }

    override fun Scene.setupMainScene(ctx: KoolContext) {
        bloomScene = Bloom3dScene(this, ibl)
        bloomScene.setup(ctx)

        createScenarioTracks()

        setupUiScene()

        onUpdate {
            controller.onUpdate()
            bloomScene.update()
        }

        val themeColors = Colors.darkColors(
            primary = ColorTheme.Accents.Main,
            primaryVariant = ColorTheme.Accents.Main.mix(Color.BLACK, 0.2f),
            secondary = ColorTheme.UI.BackgroundAccent,
            secondaryVariant = ColorTheme.UI.BackgroundElements,
            background = ColorTheme.UI.BackgroundGeneral,
            backgroundVariant = ColorTheme.UI.BackgroundSecondary,
            onPrimary = ColorTheme.UI.BackgroundSecondary,
            onSecondary = ColorTheme.UI.WhiteReplacement,
            onBackground = ColorTheme.UI.WhiteReplacement
        )

        val themeSizes = Sizes.medium(
            normalText = MsdfFont(sizePts = Dimensions.FontNormal),
            smallText = MsdfFont(sizePts = Dimensions.FontNormal * 0.8f),
            gap = Dimensions.PaddingMedium,
            smallGap = Dimensions.PaddingNormal,
            largeGap = Dimensions.PaddingHuge
        )

        addPanelSurface(
            colors = themeColors,
            sizes = themeSizes,
            name = "SequencerWindow"
        ) {
            modifier
                .size(Grow.Std, Grow.Std)
                .background(RectBackground(colors.background))
                .layout(ColumnLayout)
                .onClick { controller.clearSelection() }

            Box(width = Grow.Std, height = topAreaHeight.use()) {
                modifier.backgroundColor(Color.BLACK)
                Image(bloomScene.finalTexture) {
                    modifier
                        .size(Grow.Std, Grow.Std)
                        .imageSize(ImageSize.Stretch)
                        .onPositioned {
                            val w = it.widthPx.toInt()
                            val h = it.heightPx.toInt()
                            if (!isDraggingSplitter && (w != lastViewportSize.x || h != lastViewportSize.y)) {
                                bloomScene.updateSize(w, h)
                                lastViewportSize = Vec2i(w, h)
                            }
                        }
                }
            }

            Box(width = Grow.Std, height = 6.dp) {
                modifier
                    .backgroundColor(colors.backgroundVariant)
                    .onEnter { PointerInput.cursorShape = CursorShape.RESIZE_N }
                    .onExit { PointerInput.cursorShape = CursorShape.DEFAULT }
                    .onDragStart { isDraggingSplitter = true; it.pointer.consume() }
                    .onDrag {
                        if (isDraggingSplitter) {
                            val newHeight = (topAreaHeight.value.value + Dp.fromPx(it.pointer.delta.y).value)
                            topAreaHeight.set(Dp(newHeight.clamp(100f, 1000f)))
                        }
                    }
                    .onDragEnd { isDraggingSplitter = false; lastViewportSize = Vec2i.ZERO }
                Box(width = 40.dp, height = 2.dp) {
                    modifier.align(AlignmentX.Center, AlignmentY.Center).backgroundColor(colors.onBackground.withAlpha(0.2f))
                }
            }


            Column(width = Grow.Std, height = Grow.Std) {
                Toolbar(controller)
                Row(Grow.Std, Grow.Std) {
                    TrackHeaderList(controller)
                    TimelineArea(controller)
                    PropertiesPanel(controller)
                }
            }
        }
    }

    private fun createScenarioTracks() {
        controller.workAreaEnd.set(10f)

        val scaleTrack = AnimTrack(
            name = "Scale",
            trackColor = Palette.TransformScale,
            driver = Vec3PropertyDriver { bloomScene.cubeScale = it },
            defaultValue = Vec3f.ONES,
//            keyframes = mutableListOf(
//                Keyframe(0f, Vec3f(0f, 0f, 0f), Easing.easeOutElastic),
//                Keyframe(1.5f, Vec3f(1.5f, 1.5f, 1.5f), Easing.easeInOutQuad),
//                Keyframe(3.0f, Vec3f(1.0f, 1.0f, 1.0f)),
//                Keyframe(6.0f, Vec3f(1.0f, 1.0f, 1.0f), Easing.easeInBack),
//                Keyframe(7.0f, Vec3f(0.0f, 0.0f, 0.0f))
//            )
        )
        controller.addTrack("Transform", scaleTrack)

        val posTrack = AnimTrack(
            name = "Position",
            trackColor = Palette.TransformPosition,
            driver = Vec3PropertyDriver { bloomScene.cubePos = it },
            defaultValue = Vec3f.ZERO,
//            keyframes = mutableListOf(
//                Keyframe(0f, Vec3f(0f, -5f, 0f), Easing.easeOutBack),
//                Keyframe(1.5f, Vec3f(0f, 0f, 0f)),
//                Keyframe(3.0f, Vec3f(0f, 2f, 0f), Easing.easeInOutSine),
//                Keyframe(5.0f, Vec3f(0f, -1f, 0f), Easing.easeInOutSine),
//                Keyframe(7.0f, Vec3f(0f, 0f, 0f))
//            )
        )
        controller.addTrack("Transform", posTrack)

        val rotTrack = AnimTrack(
            name = "Rotation",
            trackColor = Palette.TransformRotation,
            driver = Vec3PropertyDriver { bloomScene.cubeRotation = it },
            defaultValue = Vec3f.ZERO,
//            keyframes = mutableListOf(
//                Keyframe(0f, Vec3f(0f, 0f, 0f), Easing.linear),
//                Keyframe(8f, Vec3f(360f, 720f, 180f))
//            )
        )
        controller.addTrack("Transform", rotTrack)

        val brightnessTrack = AnimTrack(
            name = "Box Brightness",
            trackColor = Palette.Audio,
            driver = FloatPropertyDriver { bloomScene.cubeBrightness = it },
            defaultValue = 2.2f,
//            keyframes = mutableListOf(
//                Keyframe(0f, 2.2f),
//                Keyframe(2f, 5.0f),
//                Keyframe(4f, 2.2f)
//            )
        )
        controller.addTrack("FX", brightnessTrack)

        val bloomTrack = AnimTrack(
            name = "Bloom Pulse",
            trackColor = Palette.Audio,
            driver = FloatPropertyDriver { bloomScene.bloomStrength = it },
            defaultValue = 1f,
//            keyframes = mutableListOf(
//                Keyframe(0f, 0.5f),
//                Keyframe(2f, 3.0f, Easing.easeOutExpo),
//                Keyframe(2.2f, 0.5f),
//                Keyframe(4f, 3.0f, Easing.easeOutExpo),
//                Keyframe(4.2f, 0.5f),
//                Keyframe(6f, 3.0f, Easing.easeOutExpo),
//                Keyframe(6.2f, 0.5f)
//            )
        )
        controller.addTrack("FX", bloomTrack)

        val camPanTrack = AnimTrack(
            name = "Pan (Look At)",
            trackColor = Palette.Camera,
            driver = Vec3PropertyDriver { bloomScene.camLookAt = it },
            defaultValue = Vec3f.ZERO,
//            keyframes = mutableListOf(
//                Keyframe(0f, Vec3f(0f, 0f, 0f)),
//                Keyframe(2f, Vec3f(2f, 0f, 0f), Easing.easeInOutQuad),
//                Keyframe(4f, Vec3f(-2f, 0f, 0f), Easing.easeInOutQuad),
//                Keyframe(6f, Vec3f(0f, 0f, 0f))
//            )
        )
        controller.addTrack("Camera", camPanTrack)

        val camZoomTrack = AnimTrack(
            name = "Zoom",
            trackColor = Palette.Camera,
            driver = FloatPropertyDriver { bloomScene.camZoom = it.toDouble() },
            defaultValue = 15f,
//            keyframes = mutableListOf(
//                Keyframe(0f, 15f, Easing.easeOutCubic),
//                Keyframe(2f, 5f),
//                Keyframe(6f, 5f, Easing.easeInOutQuad),
//                Keyframe(8f, 12f)
//            )
        )
        controller.addTrack("Camera", camZoomTrack)

        val camYawTrack = AnimTrack(
            name = "Rotation Y",
            trackColor = Palette.Camera,
            driver = FloatPropertyDriver { bloomScene.camHeading = it.toDouble() },
            defaultValue = 0f,
//            keyframes = mutableListOf(
//                Keyframe(0f, 0f, Easing.easeInOutQuad),
//                Keyframe(4f, 90f, Easing.easeInOutQuad),
//                Keyframe(8f, 0f)
//            )
        )
        controller.addTrack("Camera", camYawTrack)

        val camPitchTrack = AnimTrack(
            name = "Rotation X",
            trackColor = Palette.Camera,
            driver = FloatPropertyDriver { bloomScene.camPitch = it.toDouble() },
            defaultValue = 0f,
//            keyframes = mutableListOf(
//                Keyframe(0f, -30f),
//                Keyframe(3f, -10f, Easing.easeInOutSine),
//                Keyframe(6f, -60f, Easing.easeInOutSine),
//                Keyframe(8f, -30f)
//            )
        )
        controller.addTrack("Camera", camPitchTrack)

        val skyboxTrack = AnimTrack(
            name = "Skybox",
            trackColor = Palette.System,
            driver = FloatPropertyDriver { bloomScene.showSkybox = it },
            defaultValue = 0f,
//            keyframes = mutableListOf(
//                Keyframe(0f, 0f),
//                Keyframe(2f, 1f),
//                Keyframe(7f, 0f)
//            )
        )
        controller.addTrack("System", skyboxTrack)

        controller.onUpdate()
    }
}