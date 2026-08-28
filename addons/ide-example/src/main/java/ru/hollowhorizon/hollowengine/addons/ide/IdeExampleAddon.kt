package ru.hollowhorizon.hollowengine.addons.ide

import kotlinx.coroutines.CoroutineScope
import ru.hollowhorizon.hollowengine.client.ui.Column
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeFileDocument
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeFileType
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeMenu
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdeMenuItem
import ru.hollowhorizon.hollowengine.client.ui.ide.HollowIdePanel
import ru.hollowhorizon.hollowengine.client.ui.ide.registerIdeFileType
import ru.hollowhorizon.hollowengine.client.ui.ide.registerIdeMenuItem
import ru.hollowhorizon.hollowengine.client.ui.ide.registerIdePanel
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonEntrypoint
import ru.hollowhorizon.hollowengine.common.addons.extensions

class IdeExampleAddon : HollowAddonEntrypoint {
    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        val overviewPanelId = context.extensions.qualify("overview")
        context.extensions.registerIdePanel(
            HollowIdePanel(
                id = "overview",
                title = "IDE Addon Example",
                content = { ide ->
                    Column {
                        Text("This dock panel is owned by a hot-reloadable addon.")
                        Text("Focused file: ${ide.focusedFile?.path ?: "none"}")
                    }
                },
            ),
        )
        context.extensions.registerIdeMenuItem(
            HollowIdeMenuItem(
                id = "open-overview",
                menu = HollowIdeMenu.TOOLS,
                label = "Open IDE Addon Example",
                run = { ide -> ide.openPanel(overviewPanelId) },
            ),
        )
        context.extensions.registerIdeFileType(
            HollowIdeFileType.extensions(
                id = "quest-preview",
                extensions = listOf(".quest"),
                priority = 100,
                loader = { _, bytes -> QuestPreviewDocument(bytes.toString(Charsets.UTF_8)) },
                editor = { file ->
                    val document = file.document as QuestPreviewDocument
                    Column {
                        Text("Quest preview")
                        Text(document.text)
                    }
                },
            ),
        )
    }
}

private class QuestPreviewDocument(val text: String) : HollowIdeFileDocument {
    override val readOnly: Boolean = true

    override fun encode(): ByteArray = text.toByteArray()
}
