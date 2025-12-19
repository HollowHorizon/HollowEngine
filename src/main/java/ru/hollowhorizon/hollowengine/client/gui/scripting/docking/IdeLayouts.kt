package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.ConsolePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.FileTreePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.LanguageEditorPanel
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent

@SubscribeEvent
fun loadLayouts(event: LoadLayoutEvent) {
    event.provide("hollowengine.gui.ide.project_tree", ::FileTreePanel)
    event.provide("hollowengine.gui.ide.console", ::ConsolePanel)
    event.provide("hollowengine.gui.ide.translations", ::LanguageEditorPanel)
}