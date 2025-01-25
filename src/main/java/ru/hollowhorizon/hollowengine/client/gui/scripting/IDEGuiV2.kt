@file:OptIn(ExperimentalSerializationApi::class)

package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.mojang.blaze3d.platform.NativeImage
import de.fabmax.kool.Assets
import de.fabmax.kool.input.PointerInput
import de.fabmax.kool.loadImage2d
import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import de.fabmax.kool.util.MsdfFont.Companion.MSDF_TEX_PROPS
import de.fabmax.kool.util.MsdfFontData
import de.fabmax.kool.util.MsdfMeta
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.client.renderer.texture.DynamicTexture
import ru.hollowhorizon.hc.client.kool.KoolScreen
import ru.hollowhorizon.hc.client.utils.json.JsonFormat
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.stream
import ru.hollowhorizon.hollowengine.client.gui.kool.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2.projectDock
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.FileTitleBar
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.ImageFileData
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.TextFileData
import ru.hollowhorizon.hollowengine.client.kool.dragItem

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

object IDEGuiV2 : KoolScreen({
    setupUiScene()

    val dock = Dock().apply {
        borderWidth.set(Dp.fromPx(1f))
        borderColor.set(UiColors.titleBg)
        dockingSurface.colors = ideColors
        dockingSurface.sizes = ideSizes
        dockingPaneComposable = Composable {
            Column(Grow.Std, Grow.Std) {
                modifier.margin(top = sizes.heightWindowTitleBar)
                divider(horizontalMargin = 0.dp, color = UiColors.titleBg)
                root()
            }
        }

        projectDock = UiDockable("Проект", this).apply { setFloatingBounds(height = Dp(100f)) }
        val projectSurface = WindowSurface(projectDock, ideColors, ideSizes) {
            Column(Grow.Std, Grow.Std) {
                FileTitleBar(projectDock)
                IDEGuiV2.fileTree()
            }
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
            .width(Grow.Std).height(sizes.heightWindowTitleBar)
        modifier.background(
            TitleBgRenderer(
                colors.backgroundMid, Color.CYAN, fade = TitleBgRenderer.fadeProps(
                    Vec2f(0f, 0f), 14f, 0.3f
                )
            )
        )

        IDETitleBar()
    }
    addPanelSurface(ideColors, ideSizes) {
        IDEGuiV2.dndContext.dragItem()?.let {

            Popup(PointerInput.primaryPointer.x.toFloat(), PointerInput.primaryPointer.y.toFloat()) {
                modifier.background(UiRenderer { node ->
                    node.apply {
                        getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                            .localRoundRect(0f, 0f, widthPx, heightPx, heightPx * 0.5f, colors.backgroundMid)
                        colors.primary.let {
                            getUiPrimitives(UiSurface.LAYER_BACKGROUND)
                                .localRoundRectBorder(0f, 0f, widthPx, heightPx, heightPx * 0.5f, sizes.borderWidth.px, it)
                        }
                    }
                }).padding(sizes.smallGap)

                Row {
                    val icon = when {
                        it.children.isNotEmpty() && it.isExpanded.value -> FOLDER_OPEN
                        it.children.isNotEmpty() && !it.isExpanded.value -> FOLDER
                        it.treeName.endsWith(".kts") -> KOTLIN
                        else -> FILE
                    }

                    Box {
                        modifier.alignY(AlignmentY.Center)
                        Image(icon) {
                            modifier.margin(horizontal = 10.dp).size(sizes.lineHeight, sizes.lineHeight)
                                .imageSize(ImageSize.Stretch)
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

    IDEGuiV2.dock = dock
}) {
    val files = arrayListOf<FileData>()
    var fileTree = FileNode.EMPTY

    val dndContext = DragAndDropContext<FileNode>()

    @JvmStatic
    lateinit var dock: Dock

    @JvmStatic
    lateinit var projectDock: UiDockable

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