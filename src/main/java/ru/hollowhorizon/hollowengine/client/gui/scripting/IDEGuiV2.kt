package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.platform.NativeImage
import de.fabmax.kool.Assets
import de.fabmax.kool.editor.ui.UiColors
import de.fabmax.kool.editor.ui.heightTitleBar
import de.fabmax.kool.editor.ui.heightWindowTitleBar
import de.fabmax.kool.editor.ui.scrollbarWidth
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.pipeline.AsyncTextureLoader
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.MsdfFont.Companion.MSDF_TEX_PROPS
import de.fabmax.kool.util.MsdfFontData
import de.fabmax.kool.util.MsdfMeta
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.client.renderer.texture.DynamicTexture
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.utils.json.JsonFormat
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.stream
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData

val PT_SANS by lazy {
    val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>("hollowengine:fonts/pt_sans.json".rl.stream)
    val msdfMap = Texture2d(
        props = MSDF_TEX_PROPS,
        name = "MsdfFont:${fontInfo.name}",
        loader = AsyncTextureLoader { Assets.loadTextureData("hollowengine:fonts/pt_sans.png", MSDF_TEX_PROPS) }
    )
    MsdfFontData(msdfMap, fontInfo)
}
val HACK_FONT by lazy {
    val fontInfo = JsonFormat.decodeFromStream<MsdfMeta>("hollowengine:fonts/hack.json".rl.stream)
    val msdfMap = Texture2d(
        props = MSDF_TEX_PROPS,
        name = "MsdfFont:${fontInfo.name}",
        loader = AsyncTextureLoader { Assets.loadTextureData("hollowengine:fonts/hack.png", MSDF_TEX_PROPS) }
    )
    MsdfFontData(msdfMap, fontInfo)
}

object IDEGuiV2 : KoolScreen({
    setupUiScene()

    val dock = Dock().apply {
        borderWidth.set(Dp.fromPx(1f))
        borderColor.set(UiColors.titleBg)
        dockingSurface.colors = ideColors
        dockingSurface.sizes = ideSizes
        dockingPaneComposable = Composable {
            resizeMargin.set(sizes.scrollbarWidth)
            Column(Grow.Std, Grow.Std) {
                modifier.margin(top = sizes.heightWindowTitleBar)
                root()
            }
        }

        val projectDock = UiDockable("Проект", this).apply { setFloatingBounds(height = Dp(100f)) }
        val projectSurface = WindowSurface(projectDock, ideColors, ideSizes) {
            IDEGuiV2.fileTree()
        }

        addDockableSurface(projectDock, projectSurface)

        createNodeLayout(
            listOf(
                "0:row",
                "0/0:leaf",
                "0/1:leaf"
            )
        )

        getLeafAtPath("0/0")?.dock(projectDock)
    }

    addNode(dock)
    addPanelSurface(ideColors, ideSizes) {
        modifier.alignY(AlignmentY.Top)
            .width(Grow.Std)
        IDETitleBar()
    }

    IDEGuiV2.dock = dock
}) {
    val files = linkedSetOf<FileData>()
    var fileTree = TreeNode.EMPTY

    @JvmStatic
    lateinit var dock: Dock

    fun openFile(path: String, bytes: ByteArray, type: FileType) {
        val file = when (type) {
            FileType.TEXT -> TextFileData(this, path.substringAfterLast('/'), path, String(bytes))
            FileType.IMAGE -> ImageFileData(
                this,
                path.substringAfterLast('/'),
                path,
                DynamicTexture(NativeImage.read(bytes))
            )
        }

        files.add(file)

        dock.addDockableSurface(file.dockable, file.surface)
        dock.getLeafAtPath("0/1")?.dock(file.dockable)
    }

    enum class ModalAction {
        CREATE_FILE, CREATE_FOLDER, RENAME, NONE
    }

    override fun shouldCloseOnEsc(): Boolean {
        return files.filterIsInstance<TextFileData>().all { it.modifier.completions.isEmpty() }
    }
}

val ideColors = Colors.darkColors(
    background = Color("232933ff"),
    backgroundVariant = Color("161a20ff"),
    onBackground = Color("dbe6ffff"),
    secondary = Color("7786a5ff"),
    secondaryVariant = Color("4d566bff"),
    onSecondary = Color.WHITE
)
val ideSizes = Sizes.medium.copy(normalText = MsdfFont(PT_SANS, 30f))
