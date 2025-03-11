package ru.hollowhorizon.hollowengine.client.gui.scripting.panels

import net.minecraft.client.Minecraft
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.modules.ui2.Button
import de.fabmax.kool.modules.ui2.docking.Dock
import de.fabmax.kool.modules.ui2.docking.DockNode
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import ru.hollowhorizon.hollowengine.client.gui.docs.DocsNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.FileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEStorage.dock
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEStorage.files
import ru.hollowhorizon.hollowengine.client.gui.scripting.docking.insertItem
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.DocFileData


class CraftEditorPanel(dock: Dock) : DockPanel("hollowengine.gui.ide.craft_editor", dock) {
    override val icon = ""
    private val recipeList = mutableListOf<Recipe<*>>()
    private val textLineProvider = ListTextLineProvider()
    private val recipeTypes: MutableSet<RecipeType<*>> = mutableSetOf()

    override fun UiScope.compose() {
        registerRecipeLogger()
        addRecipeTypes()
        surface.apply {
            Column {
                LazyList {

                    recipeTypes.forEach {
                        Button(it.toString()) {
                            modifier.width(Grow.Std).margin(sizes.gap)
                        }
                    }
                }
            }
        }
        modifier.size(Grow.Std, Grow.Std)
    }

    fun registerRecipeLogger() {
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

    fun addRecipeTypes() {
        recipeList.forEach { recipeTypes.add(it.type) }
    }

    fun openDocFile(node: FileNode) {
        val page = (node as? DocsNode)?.page ?: return
        files.getOrPut(node.treePath) {
            val localFile = DocFileData(node.treeName, node.treePath, page)
            dock.addDockableSurface(localFile.dockable, localFile.surface)
            val fileLeaf = dock.getLeafAtPath("0/1")
            if (fileLeaf != null) fileLeaf.dock(localFile.dockable)
            else dock.getLeafAtPath("0")?.insertItem(localFile.dockable, DockNode.SlotPosition.Right)
            localFile
        }
    }
}

