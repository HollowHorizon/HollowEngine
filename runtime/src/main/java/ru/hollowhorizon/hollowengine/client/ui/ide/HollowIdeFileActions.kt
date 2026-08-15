package ru.hollowhorizon.hollowengine.client.ui.ide

/**
 * One entry of the menu a right click on a file tab opens. A file type contributes its own on top
 * of [HollowIdeStandardFileActions], so an editor can offer what only it can do without the IDE
 * knowing about it.
 */
class HollowIdeFileAction(
    val id: String,
    val label: String,
    val shortcut: String = "",
    val icon: String? = null,
    val separatorBefore: Boolean = false,
    val isVisible: (HollowIdeFileActionContext) -> Boolean = { true },
    val isEnabled: (HollowIdeFileActionContext) -> Boolean = { true },
    val run: (HollowIdeFileActionContext) -> Unit,
) {
    init {
        require(id.isNotBlank()) { "File action ID cannot be blank" }
    }
}

interface HollowIdeFileActionContext {
    val file: HollowIdeOpenFile

    val canFormat: Boolean

    fun save(): Boolean
    fun close()
    fun closeOthers()
    fun closeAll()
    fun reformat()
    fun revealInProjectView()
    fun showInExplorer()
    fun copyPath()
    fun setStatus(message: String)
}

/** The actions every editor tab offers, in the order the menu shows them. */
object HollowIdeStandardFileActions {
    const val SaveId = "save"
    const val ReformatId = "reformat"
    const val CloseId = "close"
    const val CloseOthersId = "close-others"
    const val CloseAllId = "close-all"
    const val CopyPathId = "copy-path"
    const val RevealId = "reveal"
    const val ShowInExplorerId = "show-in-explorer"

    val actions: List<HollowIdeFileAction> = listOf(
        HollowIdeFileAction(
            id = SaveId,
            label = "Save",
            shortcut = "Ctrl+S",
            icon = SaveIcon,
            isVisible = { !it.file.readOnly },
            isEnabled = { it.file.dirty },
            run = { it.save() },
        ),
        HollowIdeFileAction(
            id = ReformatId,
            label = "Reformat Code",
            shortcut = "Ctrl+Alt+L",
            icon = ReformatIcon,
            isVisible = { it.canFormat },
            run = { it.reformat() },
        ),
        HollowIdeFileAction(
            id = CloseId,
            label = "Close",
            shortcut = "Ctrl+W",
            icon = CloseIcon,
            separatorBefore = true,
            run = { it.close() },
        ),
        HollowIdeFileAction(
            id = CloseOthersId,
            label = "Close Others",
            run = { it.closeOthers() },
        ),
        HollowIdeFileAction(
            id = CloseAllId,
            label = "Close All",
            run = { it.closeAll() },
        ),
        HollowIdeFileAction(
            id = CopyPathId,
            label = "Copy Path",
            icon = CopyIcon,
            separatorBefore = true,
            run = { it.copyPath() },
        ),
        HollowIdeFileAction(
            id = RevealId,
            label = "Select in Project View",
            icon = ProjectIcon,
            run = { it.revealInProjectView() },
        ),
        HollowIdeFileAction(
            id = ShowInExplorerId,
            label = "Show in Explorer",
            icon = FolderIcon,
            run = { it.showInExplorer() },
        ),
    )
}

internal fun fileContextMenuActions(context: HollowIdeFileActionContext): List<HollowIdeFileAction> {
    val declared = context.file.type.actions
    if (declared.isEmpty()) return HollowIdeStandardFileActions.actions.filter { it.isVisible(context) }

    val overrides = declared.associateBy(HollowIdeFileAction::id)
    val standard = HollowIdeStandardFileActions.actions.map { overrides[it.id] ?: it }
    val standardIds = HollowIdeStandardFileActions.actions.mapTo(HashSet()) { it.id }
    val added = declared.filterNot { it.id in standardIds }
    val visible = (standard + added).filter { it.isVisible(context) }

    val firstAdded = visible.indexOfFirst { action -> added.any { it.id == action.id } }
    if (firstAdded < 0) return visible
    return visible.mapIndexed { index, action ->
        if (index != firstAdded || action.separatorBefore) action else action.withSeparator()
    }
}

private fun HollowIdeFileAction.withSeparator() = HollowIdeFileAction(
    id = id,
    label = label,
    shortcut = shortcut,
    icon = icon,
    separatorBefore = true,
    isVisible = isVisible,
    isEnabled = isEnabled,
    run = run,
)

private const val SaveIcon = "hollowengine:textures/gui/icons/save.svg"
private const val ReformatIcon = "hollowengine:textures/gui/icons/code_editor.svg"
private const val CloseIcon = "hollowengine:textures/gui/icons/close.png"
private const val CopyIcon = "hollowengine:textures/gui/icons/copy.svg"
private const val FolderIcon = "hollowengine:textures/gui/icons/folder_open.svg"
