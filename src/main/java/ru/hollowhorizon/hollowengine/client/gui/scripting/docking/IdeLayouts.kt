package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.*
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent

@SubscribeEvent
fun loadLayouts(event: LoadLayoutEvent) {
    event.provide("hollowengine.gui.ide.project_tree", ::FileTreePanel)
    event.provide("hollowengine.gui.ide.console", ::ConsolePanel)
    event.provide("hollowengine.gui.ide.docs", ::DocsPanel)
    event.provide("hollowengine.gui.ide.markdown", ::MarkdownEditorPanel)
    event.provide("hollowengine.gui.ide.graph", ::GraphEditorPanel)
}