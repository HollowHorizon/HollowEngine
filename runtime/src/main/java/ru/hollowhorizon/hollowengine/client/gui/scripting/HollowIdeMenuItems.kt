package ru.hollowhorizon.hollowengine.client.gui.scripting

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.editor.GizmoEditMode
import ru.hollowhorizon.hollowengine.client.editor.TransformGizmoEditor
import ru.hollowhorizon.hollowengine.client.gui.scripting.titlebar.ReloadServerResourcesPacket
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import ru.hollowhorizon.hollowengine.client.ui.UiProfiler
import ru.hollowhorizon.hollowengine.client.ui.docking.DockItem
import ru.hollowhorizon.hollowengine.client.ui.docking.DockPlacement
import ru.hollowhorizon.hollowengine.client.ui.docking.DockTarget
import ru.hollowhorizon.hollowengine.client.ui.docking.DockingState
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownMark
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownSlider
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.util.DesktopUtil
import ru.hollowhorizon.hollowengine.common.utils.openUrl

private const val ReloadIcon = "hollowengine:textures/gui/icons/reload.svg"
private const val SaveIcon = "hollowengine:textures/gui/icons/save.svg"
private const val DocsIcon = "hollowengine:textures/gui/icons/docs.svg"
private const val OptionsIcon = "hollowengine:textures/gui/icons/options.svg"

internal fun hollowIdeFileMenuItems(
    model: HollowIdeModel,
    dock: DockingState,
    focusedFile: () -> HollowIdeOpenFile?,
): List<UiDropdownItem> {
    val focusedPath = focusedFile()?.path
    return listOf(
        UiDropdownItem("hollowengine.gui.ide.file.reload_client_resources".lang, ReloadIcon) {
            HollowUiResourceAccess.clearCache()
            Minecraft.getInstance().reloadResourcePacks()
        },
        UiDropdownItem("hollowengine.gui.ide.file.reload_server_resources".lang, ReloadIcon) {
            ReloadServerResourcesPacket().send()
        },
        UiDropdownItem("hollowengine.gui.ide.file.open_mod_folder".lang, LogoIcon) {
            DesktopUtil.openInExplorer(DirectoryManager.HOLLOW_ENGINE.toFile())
        },
        UiDropdownItem("Save", SaveIcon, enabled = focusedPath != null) {
            focusedPath?.let(model::save)
            focusedFile()?.let { dock.updateItem(it.dockItem()) }
        },
        UiDropdownItem("Save All", SaveIcon, enabled = model.files.values.any { it.dirty }) {
            model.saveAll()
            model.files.values.forEach { dock.updateItem(it.dockItem()) }
        },
    )
}

internal fun hollowIdeWindowMenuItems(model: HollowIdeModel, dock: DockingState): List<UiDropdownItem> {
    return listOf(
        UiDropdownItem("hollowengine.gui.ide.project_tree".lang, ProjectIcon) {
            if (!dock.contains(ProjectTreeId)) {
                dock.open(DockItem(ProjectTreeId, "hollowengine.gui.ide.project_tree".lang, ProjectIcon, closable = false))
            }
            dock.focus(ProjectTreeId)
        },
        UiDropdownItem("Code Editor", CodeIcon) {
            if (!dock.contains(EditorWelcomeId) && model.files.values.none { dock.contains(it.id) }) {
                dock.open(DockItem(EditorWelcomeId, "Code Editor", CodeIcon, closable = false))
            }
            dock.focus(model.files.values.firstOrNull { dock.contains(it.id) }?.id ?: EditorWelcomeId)
        },
        UiDropdownItem("Cutscene Timeline", CutsceneIcon) {
            if (!dock.contains(CutsceneTimelineId)) {
                val anchor = model.files.values.firstOrNull { dock.contains(it.id) }?.id ?: EditorWelcomeId
                dock.open(
                    DockItem(CutsceneTimelineId, "Cutscene Timeline", CutsceneIcon, closable = true, minWidth = 520f, minHeight = 260f),
                    DockTarget(anchor, DockPlacement.BOTTOM),
                )
            }
            dock.focus(CutsceneTimelineId)
        },
        UiDropdownItem("Cutscene Properties", OptionsIcon) {
            if (!dock.contains(CutscenePropertiesId)) {
                val anchor = if (dock.contains(CutsceneTimelineId)) {
                    CutsceneTimelineId
                } else {
                    model.files.values.firstOrNull { dock.contains(it.id) }?.id ?: EditorWelcomeId
                }
                dock.open(
                    DockItem(CutscenePropertiesId, "Cutscene Properties", OptionsIcon, closable = true, minWidth = 240f, minHeight = 260f),
                    DockTarget(anchor, DockPlacement.RIGHT),
                )
            }
            dock.focus(CutscenePropertiesId)
        },
    )
}

