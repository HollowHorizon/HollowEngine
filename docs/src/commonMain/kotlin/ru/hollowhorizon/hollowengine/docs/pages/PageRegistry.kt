package ru.hollowhorizon.hollowengine.docs.pages

import de.fabmax.kool.util.logI
import ru.hollowhorizon.hollowengine.docs.DocsNode

object PageRegistry {
    var currentPage: DocPage? = null
        set(value) {
            field = value
            setPage()
        }
    lateinit var setPage: () -> Unit

    fun loadPages(root: DocsNode) {
        root.apply {
            addPage(SimpleTextPage("Создание нпс", "npcs", "Тут чёта про создание"))
            addPage(SimpleTextPage("Удаление нпс", "npcs", "Тут чёта про удаление"))

            addPage(SimpleTextPage("Эффекты (Бедрок)", "effects/bedrock", "Чёта про эффекты"))
            addPage(SimpleTextPage("Эффекты (Эфиксер)", "effects/effekseer", "Чёта про эффекты"))
        }
    }
}

fun DocsNode.addPage(page: DocPage) {
    val pathSegments = page.location.split("/")
    var currentNode = this

    for ((i, segment) in pathSegments.withIndex()) {
        // Проверяем, существует ли уже дочерний узел с таким именем
        val existingNode = currentNode.children.find { it.treeName == segment }
        if (existingNode != null) {
            currentNode = existingNode
        } else {
            // Создаем новый узел, если он не существует
            val newNode = DocsNode(segment, if (currentNode.treePath.isEmpty()) segment else "${currentNode.treePath}/$segment")
            newNode.depth = i+1
            currentNode.children.add(newNode)
            currentNode = newNode
        }
    }

    val pageNode = DocsNode(page.name, currentNode.treePath + "/" + page.location)
    pageNode.depth = currentNode.depth + 1
    logI { page.name }
    pageNode.page = page
    currentNode.children.add(pageNode)
}