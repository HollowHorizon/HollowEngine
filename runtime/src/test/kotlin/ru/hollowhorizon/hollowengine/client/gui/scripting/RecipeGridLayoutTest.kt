package ru.hollowhorizon.hollowengine.client.gui.scripting

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import ru.hollowhorizon.hollowengine.client.ui.ide.recipe.shapedCells
import ru.hollowhorizon.hollowengine.client.ui.ide.recipe.writeShaped
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeGridLayoutTest {
    private val stick = item("minecraft:stick")
    private val planks = tag("minecraft:planks")
    private val iron = item("minecraft:iron_ingot")

    @Test
    fun `pattern is trimmed to the filled cells`() {
        val recipe = JsonObject()
        recipe.writeShaped(grid(4 to stick, 7 to stick))

        assertEquals(listOf("#", "#"), recipe.pattern())
        assertEquals(stick, recipe.getAsJsonObject("key").get("#"))
    }

    @Test
    fun `an ingredient keeps the key the file gave it`() {
        val recipe = recipe(
            """{"pattern": ["PP", "PP"], "key": {"P": {"tag": "minecraft:planks"}}}""",
        )
        val cells = shapedCells(recipe)!!.toMutableList()
        cells[8] = iron
        recipe.writeShaped(cells)

        assertEquals(listOf("PP ", "PP ", "  #"), recipe.pattern())
        assertEquals(planks, recipe.getAsJsonObject("key").get("P"))
        assertEquals(iron, recipe.getAsJsonObject("key").get("#"))
    }

    @Test
    fun `a new ingredient never takes a key already in use`() {
        val recipe = recipe("""{"pattern": ["#"], "key": {"#": {"item": "minecraft:stick"}}}""")
        recipe.writeShaped(grid(0 to iron, 1 to stick))

        assertEquals(listOf("X#"), recipe.pattern())
        assertEquals(setOf("#", "X"), recipe.getAsJsonObject("key").keySet())
    }

    @Test
    fun `cells read back what was written`() {
        val cells = grid(0 to planks, 2 to planks, 4 to stick, 6 to iron)
        val recipe = JsonObject().apply { writeShaped(cells) }

        assertEquals(cells, shapedCells(recipe))
    }

    @Test
    fun `a pattern wider than the grid is not read`() {
        assertNull(shapedCells(recipe("""{"pattern": ["####"], "key": {}}""")))
    }

    private fun grid(vararg cells: Pair<Int, JsonElement>): List<JsonElement?> =
        List(9) { index -> cells.firstOrNull { it.first == index }?.second }

    private fun JsonObject.pattern(): List<String> = getAsJsonArray("pattern").map { it.asString }

    private fun recipe(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun item(id: String): JsonElement = JsonObject().apply { addProperty("item", id) }

    private fun tag(id: String): JsonElement = JsonObject().apply { addProperty("tag", id) }
}
