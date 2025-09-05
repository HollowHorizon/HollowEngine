package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.Assets
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.pipeline.MipMapping
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.SingleColorTexture
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFontData
import de.fabmax.kool.util.MsdfMeta
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.kool.KoolScreen
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.TitleBarCreationEvent
import ru.hollowhorizon.hollowengine.client.utils.stream
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

class ScriptingEnvironmentScreen : KoolScreen() {
    val dock = Dock()
    var overlay: UiScope.() -> Unit = {}

    override fun Scene.setup() {
        setupUiScene()

        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            addPanelSurface(sizes = IdeTheme.sizes, colors = IdeTheme.colors) { ServerIdeWarning() }
            return
        }

        var titleBarHeight = 0f

        addNode(dock)
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

    override fun onClose() {
        super.onClose()
        DockLayout.saveLayout(dock, LayoutLoader.IDE_LAYOUT)
        IdeContent.files.clear()
    }

    override fun shouldCloseOnEsc(): Boolean {
        return IdeContent.files.values.filterIsInstance<TextFileData>().find {
            it.surface.isFocused.value
        }?.modifier?.completions?.isEmpty() ?: true
    }

    override fun isPauseScreen() = false
}