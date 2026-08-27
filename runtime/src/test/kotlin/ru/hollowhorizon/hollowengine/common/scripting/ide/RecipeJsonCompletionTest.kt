package ru.hollowhorizon.hollowengine.common.scripting.ide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeJsonCompletionTest {
    @Test
    fun `completes ingredient item values in recipe files`() {
        val text = """{"ingredient":{"item":"minecraft:dia"}}"""
        val caret = text.indexOf("dia") + 3

        assertEquals("minecraft:dia", recipeItemValuePrefix("data/example/recipe/tool.json", text, caret))
    }

    @Test
    fun `completes result ids in modern recipe objects`() {
        val text = """{"result":{"count":2,"id":"example:gear"}}"""
        val caret = text.indexOf("gear") + 4

        assertEquals("example:gear", recipeItemValuePrefix("data/example/recipes/tool.json", text, caret))
    }

    @Test
    fun `completes legacy direct result values`() {
        val text = """{"result":"minecraft:stick"}"""
        val caret = text.indexOf("stick") + 5

        assertEquals("minecraft:stick", recipeItemValuePrefix("data/example/recipe/tool.json", text, caret))
    }

    @Test
    fun `ignores non item recipe strings`() {
        val text = """{"type":"minecraft:crafting_shaped","key":{"X":{"tag":"c:ingots"}}}"""

        assertNull(recipeItemValuePrefix("data/example/recipe/tool.json", text, text.indexOf("shaped") + 6))
        assertNull(recipeItemValuePrefix("data/example/recipe/tool.json", text, text.indexOf("ingots") + 6))
    }

    @Test
    fun `ignores matching fields outside recipe folders`() {
        val text = """{"item":"minecraft:diamond"}"""
        val caret = text.indexOf("diamond") + 7

        assertNull(recipeItemValuePrefix("data/example/loot_table/chest.json", text, caret))
    }
}
