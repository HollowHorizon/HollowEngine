package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.*
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutsceneEditorPanel
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.CutscenePropertiesPanel
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.MAIN_PANEL_ID
import ru.hollowhorizon.hollowengine.client.gui.timeline.cutscene.PROPERTIES_PANEL_ID
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent

@SubscribeEvent
fun loadLayouts(event: LoadLayoutEvent) {
    //event.provide("hollowengine.gui.ide.project_tree", ::FileTreePanel)
    event.provide("hollowengine.gui.ide.console", ::ConsolePanel)
    event.provide("hollowengine.gui.ide.docs", ::DocsPanel)
    event.provide("hollowengine.gui.ide.markdown", ::MarkdownEditorPanel)
    event.provide("hollowengine.gui.ide.tags", ::TagEditorPanel)
    event.provide("hollowengine.gui.ide.viewport", ::GameViewportPanel)
    event.provide(MAIN_PANEL_ID, ::CutsceneEditorPanel)
    event.provide(PROPERTIES_PANEL_ID, ::CutscenePropertiesPanel)
}
