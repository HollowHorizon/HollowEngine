package ru.hollowhorizon.hollowengine.docs

import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.UiDockable
import de.fabmax.kool.scene.Scene
import de.fabmax.kool.util.*
import ru.hollowhorizon.hollowengine.docs.pages.PageRegistry

val DOCS_GENERATOR: Scene.() -> Unit = {
    setupUiScene()

    launchOnMainThread {

        val ideColors = Colors.darkColors(
            background = Color("232933ff"),
            backgroundVariant = Color("161a20ff"),
            onBackground = Color("dbe6ffff"),
            secondary = Color("7786a5ff"),
            secondaryVariant = Color("4d566bff"),
            onSecondary = Color.WHITE
        )
        val ideSizes = Sizes.medium.copy(normalText = MsdfFont("fonts/pt_sans").getOrThrow())

        val dock = Dock().apply {
            borderWidth.set(Dp.fromPx(1f))
            borderColor.set(UiColors.titleBg)
            dockingSurface.colors = ideColors
            dockingSurface.sizes = ideSizes
            dockingPaneComposable = Composable {
                clearColor = ClearColorFill(Color.BLACK)
                root()
            }

            val root = DocsNode("HollowEngine", "hollowengine")
            PageRegistry.loadPages(root)

            val projectDock = UiDockable("Проект", this).apply { setFloatingBounds(height = Dp(100f)) }
            val projectSurface = WindowSurface(projectDock, ideColors, ideSizes) {
                root()
            }

            val pageDock = UiDockable("Page", this).apply { setFloatingBounds(height = Dp(100f)) }
            val pageSurface = WindowSurface(pageDock, ideColors, ideSizes) {
                logI { (PageRegistry.currentPage == null).toString() }
                PageRegistry.currentPage?.apply { compose() } ?: run {
                    Text("Open any page.") {
                        modifier.align(AlignmentX.Center, AlignmentY.Center)
                    }
                }
            }

            addDockableSurface(projectDock, projectSurface)
            addDockableSurface(pageDock, pageSurface)

            createNodeLayout(
                listOf(
                    "0:row",
                    "0/0:leaf",
                    "0/1:leaf"
                )
            )

            getLeafAtPath("0/0")?.dock(projectDock)
            getLeafAtPath("0/1")?.dock(pageDock)

            PageRegistry.setPage = { pageSurface.triggerUpdate() }
        }
        addNode(dock)
    }
}

private object UiColors {
    val border = Color("0f1114ff")
    val titleBg = Color("343a49ff")
    val windowTitleBgAccent = MdColor.DEEP_PURPLE
    val titleBgAccent = MdColor.DEEP_PURPLE
    val titleText = Color("dbe6ffff")
    val secondaryBright = Color("a0b3d8ff")
    val selectionChild = Color("ff7b0080")
}


val Sizes.lineHeight: Dp get() = baseSize * (2f / 3f)
val Sizes.baseSize: Dp get() = largeGap * 2f
val Sizes.lineHeightLarge: Dp get() = baseSize * 0.9f
val Sizes.heightTitleBar: Dp get() = lineHeightLarge
val Sizes.heightWindowTitleBar: Dp get() = heightTitleBar * 1.1f
val Sizes.scrollbarWidth: Dp get() = gap * 0.33f

val Sizes.editorPanelMarginStart: Dp get() = gap * 1.5f
val Sizes.editorPanelMarginEnd: Dp get() = gap

val Colors.hoverBg: Color get() = secondaryVariantAlpha(0.35f)
val Colors.backgroundMid: Color get() = background.mix(backgroundVariant, 0.5f)
val Colors.weakDividerColor: Color get() = secondaryVariantAlpha(0.75f)