package ru.hollowhorizon.hollowengine.client.ui.ide

import ru.hollowhorizon.hollowengine.client.ui.docking.DockItem
import ru.hollowhorizon.hollowengine.client.ui.docking.DockPlacement
import ru.hollowhorizon.hollowengine.client.ui.docking.DockSingleTabPresentation
import ru.hollowhorizon.hollowengine.client.ui.docking.DockTarget
import ru.hollowhorizon.hollowengine.client.ui.docking.DockingState
import ru.hollowhorizon.hollowengine.client.ui.ide.asset.AssetManagerLang
import ru.hollowhorizon.hollowengine.client.utils.lang

/** Where a tool window docks when it is opened: next to the first of these that is on screen. */
internal enum class ToolWindowAnchor {
    EDITORS,
    PROJECT,
    TIMELINE,
}

/**
 * A window the IDE can open from the Windows menu. Its placement is declared once here instead of
 * being repeated at every place that opens it.
 */
internal class HollowIdeToolWindow(
    val id: String,
    val titleKey: String,
    val icon: String,
    val placement: DockPlacement,
    val anchors: List<ToolWindowAnchor>,
    val minWidth: Float = 48f,
    val minHeight: Float = 32f,
    val closable: Boolean = true,
    val singleTabPresentation: DockSingleTabPresentation = DockSingleTabPresentation.BAR,
) {
    val title: String get() = titleKey.lang

    fun dockItem(): DockItem = DockItem(
        id = id,
        title = title,
        icon = icon,
        closable = closable,
        minWidth = minWidth,
        minHeight = minHeight,
        singleTabPresentation = singleTabPresentation,
        pinnable = true,
    )
}

internal object HollowIdeToolWindows {
    val Project = HollowIdeToolWindow(
        id = ProjectTreeId,
        titleKey = "hollowengine.gui.ide.project_tree",
        icon = ProjectIcon,
        placement = DockPlacement.LEFT,
        anchors = listOf(ToolWindowAnchor.EDITORS),
    )
    val AssetManager = HollowIdeToolWindow(
        id = AssetManagerId,
        titleKey = AssetManagerLang.TITLE,
        icon = AssetManagerIcon,
        placement = DockPlacement.RIGHT,
        anchors = listOf(ToolWindowAnchor.PROJECT, ToolWindowAnchor.EDITORS),
        minWidth = 260f,
        minHeight = 130f,
    )
    val Console = HollowIdeToolWindow(
        id = ConsoleId,
        titleKey = "hollowengine.gui.ide.console",
        icon = ConsoleIcon,
        placement = DockPlacement.BOTTOM,
        anchors = listOf(ToolWindowAnchor.EDITORS, ToolWindowAnchor.PROJECT),
        minWidth = 180f,
        minHeight = 90f,
    )
    val CutsceneTimeline = HollowIdeToolWindow(
        id = CutsceneTimelineId,
        titleKey = "hollowengine.gui.ide.windows.cutscene_timeline",
        icon = CutsceneIcon,
        placement = DockPlacement.BOTTOM,
        anchors = listOf(ToolWindowAnchor.EDITORS, ToolWindowAnchor.PROJECT),
        minWidth = 260f,
        minHeight = 130f,
    )
    val Inspector = HollowIdeToolWindow(
        id = InspectorId,
        titleKey = "hollowengine.gui.ide.windows.inspector",
        icon = OptionsIcon,
        placement = DockPlacement.RIGHT,
        anchors = listOf(ToolWindowAnchor.EDITORS, ToolWindowAnchor.TIMELINE, ToolWindowAnchor.PROJECT),
        minWidth = 120f,
        minHeight = 130f,
    )
    val GameViewport = HollowIdeToolWindow(
        id = GameViewportId,
        titleKey = "hollowengine.gui.ide.windows.game_viewport",
        icon = CutsceneIcon,
        placement = DockPlacement.TOP,
        anchors = listOf(ToolWindowAnchor.TIMELINE, ToolWindowAnchor.EDITORS, ToolWindowAnchor.PROJECT),
        minWidth = 160f,
        minHeight = 90f,
        singleTabPresentation = DockSingleTabPresentation.OVERLAY,
    )
    val UiProfiler = HollowIdeToolWindow(
        id = UiProfilerId,
        titleKey = "hollowengine.gui.ide.tools.ui_profiler",
        icon = OptionsIcon,
        placement = DockPlacement.BOTTOM,
        anchors = listOf(ToolWindowAnchor.TIMELINE, ToolWindowAnchor.PROJECT),
        minWidth = 180f,
        minHeight = 130f,
    )

    val menu: List<HollowIdeToolWindow?> = listOf(
        Project, Inspector, AssetManager, Console,
        null,
        CutsceneTimeline, GameViewport,
    )

    /** Every window the editor knows how to open, for turning a remembered id back into one. */
    val all: List<HollowIdeToolWindow> = listOf(
        Project, AssetManager, Console, CutsceneTimeline, Inspector, GameViewport, UiProfiler,
    )

    fun byId(id: String): HollowIdeToolWindow? = all.firstOrNull { it.id == id }
}

/** Opens [window] beside its preferred neighbour, or just focuses it if it is already open. */
internal fun DockingState.openToolWindow(window: HollowIdeToolWindow, model: HollowIdeModel) {
    if (contains(window.id)) {
        focus(window.id)
        return
    }
    val anchor = window.anchors.firstNotNullOfOrNull { anchor ->
        when (anchor) {
            ToolWindowAnchor.EDITORS -> model.files.values.firstOrNull { contains(it.id) }?.id
            ToolWindowAnchor.PROJECT -> ProjectTreeId.takeIf(::contains)
            ToolWindowAnchor.TIMELINE -> CutsceneTimelineId.takeIf(::contains)
        }
    }
    open(window.dockItem(), DockTarget(anchor, window.placement))
}
