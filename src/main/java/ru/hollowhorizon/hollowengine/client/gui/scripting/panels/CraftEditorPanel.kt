package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import net.minecraft.client.Minecraft
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.Button
import de.fabmax.kool.modules.ui2.docking.Dock
import net.minecraft.world.item.crafting.Recipe


class CraftEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.craft_editor", dock) {
    override val icon = ""
    private val recipeList = mutableListOf<Recipe<*>>()
    private val textLineProvider = ListTextLineProvider()
    private val buttonList = mutableListOf<Pair<String, TextScope>>()

    override fun UiScope.compose() {
        registerRecipeLogger()
        surface.apply {
            recipeList.forEach {
                buttonList.add(Pair(it.id.toString(), Button(text = it.id.toString()){}))
            }

            Column {
                buttonList.forEach {
                    it.second.modifier.width(Grow.Std).margin(sizes.gap)
                }
            }
        }
    }

    fun registerRecipeLogger(){
        val client = Minecraft.getInstance()
        val recipeManager = client.connection?.recipeManager
            ?: return

        recipeList.clear()
        textLineProvider.lines.clear()

        val allRecipes: Collection<Recipe<*>> = recipeManager.recipes

        for (recipe in allRecipes) {

            recipeList.add(recipe)
            /*
            val recipeName = recipe.id.toString()
            textLineProvider.lines.add(
                TextLine(
                    listOf(Pair(recipeName, TextAttributes(font = MsdfFont(PT_SANS), color = Color.WHITE)))
                )
            )
             */
        }
    }
}

