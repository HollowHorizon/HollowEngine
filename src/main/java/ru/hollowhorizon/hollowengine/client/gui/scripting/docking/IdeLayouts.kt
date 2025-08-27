package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DocsTreePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.FileTreePanel

@SubscribeEvent
fun loadLayouts(event: LoadLayoutEvent) {
    event.provide("hollowengine.gui.ide.project_tree", ::FileTreePanel)
    event.provide("hollowengine.gui.ide.docs", ::DocsTreePanel)
}