package ru.hollowhorizon.hollowengine.client.ui.ide

import androidx.compose.runtime.Composable
import ru.hollowhorizon.hollowengine.client.ui.docking.DockPlacement
import ru.hollowhorizon.hollowengine.client.ui.ide.files.HollowIdeLanguageService
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonExtensionPoint
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonExtensions
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonRegistration
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.DefinitionLocation
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.HoverInfo
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayHint
import ru.hollowhorizon.hollowengine.common.scripting.ide.OccurrenceRange
import ru.hollowhorizon.hollowengine.common.scripting.ide.SignatureHelp

/** Typed extension points consumed by the in-game Hollow IDE. */
object HollowIdeExtensionPoints {
    val FILE_TYPES = HollowAddonExtensionPoint("hollowengine:ide/file-types", HollowIdeFileType::class)
    val PANELS = HollowAddonExtensionPoint("hollowengine:ide/panels", HollowIdePanel::class)
    val MENU_ITEMS = HollowAddonExtensionPoint("hollowengine:ide/menu-items", HollowIdeMenuItem::class)
    val FILE_ACTIONS = HollowAddonExtensionPoint("hollowengine:ide/file-actions", HollowIdeFileActionProvider::class)
    val PROJECT_ACTIONS = HollowAddonExtensionPoint(
        "hollowengine:ide/project-actions",
        HollowIdeProjectActionProvider::class,
    )
    val LANGUAGES = HollowAddonExtensionPoint("hollowengine:ide/languages", HollowIdeLanguageService::class)
    val CODE_INSIGHT = HollowAddonExtensionPoint(
        "hollowengine:ide/code-insight",
        HollowIdeCodeInsightContributor::class,
    )
}

/** Operations a contributed panel or menu action may request from the IDE host. */
interface HollowIdeContext {
    val focusedFile: HollowIdeOpenFile?

    fun openFile(path: String): Boolean

    fun openPanel(id: String): Boolean

    fun closePanel(id: String): Boolean

    fun isPanelOpen(id: String): Boolean

    fun saveAll(): Int

    fun refreshProject()

    fun setStatus(message: String)
}

/** A contributed dock panel. [title] may be literal text or a Minecraft translation key. */
class HollowIdePanel(
    val id: String,
    val title: String,
    val icon: String? = null,
    val closable: Boolean = true,
    val minWidth: Float = 240f,
    val minHeight: Float = 160f,
    val placement: HollowIdePanelPlacement = HollowIdePanelPlacement(),
    val showInWindowMenu: Boolean = true,
    val content: @Composable (HollowIdeContext) -> Unit,
) {
    init {
        require(id.isNotBlank()) { "IDE panel ID cannot be blank" }
        require(title.isNotBlank()) { "IDE panel title cannot be blank" }
        require(minWidth > 0f && minHeight > 0f) { "IDE panel minimum size must be positive" }
    }
}

data class HollowIdePanelPlacement(
    val anchor: HollowIdePanelAnchor = HollowIdePanelAnchor.Editor,
    val placement: DockPlacement = DockPlacement.RIGHT,
)

sealed interface HollowIdePanelAnchor {
    data object Root : HollowIdePanelAnchor
    data object Project : HollowIdePanelAnchor
    data object Editor : HollowIdePanelAnchor
    data object Console : HollowIdePanelAnchor
    data class Panel(val id: String) : HollowIdePanelAnchor
}

enum class HollowIdeMenu {
    FILE,
    WINDOW,
    TOOLS,
    HELP,
}

enum class HollowIdeMenuMark {
    CHECKBOX,
    RADIO,
}

/** A contributed toolbar item. [label] may be literal text or a Minecraft translation key. */
class HollowIdeMenuItem(
    val id: String,
    val menu: HollowIdeMenu,
    val label: String,
    val icon: String? = null,
    val mark: HollowIdeMenuMark? = null,
    val closeOnClick: Boolean = true,
    val isVisible: (HollowIdeContext) -> Boolean = { true },
    val isEnabled: (HollowIdeContext) -> Boolean = { true },
    val isChecked: (HollowIdeContext) -> Boolean = { false },
    val run: (HollowIdeContext) -> Unit,
) {
    init {
        require(id.isNotBlank()) { "IDE menu item ID cannot be blank" }
        require(label.isNotBlank()) { "IDE menu item label cannot be blank" }
    }
}

