@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.Assets
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockLayout
import de.fabmax.kool.modules.ui2.docking.DockNode
import de.fabmax.kool.modules.ui2.docking.Dockable
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.*
import de.fabmax.kool.util.MsdfFont.Companion.MSDF_TEX_PROPS
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.utils.json.JsonFormat
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.stream
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.kool.UiColors
import ru.hollowhorizon.hollowengine.client.gui.kool.backgroundMid
import ru.hollowhorizon.hollowengine.client.gui.kool.lineHeight
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.LayoutLoader
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.DocFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DockPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DocsTreePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.FileTreePanel
import ru.hollowhorizon.hollowengine.client.kool.dragItem
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.docs.pages.WelcomePage

import com.akuleshov7.ktoml.Toml

val PT_SANS by lazy {
    val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>("hollowengine:fonts/pt_sans.json".rl.stream)
    val msdfMap = Texture2d(MSDF_TEX_PROPS, "MsdfFont:${fontInfo.name}") {
        Assets.loadImage2d("hollowengine:fonts/pt_sans.png", MSDF_TEX_PROPS).getOrThrow()
    }
    MsdfFontData(msdfMap, fontInfo)
}

val HACK_FONT by lazy {
    val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>("hollowengine:fonts/hack.json".rl.stream)
    val msdfMap = Texture2d(MSDF_TEX_PROPS, "MsdfFont:${fontInfo.name}") {
        Assets.loadImage2d("hollowengine:fonts/hack.png", MSDF_TEX_PROPS).getOrThrow()
    }
    MsdfFontData(msdfMap, fontInfo)
}

class IDEGuiV2 : KoolScreen({
    setupUiScene()

    val dock = Dock().apply {
        borderWidth.set(Dp.fromPx(1f))
        borderColor.set(UiColors.titleBg)
        dockingSurface.colors = ideColors
        dockingSurface.sizes = ideSizes
        dockingPaneComposable = Composable {
            Column(Grow.Std, Grow.Std) {
                modifier.margin(top = 10.dp)
                Box(width = Grow.Std, height = sizes.borderWidth) { modifier.backgroundColor(UiColors.titleBg) }
                divider(horizontalMargin = 0.dp, color = colors.backgroundMid)
                root()
            }
        }

        val projectDock = FileTreePanel(this)
        val docsDock = DocsTreePanel(this)
        panels[projectDock.dockable] = projectDock
        panels[docsDock.dockable] = docsDock

        LayoutLoader.loadIdeLayout(this) { name ->
            when (name) {
                projectDock.name -> projectDock.dockable
                docsDock.name -> docsDock.dockable
                else -> null
            }
        }
    }

    addNode(dock)
    addPanelSurface(ideColors, ideSizes) {
        modifier.alignY(AlignmentY.Top).size(Grow.Std, 10.dp)

        IDETitleBar()

        IDEStorage.dndContext.dragItem()?.let {

            Popup(PointerInput.primaryPointer.x.toFloat(), PointerInput.primaryPointer.y.toFloat()) {
                modifier.background(UiRenderer { node ->
                    node.apply {
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                            .localRoundRect(0f, 0f, widthPx, heightPx, heightPx * 0.5f, colors.backgroundMid)
                        colors.primary.let {
                            getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                                .localRoundRectBorder(
                                    0f,
                                    0f,
                                    widthPx,
                                    heightPx,
                                    heightPx * 0.5f,
                                    sizes.borderWidth.px,
                                    it
                                )
                        }
                    }
                }).padding(sizes.smallGap)

                Row {
                    it.apply {
                        val icon = getIcon(this)


                        Box {
                            modifier.alignY(AlignmentY.Center)
                            Image(icon) {
                                modifier.alignY(AlignmentY.Center).size(sizes.lineHeight, sizes.lineHeight)
                                    .imageSize(ImageSize.Stretch)
                            }
                        }
                    }

                    Box {
                        modifier.size(Grow.Std, Grow.Std)
                        Text(it.treeName) {
                            modifier
                                .alignY(AlignmentY.Center)
                                .textColor(colors.primary)
                        }
                    }
                }
            }
        }
        surface.triggerUpdate()
    }

    IDEStorage.dock = dock
}) {

    init {
        load()
    }

    override fun shouldCloseOnEsc(): Boolean {
        return IDEStorage.files.values.filterIsInstance<TextFileData>().all { it.modifier.completions.isEmpty() }
    }

    override fun onClose() {
        super.onClose()
        DockLayout.saveLayout(IDEStorage.dock, LayoutLoader.IDE_LAYOUT)
    }

    override fun isPauseScreen() = false

}

val panels = HashMap<Dockable, DockPanel>()

var ideColors = Colors.darkColors(
    background = Color("232933EE"),
    backgroundVariant = Color("161a2088"),
    onBackground = Color("dbe6ffff"),
    secondary = Color("7786a5ff"),
    secondaryVariant = Color("4d566bff"),
    onSecondary = Color.WHITE
)

fun load() {
    val configFile = DirectoryManager.HOLLOW_ENGINE.resolve("IdeStile.toml").toFile()

    if(!configFile.exists()) {
        configFile.writeText(Toml.encodeToString(ColorsSerializer, ideColors))
    } else {
        ideColors = Toml.decodeFromString(ColorsSerializer, configFile.readText())
    }
}

val ideSizes = Sizes.small.copy(normalText = MsdfFont(PT_SANS, 10f))
