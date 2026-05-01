package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.pipeline.*
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFontData
import de.fabmax.kool.util.MsdfMeta
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay.dock
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.TitleBarCreationEvent
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.client.utils.stream
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderTickEvent
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.utils.json.JsonFormat
import ru.hollowhorizon.hollowengine.common.utils.rl

val HACK_FONT by lazy {
    val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>("hollowengine:fonts/hack.json".rl.stream)
    val msdfMap = Texture2d(TexFormat.RGBA, MipMapping.Off, SamplerSettings(), "MsdfFont:${fontInfo.name}") {
        Assets.loadImage2d("hollowengine:fonts/hack.png")
            .getOrDefault(SingleColorTexture.getColorTextureData(Color.BLACK))
    }
    MsdfFontData(msdfMap, fontInfo)
}

object ScriptingEnvironmentOverlay {
    val dock: Dock
    val scene = Scene("IDE Overlay").apply {
        setupUiScene()
        clearColor = ClearColorDontCare
        clearDepth = ClearDepthDontCare
        depthMode = DepthMode.Legacy
        dock = Dock(this)
        setup()
    }
    var overlay: UiScope.() -> Unit = {}
    var titleBarHeight = 0f
    var isCollapsed = true
        set(value) {
            field = value
            if(value) scene.removeNode(dock)
            else scene.addNode(dock, 0)
        }


    fun Scene.setup() {
        setupUiScene()

        addPanelSurface(sizes = IdeTheme.sizes, colors = IdeTheme.colors) {
            modifier.size(if (isCollapsed) FitContent else Grow.Std, FitContent)
            if (isCollapsed) modifier.background(null)
            else modifier.backgroundColor(ColorTheme.UI.BackgroundSecondary)

            Row(width = if (isCollapsed) FitContent else Grow.Std) {
                TitleBarCreationEvent.Start(this).post()
                Box { modifier.alignX(AlignmentX.Center).width(Grow.Std) }
                TitleBarCreationEvent.Center(this).post()
                Box { modifier.alignX(AlignmentX.End).width(Grow.Std) }
                TitleBarCreationEvent.End(this).post()
            }

            modifier.onPositioned {
                titleBarHeight = it.bottomPx
            }

            overlay()

            surface.triggerUpdate()
        }

        dock.apply {
            borderWidth.set(Dimensions.PaddingNormal)
            borderColor.set(ColorTheme.UI.BackgroundGeneral)
            dockingSurface.sizes = IdeTheme.sizes
            dockingSurface.colors = IdeTheme.colors
            dockingPaneComposable = Composable {
                Column(Grow.Std, Grow.Std) {
                    modifier.margin(top = Dp.fromPx(titleBarHeight))
                    root()
                }
            }
        }
        LayoutLoader.loadIdeLayout(dock)
    }

//    override fun onClose() {
//        super.onClose()
//        DockLayout.saveLayout(dock, LayoutLoader.IDE_LAYOUT)
//        IdeContent.files.clear()
//    }
}

fun isMouseOverDock(x: Float, y: Float): Boolean {
    if(ScriptingEnvironmentOverlay.isCollapsed) return false
    return (dock.dockables.values.any { it.isInBounds(Vec2f(x, y)) } || y <= ScriptingEnvironmentOverlay.titleBarHeight) && dock.isVisible
}

fun isAnyFocusNodeInput(): Boolean {
    return dock.dockables.keys.any { it.inputHandler.blockAllKeyboardInput }
}

@SubscribeEvent
fun onDrawOverlay(event: RenderTickEvent.Blit) {
    ScriptingEnvironmentOverlay.scene.render()
}