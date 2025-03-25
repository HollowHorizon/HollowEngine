package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.KoolApplication
import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.kool.minecraft.MCAssetLoader
import ru.hollowhorizon.hc.common.events.EventBus
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.loadLayouts
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.TitleBarCreationEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.leftBarContents
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.rightBarContents

fun main() = KoolApplication(KoolConfigJvm(defaultAssetLoader = MCAssetLoader, windowSize = Vec2i(720, 480))) {
    EventBus.register(::leftBarContents)
    EventBus.register(::rightBarContents)
    EventBus.register(::loadLayouts)
    val gui = ScriptingEnvironmentScreen()
    gui.load()
    gui.scene.isVisible = true
    ctx.addScene(gui.scene)
}

class ScriptingEnvironmentScreen : KoolScreen() {
    val dock = Dock()

    override fun Scene.setup() {
        setupUiScene(clearColor = ClearColorFill(Color.BLACK))

        var titleBarHeight = 0f

        addPanelSurface(sizes = IdeTheme.sizes, colors = IdeTheme.colors) {
            modifier.size(Grow.Std, FitContent)

            Row(width = Grow.Std) {
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
        addNode(dock)
    }

    fun load() = init()
}