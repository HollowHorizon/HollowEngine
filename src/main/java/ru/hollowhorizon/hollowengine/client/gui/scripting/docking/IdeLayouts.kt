package ru.hollowhorizon.hollowengine.client.gui.scripting.docking

import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.RecipeEditorPanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.DocsTreePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.FileTreePanel
import ru.hollowhorizon.hollowengine.client.gui.scripting.panels.NbtEditorPanel

@SubscribeEvent
fun loadLayouts(event: LoadLayoutEvent) {
    event.provide("hollowengine.gui.ide.project_tree", ::FileTreePanel)
    event.provide("hollowengine.gui.ide.docs", ::DocsTreePanel)
    event.provide("hollowengine.gui.ide.recipes", ::RecipeEditorPanel)
    event.provide("hollowengine.gui.ide.nbt", ::NbtEditorPanel)
}