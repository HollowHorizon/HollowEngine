package ru.hollowhorizon.hollowengine.client.ui.ide.asset

import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AssetManagerStateTest {
    @Test
    fun `keeps independent grid scroll state for each directory`() {
        val state = AssetManagerState()
        val recipes = AssetDirectory("minecraft", "recipe")
        val tags = AssetDirectory("minecraft", "tags/item")

        assertSame(state.gridState(AssetResourceScope.SERVER, recipes), state.gridState(AssetResourceScope.SERVER, recipes))
        assertFalse(state.gridState(AssetResourceScope.SERVER, recipes) === state.gridState(AssetResourceScope.SERVER, tags))
    }

    @Test
    fun `recipe filters keep directories navigable and filter files by state`() {
        val directory = AssetGridEntry.Directory(AssetDirectory("minecraft", "recipe/tools"))
        val hidden = AssetGridEntry.File(
            AssetFile(ResourceLocation.parse("minecraft:recipe/hidden.json"), "test", AssetResourceState.HIDDEN),
        )
        val untouched = AssetGridEntry.File(
            AssetFile(ResourceLocation.parse("minecraft:recipe/base.json"), "test", AssetResourceState.UNTOUCHED),
        )

        assertTrue(AssetRecipeFilter.HIDDEN.accepts(directory))
        assertTrue(AssetRecipeFilter.HIDDEN.accepts(hidden))
        assertFalse(AssetRecipeFilter.HIDDEN.accepts(untouched))
    }
}