internal fun hollowIdeToolMenuItems(dock: DockingState, profiler: UiProfiler): List<UiDropdownItem> {
    return listOf(
        UiDropdownItem(
            label = "UI Profiler",
            checked = dock.contains(UiProfilerId),
            mark = UiDropdownMark.CHECKBOX,
            closeOnClick = false,
        ) {
            if (dock.contains(UiProfilerId)) {
                dock.close(UiProfilerId)
                profiler.enabled = false
            } else {
                val anchor = if (dock.contains(CutsceneTimelineId)) CutsceneTimelineId else EditorWelcomeId
                dock.open(
                    DockItem(UiProfilerId, "UI Profiler", OptionsIcon, closable = true, minWidth = 360f, minHeight = 260f),
                    DockTarget(anchor, DockPlacement.BOTTOM),
                )
                dock.focus(UiProfilerId)
                profiler.enabled = true
            }
        },
        UiDropdownItem(
            label = "hollowengine.gui.ide.gizmo".lang,
            checked = TransformGizmoEditor.isEnabled,
            mark = UiDropdownMark.CHECKBOX,
            closeOnClick = false,
        ) {
            TransformGizmoEditor.toggleEnabled()
        },
        UiDropdownItem(
            label = "Translate",
            checked = TransformGizmoEditor.mode == GizmoEditMode.TRANSLATE,
            mark = UiDropdownMark.RADIO,
            closeOnClick = false,
        ) {
            TransformGizmoEditor.setMode(GizmoEditMode.TRANSLATE)
        },
        UiDropdownItem(
            label = "Rotate",
            checked = TransformGizmoEditor.mode == GizmoEditMode.ROTATE,
            mark = UiDropdownMark.RADIO,
            closeOnClick = false,
        ) {
            TransformGizmoEditor.setMode(GizmoEditMode.ROTATE)
        },
        UiDropdownItem(
            label = "Scale",
            checked = TransformGizmoEditor.mode == GizmoEditMode.SCALE,
            mark = UiDropdownMark.RADIO,
            closeOnClick = false,
        ) {
            TransformGizmoEditor.setMode(GizmoEditMode.SCALE)
        },
        UiDropdownItem(
            label = "hollowengine.gui.ide.gui_scale".lang,
            slider = UiDropdownSlider(
                value = HollowIdeScale.guiScale.toFloat(),
                min = 0f,
                max = HollowIdeScale.MaxScale.toFloat(),
                step = 1f,
                valueLabel = { HollowIdeScale.label(it.toInt()) },
                onCommit = { HollowIdeScale.guiScale = it.toInt() },
            ),
            closeOnClick = false,
        ),
    )
}

internal fun hollowIdeHelpMenuItems(): List<UiDropdownItem> {
    return listOf(
        UiDropdownItem("Telegram", DocsIcon) { openUrl("https://t.me/hollowengine") },
        UiDropdownItem("Discord", DocsIcon) { openUrl("https://discord.gg/qKpPhkwGCY") },
    )
}