fun interface HollowIdeFileActionProvider {
    fun actions(context: HollowIdeFileActionContext): List<HollowIdeFileAction>
}

class HollowIdeProjectAction(
    val id: String,
    val label: String,
    val shortcut: String = "",
    val icon: String? = null,
    val isVisible: (HollowIdeProjectActionContext) -> Boolean = { true },
    val isEnabled: (HollowIdeProjectActionContext) -> Boolean = { true },
    val run: (HollowIdeProjectActionContext) -> Unit,
) {
    init {
        require(id.isNotBlank()) { "IDE project action ID cannot be blank" }
        require(label.isNotBlank()) { "IDE project action label cannot be blank" }
    }
}

interface HollowIdeProjectActionContext {
    val ide: HollowIdeContext
    val path: String
    val selectedPaths: List<String>
    val isDirectory: Boolean
}

fun interface HollowIdeProjectActionProvider {
    fun actions(context: HollowIdeProjectActionContext): List<HollowIdeProjectAction>
}

/** An inlay at an absolute UTF-16 offset. The IDE converts it to its line-relative representation. */
data class HollowIdePositionedInlayHint(
    val offset: Int,
    val hint: InlayHint,
)

/** Additional language intelligence layered over the selected language analyzer. */
interface HollowIdeCodeInsightContributor {
    fun supports(path: String): Boolean = true

    fun occurrences(path: String, text: String, offset: Int): List<OccurrenceRange> = emptyList()

    fun completions(path: String, text: String, offset: Int): List<CompletionItem> = emptyList()

    fun diagnostics(path: String, text: String): List<Diagnostic> = emptyList()

    fun inlays(path: String, text: String): List<HollowIdePositionedInlayHint> = emptyList()

    fun definition(path: String, text: String, offset: Int): DefinitionLocation? = null

    fun signatureHelp(path: String, text: String, offset: Int): SignatureHelp? = null

    fun hover(path: String, text: String, offset: Int): HoverInfo? = null
}

fun HollowAddonExtensions.registerIdeFileType(type: HollowIdeFileType): HollowAddonRegistration =
    register(HollowIdeExtensionPoints.FILE_TYPES, type.id, type, type.priority)

fun HollowAddonExtensions.registerIdePanel(
    panel: HollowIdePanel,
    priority: Int = 0,
): HollowAddonRegistration = register(HollowIdeExtensionPoints.PANELS, panel.id, panel, priority)

fun HollowAddonExtensions.registerIdeMenuItem(
    item: HollowIdeMenuItem,
    priority: Int = 0,
): HollowAddonRegistration = register(HollowIdeExtensionPoints.MENU_ITEMS, item.id, item, priority)

fun HollowAddonExtensions.registerIdeFileActions(
    id: String,
    provider: HollowIdeFileActionProvider,
    priority: Int = 0,
): HollowAddonRegistration = register(HollowIdeExtensionPoints.FILE_ACTIONS, id, provider, priority)

fun HollowAddonExtensions.registerIdeProjectActions(
    id: String,
    provider: HollowIdeProjectActionProvider,
    priority: Int = 0,
): HollowAddonRegistration = register(HollowIdeExtensionPoints.PROJECT_ACTIONS, id, provider, priority)

fun HollowAddonExtensions.registerIdeLanguage(
    language: HollowIdeLanguageService,
    priority: Int = 0,
): HollowAddonRegistration = register(HollowIdeExtensionPoints.LANGUAGES, language.id, language, priority)

fun HollowAddonExtensions.registerIdeCodeInsight(
    id: String,
    contributor: HollowIdeCodeInsightContributor,
    priority: Int = 0,
): HollowAddonRegistration = register(HollowIdeExtensionPoints.CODE_INSIGHT, id, contributor, priority)
