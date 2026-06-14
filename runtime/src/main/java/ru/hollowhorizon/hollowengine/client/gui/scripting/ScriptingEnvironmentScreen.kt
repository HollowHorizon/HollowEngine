package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.pipeline.ClearColorDontCare
import de.fabmax.kool.pipeline.ClearDepthDontCare
import de.fabmax.kool.pipeline.DepthMode
import de.fabmax.kool.scene.Scene
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ChatScreen
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentOverlay.dock
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.TitleBarCreationEvent
import ru.hollowhorizon.hollowengine.client.kool.gl.render
import ru.hollowhorizon.hollowengine.common.config.EditMode
import ru.hollowhorizon.hollowengine.common.config.HollowEngineConfig
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderTickEvent

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
            if (value) scene.removeNode(dock)
            else scene.addNode(dock, 0)
        }


    fun Scene.setup() {
        setupUiScene()

        addPanelSurface(sizes = IdeTheme.sizes, colors = IdeTheme.colors) {
            modifier.size(if (isCollapsed) FitContent else Grow.Std, FitContent)

            val isCollapsed = isCollapsed || HollowEngineConfig.editMode == EditMode.DISABLED ||
                    HollowEngineConfig.editMode == EditMode.CHAT_ONLY && Minecraft.getInstance().screen !is ChatScreen

            if (isCollapsed) modifier.background(null)
            else modifier.backgroundColor(ColorTheme.UI.BackgroundSecondary)

            Row(width = if (isCollapsed) FitContent else Grow.Std) {
                TitleBarCreationEvent.Start.post(TitleBarCreationEvent.Start(this))
                Box { modifier.alignX(AlignmentX.Center).width(Grow.Std) }
                TitleBarCreationEvent.Center.post(TitleBarCreationEvent.Center(this))
                Box { modifier.alignX(AlignmentX.End).width(Grow.Std) }
                TitleBarCreationEvent.End.post(TitleBarCreationEvent.End(this))
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
    if (HollowIdeOverlay.isMouseOver(x, y)) return true
    if (ScriptingEnvironmentOverlay.isCollapsed) return false
    return (dock.dockables.values.any {
        it.isInBounds(
            Vec2f(
                x,
                y
            )
        )
    } || y <= ScriptingEnvironmentOverlay.titleBarHeight) && dock.isVisible
}

fun isAnyFocusNodeInput(): Boolean {
    if (HollowIdeOverlay.hasFocusedInput()) return true
    return dock.dockables.keys.any { it.inputHandler.blockAllKeyboardInput }
}

@SubscribeEvent
@ClientOnly
fun onDrawOverlay(event: RenderTickEvent.Blit) {
    if (HollowIdeOverlay.isVisible()) return
    ScriptingEnvironmentOverlay.scene.render()
}
