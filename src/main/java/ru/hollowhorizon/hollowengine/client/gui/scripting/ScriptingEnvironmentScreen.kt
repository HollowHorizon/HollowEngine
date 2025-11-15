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
    val dock = Dock()
    var overlay: UiScope.() -> Unit = {}
    var titleBarHeight = 0f
    var isCollapsed = true
        set(value) {
            field = value
            dock.isVisible = !value
        }

    val scene = Scene("IDE Overlay").apply {
        setupUiScene()
        clearColor = ClearColorDontCare
        clearDepth = ClearDepthDontCare
        setup()
    }


    fun Scene.setup() {
        setupUiScene()

        dock.isVisible = false
        addNode(dock)
        addPanelSurface(sizes = IdeTheme.sizes, colors = IdeTheme.colors) {
            modifier.size(if (isCollapsed) FitContent else Grow.Std, FitContent)
            if (isCollapsed) modifier.background(null)
            Row(width = if (isCollapsed) FitContent else Grow.Std) {
                modifier.margin(sizes.smallGap)

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
            borderWidth.set(IdeTheme.sizes.borderWidth)
            borderColor.set(Color("3C3C4AFF"))
            dockingSurface.sizes = IdeTheme.sizes
            dockingSurface.colors = IdeTheme.colors
            dockingPaneComposable = Composable {
                Column(Grow.Std, Grow.Std) {
                    modifier.margin(top = Dp.fromPx(titleBarHeight))

                    Box(width = Grow.Std, height = sizes.borderWidth) { modifier.backgroundColor(Color("3C3C4AFF")) }
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
    return (dock.dockables.values.any { it.isInBounds(Vec2f(x, y)) } || y <= ScriptingEnvironmentOverlay.titleBarHeight) && dock.isVisible
}

fun isAnyFocusNodeInput(): Boolean {
    return dock.dockables.keys.any { it.inputHandler.blockAllKeyboardInput }
}

@SubscribeEvent
fun onDrawOverlay(event: RenderTickEvent.Post) {
    ScriptingEnvironmentOverlay.scene.render()
}