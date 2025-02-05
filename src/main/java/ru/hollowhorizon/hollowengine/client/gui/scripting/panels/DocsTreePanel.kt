package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.docking.Dock
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.common.scripting.core.completion.ScriptColorizer
import ru.hollowhorizon.hollowengine.docs.pages.CreditsPage
import ru.hollowhorizon.hollowengine.docs.pages.ScriptingPage
import ru.hollowhorizon.hollowengine.docs.pages.WelcomePage
import ru.hollowhorizon.hollowengine.docs.pages.story.StoryEventsPage
import ru.hollowhorizon.hollowengine.docs.pages.story.npcs.CreationPage
import ru.hollowhorizon.hollowengine.docs.pages.story.npcs.NpcsPage

class DocsTreePanel(dock: Dock) : DockPanel("hollowengine.gui.ide.docs", dock) {
    override val icon = "hollowengine:textures/gui/icons/docs.png"

    override fun UiScope.compose() {
        docsTree()
    }

    val docsTree = DocsNode("HollowEngine", "").apply {
        initPages()

        isFolder = true
        children += DocsNode("Добро пожаловать", "welcome", WelcomePage)
        children += DocsNode("Авторы", "credits", CreditsPage)
        children += DocsNode("Скриптинг", "scripts", ScriptingPage).apply {
            isFolder = true
            children += DocsNode("Сюжетные события", "scripting/story_events", StoryEventsPage).apply {
                isFolder = true
                children += DocsNode("Персонажи", "scripting/story_events/npcs", NpcsPage).apply {
                    isFolder = true
                    children += DocsNode("Создание", "scripting/story_events/npcs/creation", CreationPage)
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

    fun initPages() {
        CreationPage.creationTemplate.apply {
            clear()
            addAll(ScriptColorizer.parse("create.story.kts", """
                // Объявляем переменную 'vitalik'
                val vitalik = npc(
                    name = "Виталик", // Имя персонажа, которое будет по умолчанию видно в чате и над головой
                    pos = pos(0, 0, 0), // Координаты спавна персонажа
                    world = "minecraft:overworld", // Измерение, в котором будет призван персонаж
                    model = "hollowengine:models/entity/player_model.gltf", // Путь до модели персонажа
                    rotation = rotation(180, 0), // Поворот персонажа при спавне по осям 'pitch' и 'yaw'
                    size = 1f to 1.2f, // Размер хитбокса персонажа по Ширине и Высоте
                    attributes = mapOf( // Атрибуты персонажа (здоровье, скорость, или что-то добавленное модами)
                        "my_mod_id:my.best.attrubute_one" to 10f,
                        "my_mod_id:my.best.attribute_two" to 0.5f,
                    ),
                    textures = mapOf( // Замена текстур персонажа (нужно указать исходную и текстуру для замены)
                        // Исходная текстура указана в файле вашей модели или сгенерирована движком, в этом случае можно узнать её имя командой `/hollowengine model`
                        "model/texture_id_one" to "my_mod_id:path/to/my_best_texture.png",
                        "model/texture_id_two" to "my_mod_id:path/to/my_another_texture.png",
                    ),
                    animations = mapOf( // Замена автоматически определённых анимаций на ваши
                        AnimationType.IDLE to "bestIdleAnimation",
                        AnimationType.WALK to "walkAnimLoop",
                    ),
                    transform = Transform( // Изменение положения модели относительно центра сущности
                        tX = 1.1f, tY = 0.4f, tZ = 3f, // Смещение модели по осям X, Y и Z
                        rX = 90f, rY = 2.4f, rZ = 194f, // Поворот модели по осям X, y и Z
                        sX = 0.1f, sY = 1.001f, sZ = 9.2f // Размер модели по осям X, Y и Z
                    ),
                    showName = false, // Показать/Скрыть подпись имени над головой персонажа
                    inverseHeadRotation = true, // Поменять местами x и y при повороте головы (в некоторых моделях они могут быть перепутаны)
                )
            """.trimIndent()))
        }
    }

    private fun FileNode.resize(depth: Int = 0): FileNode {
        this.depth = depth
        children.forEach { it.resize(depth + 1) }
        return this
    }
}