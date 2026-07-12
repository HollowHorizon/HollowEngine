package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HollowIdeModelTests {
    @Test
    fun `focus selection keeps existing multi selection when target is selected`() {
        val model = HollowIdeModel()
        val first = HollowIdeFileNode("first.kts", "first.kts", 0, false)
        val second = HollowIdeFileNode("second.kts", "second.kts", 0, false)

        model.select(first)
        model.select(second, additive = true)
        model.focusSelection(first)

        assertEquals("first.kts", model.selectedTreePath)
        assertEquals(listOf("first.kts", "second.kts"), model.selectedTreePaths.toList())
    }

    @Test
    fun `delete removes selected file without path provider crash`() {
        val root = DirectoryManager.HOLLOW_ENGINE.toFile().resolve("codex-delete-test-${System.nanoTime()}")
        val file = root.resolve("script.kts")
        try {
            root.mkdirs()
            file.writeText("val value = 1")

            val model = HollowIdeModel()
            val path = "${root.name}/${file.name}"

            assertEquals(HollowIdeFileOperationResult.Success, model.delete(listOf(path)))
            assertFalse(file.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `refresh preserves expansion at every tree depth`() {
        val root = DirectoryManager.HOLLOW_ENGINE.toFile().resolve("codex-tree-test-${System.nanoTime()}")
        try {
            root.resolve("first/second/third").mkdirs()
            val treeRoot = HollowIdeFileNode(root.name, root.name, 0, true).apply {
                expanded = true
                refresh()
            }
            val first = treeRoot.children.single().apply {
                expanded = true
                refresh()
            }
            first.children.single().apply {
                expanded = true
                refresh()
            }

            treeRoot.refresh()

            val refreshedFirst = treeRoot.children.single()
            val refreshedSecond = refreshedFirst.children.single()
            assertTrue(refreshedFirst.expanded)
            assertTrue(refreshedSecond.expanded)
            assertEquals("third", refreshedSecond.children.single().name)
        } finally {
            root.deleteRecursively()
        }
    }
}
