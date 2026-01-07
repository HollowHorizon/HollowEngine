package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.ConsolePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DocsPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.FileTreePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.MarkdownEditorPanel
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent

@SubscribeEvent
fun loadLayouts(event: LoadLayoutEvent) {
    event.provide("hollowengine.gui.ide.project_tree", ::FileTreePanel)
    event.provide("hollowengine.gui.ide.console", ::ConsolePanel)
    event.provide("hollowengine.gui.ide.docs", ::DocsPanel)
    event.provide("hollowengine.gui.ide.markdown", ::MarkdownEditorPanel)
}