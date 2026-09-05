package ru.hollowhorizon.hollowengine.client.ui.ide

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.client.editor.GizmoEditMode
import ru.hollowhorizon.hollowengine.client.editor.TransformGizmoEditor
import ru.hollowhorizon.hollowengine.client.ui.HollowUiResourceAccess
import ru.hollowhorizon.hollowengine.client.ui.UiProfiler
import ru.hollowhorizon.hollowengine.client.ui.docking.DockingState
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownItem
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownMark
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiDropdownSlider
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.network.ReloadServerResourcesPacket
import ru.hollowhorizon.hollowengine.common.utils.DesktopUtil
import ru.hollowhorizon.hollowengine.common.utils.openUrl

private const val ReloadIcon = "hollowengine:textures/gui/icons/reload.svg"
private const val ReformatIcon = "hollowengine:textures/gui/icons/code_editor.svg"
private const val SaveIcon = "hollowengine:textures/gui/icons/save.svg"
private const val DocsIcon = "hollowengine:textures/gui/icons/docs.svg"

internal fun hollowIdeFileMenuItems(
    model: HollowIdeModel,
    dock: DockingState,
    focusedFile: () -> HollowIdeOpenFile?,
    canReformat: (HollowIdeOpenFile) -> Boolean,
    onReformat: (HollowIdeOpenFile) -> Unit,
): List<UiDropdownItem> {
    val focused = focusedFile()
    val focusedPath = focused?.path
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
        UiDropdownItem(
            label = "Reformat Code",
            icon = ReformatIcon,
            enabled = focused != null && canReformat(focused),
        ) {
            focused?.let(onReformat)
        },
    )
}

internal fun hollowIdeToolMenuItems(
    context: HollowIdeContext,
    dock: DockingState,
    profiler: UiProfiler,
): List<UiDropdownItem> {
    return listOf(
        UiDropdownItem(
            label = "UI Profiler",
            checked = dock.contains(UiProfilerId),
            mark = UiDropdownMark.CHECKBOX,
            closeOnClick = false,
        ) {
            if (dock.contains(UiProfilerId)) {
                context.closePanel(UiProfilerId)
                profiler.enabled = false
            } else {
                context.openPanel(UiProfilerId)
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
                value = HollowIdeScale.guiScale,
                min = 0f,
                max = HollowIdeScale.MaxScale.toFloat(),
                step = 1f,
                valueLabel = { HollowIdeScale.label(it) },
                onCommit = { HollowIdeScale.guiScale = it },
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
