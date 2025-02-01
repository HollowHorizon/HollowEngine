package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2

class DocsTreePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.docs", dock) {
    override val icon = "hollowengine:textures/gui/icons/docs.png"

    override fun UiScope.compose() {
        docsTree()
    }

    val docsTree = DocsNode("HollowEngine", "").apply {
        isFolder = true
        children += DocsNode("Добро пожаловать", "welcome")
        children += DocsNode("Авторы", "credits")
        children += DocsNode("Скриптинг", "scripts").apply {
            isFolder = true
            children += DocsNode("Сюжетные события", "scripting/story_events").apply {
                isFolder = true
                children += DocsNode("Персонажи", "scripting/story_events/npcs").apply {
                    isFolder = true
                    children += DocsNode("Создание", "scripting/story_events/npcs/creation")
                    children += DocsNode("Настройка", "scripting/story_events/npcs/options")
                    children += DocsNode("Действия", "scripting/story_events/npcs/actions")
                    children += DocsNode("Анимации", "scripting/story_events/npcs/animations")
                    children += DocsNode("Задания", "scripting/story_events/npcs/quests")
                    children += DocsNode("Торговля", "scripting/story_events/npcs/trading")
                }
                children += DocsNode("Сцена", "scripting/story_events/scene").apply {
                    isFolder = true
                    children += DocsNode("Мир", "scripting/story_events/npcs/world")
                    children += DocsNode("Диалоги", "scripting/story_events/scene/dialogues")
                    children += DocsNode("Камера", "scripting/story_events/scene/camera")
                    children += DocsNode("Переходы", "scripting/story_events/scene/transitions")
                    children += DocsNode("Частицы", "scripting/story_events/scene/particles")
                    children += DocsNode("Пост-процессинг", "scripting/story_events/npcs/shaders")
                }
                children += DocsNode("Игроки", "scripting/story_events/players").apply {
                    isFolder = true
                }
                children += DocsNode("Разное", "scripting/story_events/utils").apply {
                    isFolder = true
                }
            }
        }
    }.resize()

    private fun FileNode.resize(depth: Int = 0): FileNode {
        this.depth = depth
        children.forEach { it.resize(depth + 1) }
        return this
    }
}